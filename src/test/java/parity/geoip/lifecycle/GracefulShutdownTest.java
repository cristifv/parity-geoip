package parity.geoip.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import parity.geoip.api.RequestListener;
import parity.geoip.config.GeoipProperties;

@ExtendWith(MockitoExtension.class)
class GracefulShutdownTest {

    @TempDir
    Path dir;

    @Mock
    private KafkaListenerEndpointRegistry registry;

    @Mock
    private MessageListenerContainer listener;

    @Test
    void startupRemovesOldFilesThenCreatesTheHealthyFile() throws IOException {
        Files.write(healthy(), new byte[0]);
        Files.write(drained(), new byte[0]);
        GracefulShutdown shutdown = shutdown(Duration.ofSeconds(1));

        shutdown.removeStaleFiles();
        assertThat(healthy()).doesNotExist();
        assertThat(drained()).doesNotExist();

        shutdown.started();
        assertThat(healthy()).exists().isEmptyFile();
    }

    @Test
    void drainStopsTheListenerThenCreatesTheDrainedFile() throws IOException {
        when(registry.getListenerContainer(RequestListener.ID)).thenReturn(listener);
        when(listener.isRunning()).thenReturn(true);
        doAnswer(stop -> {
            // The healthy file is gone before the listener stops.
            assertThat(healthy()).doesNotExist();
            stop.<Runnable>getArgument(0).run();
            return null;
        }).when(listener).stop(any(Runnable.class));
        GracefulShutdown shutdown = shutdown(Duration.ofSeconds(1));
        shutdown.started();

        shutdown.stop();

        verify(listener).stop(any(Runnable.class));
        assertThat(healthy()).doesNotExist();
        assertThat(drained()).exists().isEmptyFile();
    }

    @Test
    void noDrainedFileWhenTheTimeoutRunsOut() throws IOException {
        when(registry.getListenerContainer(RequestListener.ID)).thenReturn(listener);
        when(listener.isRunning()).thenReturn(true);
        GracefulShutdown shutdown = shutdown(Duration.ofMillis(50));
        shutdown.started();

        shutdown.stop(); // the listener never reports it has stopped

        assertThat(healthy()).doesNotExist();
        assertThat(drained()).doesNotExist();
    }

    private GracefulShutdown shutdown(Duration drainTimeout) {
        return new GracefulShutdown(registry, new GeoipProperties(
                new GeoipProperties.Topics("geoip.requests", "geoip.responses"),
                new GeoipProperties.Databases("city.mmdb", ""),
                List.of(),
                new GeoipProperties.Lifecycle(healthy().toString(), drained().toString(), drainTimeout)));
    }

    private Path healthy() {
        return dir.resolve("parity-geoip.healthy");
    }

    private Path drained() {
        return dir.resolve("parity-geoip.drained");
    }
}
