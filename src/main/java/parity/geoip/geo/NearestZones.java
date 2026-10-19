package parity.geoip.geo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import parity.geoip.config.GeoipProperties;

/** Ranks the configured zones by great-circle (haversine) distance from a point. */
@Component
public class NearestZones {

    /** The mean Earth radius. */
    static final double EARTH_RADIUS_KM = 6371.0088;

    private final List<GeoipProperties.Zone> zones;

    public NearestZones(GeoipProperties properties) {
        this.zones = List.copyOf(properties.zones());
        Set<String> names = new HashSet<>();
        for (GeoipProperties.Zone zone : zones) {
            if (!names.add(zone.name())) {
                throw new IllegalStateException("zone " + zone.name() + " is configured twice");
            }
        }
    }

    /** Every zone, nearest first. Zones at the same distance are ordered by name. */
    public List<ZoneDistance> rank(double lat, double lon) {
        return zones.stream()
                .map(zone -> new ZoneDistance(zone.name(), Math.round(distanceKm(lat, lon, zone.lat(), zone.lon()))))
                .sorted(Comparator.comparingLong(ZoneDistance::distanceKm).thenComparing(ZoneDistance::name))
                .toList();
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
