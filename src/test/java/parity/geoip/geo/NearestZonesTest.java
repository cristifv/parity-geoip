package parity.geoip.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import parity.geoip.config.GeoipProperties;

class NearestZonesTest {

    static final GeoipProperties.Zone EU1 = new GeoipProperties.Zone("eu1", 50.11, 8.68);
    static final GeoipProperties.Zone US1 = new GeoipProperties.Zone("us1", 39.04, -77.49);

    @Test
    void haversineDistances() {
        assertThat(NearestZones.distanceKm(48.8566, 2.3522, 51.5074, -0.1278)).isCloseTo(343.557, within(0.01));
        assertThat(NearestZones.distanceKm(51.5142, -0.0931, 39.04, -77.49)).isCloseTo(5919.741, within(0.01));
        assertThat(NearestZones.distanceKm(0, 0, 0, 180)).isCloseTo(Math.PI * NearestZones.EARTH_RADIUS_KM, within(1e-6));
        assertThat(NearestZones.distanceKm(10, 20, 10, 20)).isZero();
    }

    @Test
    void ranksNearestFirst() {
        NearestZones zones = zones(List.of(US1, EU1));
        assertThat(zones.rank(51.5142, -0.0931))
                .containsExactly(new ZoneDistance("eu1", 635), new ZoneDistance("us1", 5920));
        assertThat(zones.rank(47.2513, -122.3149))
                .containsExactly(new ZoneDistance("us1", 3695), new ZoneDistance("eu1", 8214));
    }

    @Test
    void tiesAreOrderedByName() {
        NearestZones zones = zones(List.of(
                new GeoipProperties.Zone("eu1b", 50.11, 8.68), new GeoipProperties.Zone("eu1a", 50.11, 8.68)));
        assertThat(zones.rank(0, 0)).extracting(ZoneDistance::name).containsExactly("eu1a", "eu1b");
    }

    @Test
    void noZones() {
        assertThat(zones(List.of()).rank(51.5, 0)).isEmpty();
    }

    @Test
    void rejectsADuplicateZone() {
        assertThatThrownBy(() -> zones(List.of(EU1, US1, new GeoipProperties.Zone("eu1", 0.0, 0.0))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("zone eu1 is configured twice");
    }

    static NearestZones zones(List<GeoipProperties.Zone> zones) {
        return new NearestZones(TestDatabases.properties(TestDatabases.CITY, "", zones));
    }
}
