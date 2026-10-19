package parity.geoip.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * What a lookup found for an IP address, as it appears in responses. Fields the databases
 * have nothing for are left out.
 */
@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"ip", "country", "country_name", "region", "region_name", "city", "lat", "lon",
        "accuracy_km", "asn", "as_org", "nearest_zone", "zones"})
public class GeoLocation {
    /** The address as normalized by the parser ({@code ::ffff:1.2.3.4} becomes {@code 1.2.3.4}). */
    String ip;
    /** ISO 3166-1 alpha-2. */
    String country;
    String countryName;
    /** The top-level subdivision's ISO 3166-2 code, without the country (England is {@code ENG}). */
    String region;
    String regionName;
    String city;
    Double lat;
    Double lon;
    /** The location is within this radius, in kilometres. */
    Integer accuracyKm;
    Long asn;
    String asOrg;
    /** The first of {@code zones}. */
    String nearestZone;
    /** Every configured zone, nearest first. Only when the location has coordinates. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<ZoneDistance> zones;
}
