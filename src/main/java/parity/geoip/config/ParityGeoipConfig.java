package parity.geoip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ParityGeoipConfig {

    /**
     * A request whose response can't be written to Kafka is retried every second until it
     * works. Client errors and failed lookups become error responses and never get here.
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
