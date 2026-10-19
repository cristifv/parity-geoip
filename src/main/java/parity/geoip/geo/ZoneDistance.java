package parity.geoip.geo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A zone and its distance from the looked-up location, rounded to the kilometre. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"name", "distance_km"})
public record ZoneDistance(String name, long distanceKm) {
}
