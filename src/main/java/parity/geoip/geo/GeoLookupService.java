package parity.geoip.geo;

import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Looks an IP address up in the City and ASN databases and ranks the zones by distance. */
@Service
@RequiredArgsConstructor
public class GeoLookupService {

    private final GeoDatabases databases;
    private final NearestZones nearestZones;

    /** What the databases know about the address, or empty if neither has it. */
    public Optional<GeoLocation> lookup(InetAddress address) {
        Optional<CityResponse> city = find(() -> databases.city().tryCity(address));
        Optional<AsnResponse> asn = databases.asn().flatMap(reader -> find(() -> reader.tryAsn(address)));
        if (city.isEmpty() && asn.isEmpty()) {
            return Optional.empty();
        }
        GeoLocation.GeoLocationBuilder location = GeoLocation.builder().ip(address.getHostAddress());
        city.ifPresent(response -> addCity(location, response));
        asn.ifPresent(response -> location
                .asn(response.autonomousSystemNumber())
                .asOrg(response.autonomousSystemOrganization()));
        return Optional.of(location.build());
    }

    private void addCity(GeoLocation.GeoLocationBuilder location, CityResponse response) {
        location.country(response.country().isoCode())
                .countryName(response.country().name())
                .city(response.city().name());
        List<Subdivision> subdivisions = response.subdivisions();
        if (!subdivisions.isEmpty()) {
            location.region(subdivisions.getFirst().isoCode()).regionName(subdivisions.getFirst().name());
        }
        Location coordinates = response.location();
        if (coordinates.latitude() != null && coordinates.longitude() != null) {
            List<ZoneDistance> zones = nearestZones.rank(coordinates.latitude(), coordinates.longitude());
            location.lat(coordinates.latitude())
                    .lon(coordinates.longitude())
                    .accuracyKm(coordinates.accuracyRadius())
                    .zones(zones)
                    .nearestZone(zones.isEmpty() ? null : zones.getFirst().name());
        }
    }

    @FunctionalInterface
    private interface Lookup<T> {
        Optional<T> run() throws IOException, GeoIp2Exception;
    }

    /** A failed read means a corrupt or truncated database file. */
    private static <T> Optional<T> find(Lookup<T> lookup) {
        try {
            return lookup.run();
        } catch (IOException e) {
            throw new UncheckedIOException("database read failed", e);
        } catch (GeoIp2Exception e) {
            throw new IllegalStateException("database lookup failed", e);
        }
    }
}
