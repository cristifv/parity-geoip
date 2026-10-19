package parity.geoip.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A request from the requests topic. Which fields are required depends on {@code cmd}:
 * the validation groups below.
 */
@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoRequest {

    /** {@code lookup}. */
    public interface Lookup {}

    /** Echoed back in the response, any JSON value. */
    private JsonNode id;

    @NotBlank(message = "cmd is required")
    private String cmd;

    /** Topic for the response instead of the default responses topic. */
    private String replyTo;

    @NotBlank(groups = Lookup.class, message = "ip is required")
    private String ip;
}
