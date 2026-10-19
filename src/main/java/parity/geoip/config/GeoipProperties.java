package parity.geoip.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Settings under {@code parity-geoip.*}. */
@Validated
@ConfigurationProperties("parity-geoip")
public record GeoipProperties(
        @Valid @NotNull Topics topics,
        @Valid @NotNull Databases databases,
        @DefaultValue List<@Valid Zone> zones,
        @Valid @NotNull Lifecycle lifecycle) {

    /** The Kafka topics of the public API. */
    public record Topics(
            @NotBlank String requests,
            @NotBlank String responses) {
    }

    /** The MaxMind database files. {@code asn} is optional: blank means none. */
    public record Databases(
            @NotBlank String city,
            String asn) {
    }

    /** A zone and where it is. */
    public record Zone(
            @NotBlank String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {
    }

    /**
     * Startup and graceful shutdown. The two files are empty markers for the orchestrator
     * (Kubernetes or Docker Swarm): the healthy file exists while the service is up and
     * taking work, the drained file once it has drained and can be stopped.
     */
    public record Lifecycle(
            @NotBlank String healthyFile,
            @NotBlank String drainedFile,
            @NotNull Duration drainTimeout) {
    }
}
