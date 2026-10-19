package parity.geoip.lifecycle;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;
import parity.geoip.api.RequestListener;
import parity.geoip.config.GeoipProperties;

/**
 * Startup and graceful shutdown, signalled to the orchestrator with two empty files:
 * <ul>
 *   <li>the healthy file, created once the service has started;</li>
 *   <li>the drained file, created once the service has drained and can be stopped.</li>
 * </ul>
 * On {@code SIGTERM} this stops first (the highest phase): it removes the healthy file and
 * stops the request listener, which finishes the request in progress, writes its response
 * and commits its offset. Then it creates the drained file. If the drain timeout runs out
 * first, there is no drained file, and the request left is delivered again later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdown implements SmartLifecycle {

    private final KafkaListenerEndpointRegistry registry;
    private final GeoipProperties properties;
    private volatile boolean running;

    /** Files left by an earlier run would tell the orchestrator the wrong thing. */
    @PostConstruct
    void removeStaleFiles() throws IOException {
        for (Path file : new Path[] {healthyFile(), drainedFile()}) {
            if (Files.deleteIfExists(file)) {
                log.info("removed {}, left by an earlier run", file);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void started() throws IOException {
        touch(healthyFile());
        log.info("started; created {}", healthyFile());
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        long startedAt = System.nanoTime();
        long timeoutMs = properties.lifecycle().drainTimeout().toMillis();
        try {
            Files.deleteIfExists(healthyFile());
            log.info("draining: removed {}", healthyFile());
            if (drain(timeoutMs)) {
                touch(drainedFile());
                log.info("drained in {} ms; created {}", (System.nanoTime() - startedAt) / 1_000_000, drainedFile());
            } else {
                log.error("not drained within {} ms: Kafka will deliver the request in progress again; no {}",
                        timeoutMs, drainedFile());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("graceful shutdown: {}", e.toString());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Before the Kafka listener containers ({@code Integer.MAX_VALUE - 100}). */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /** Stops the listener: the consumer finishes its request, commits and leaves the group. */
    private boolean drain(long timeoutMs) throws InterruptedException {
        MessageListenerContainer listener = registry.getListenerContainer(RequestListener.ID);
        if (listener == null || !listener.isRunning()) {
            return true;
        }
        CountDownLatch stopped = new CountDownLatch(1);
        listener.stop(stopped::countDown);
        return stopped.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private Path healthyFile() {
        return Path.of(properties.lifecycle().healthyFile());
    }

    private Path drainedFile() {
        return Path.of(properties.lifecycle().drainedFile());
    }

    private static void touch(Path file) throws IOException {
        Files.write(file, new byte[0]);
    }
}
