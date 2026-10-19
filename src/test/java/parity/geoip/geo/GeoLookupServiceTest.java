package parity.geoip.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import parity.geoip.config.GeoipProperties;

/** Lookups against MaxMind's test databases ({@link TestDatabases}). */
class GeoLookupServiceTest {

    private static final List<GeoipProperties.Zone> ZONES = List.of(NearestZonesTest.EU1, NearestZonesTest.US1);

    private GeoDatabases databases;

    @AfterEach
    void close() throws IOException {
        if (databases != null) {
            databases.close();
        }
    }

    @Test
    void cityLookup() {
        GeoLookupService geo = service(TestDatabases.ASN);
        assertThat(geo.lookup(IpAddresses.parse("81.2.69.142"))).contains(GeoLocation.builder()
                .ip("81.2.69.142")
                .country("GB").countryName("United Kingdom")
                .region("ENG").regionName("England")
                .city("London")
                .lat(51.5142).lon(-0.0931).accuracyKm(10)
                .nearestZone("eu1")
                .zones(List.of(new ZoneDistance("eu1", 635), new ZoneDistance("us1", 5920)))
                .build());
    }

    @Test
    void regionIsTheTopLevelSubdivision() {
        // Boxford: England, then West Berkshire.
        GeoLocation boxford = service("").lookup(IpAddresses.parse("2.125.160.216")).orElseThrow();
        assertThat(boxford.getRegion()).isEqualTo("ENG");
        assertThat(boxford.getCity()).isEqualTo("Boxford");
    }

    @Test
    void countryOnly() {
        GeoLocation japan = service("").lookup(IpAddresses.parse("2001:218::1")).orElseThrow();
        assertThat(japan.getCountry()).isEqualTo("JP");
        assertThat(japan.getRegion()).isNull();
        assertThat(japan.getCity()).isNull();
        assertThat(japan.getLat()).isEqualTo(35.68536);
        assertThat(japan.getNearestZone()).isEqualTo("eu1");
    }

    @Test
    void asnOnly() {
        GeoLookupService geo = service(TestDatabases.ASN);
        assertThat(geo.lookup(IpAddresses.parse("1.128.0.1"))).contains(GeoLocation.builder()
                .ip("1.128.0.1").asn(1221L).asOrg("Telstra Pty Ltd").build());
        assertThat(geo.lookup(IpAddresses.parse("12.81.96.1")).orElseThrow().getAsn()).isEqualTo(7018L);
        assertThat(geo.lookup(IpAddresses.parse("12.81.96.1")).orElseThrow().getAsOrg()).isNull();
    }

    @Test
    void withoutAnAsnDatabase() {
        GeoLookupService geo = service("");
        assertThat(geo.lookup(IpAddresses.parse("1.128.0.1"))).isEmpty();
        assertThat(geo.lookup(IpAddresses.parse("81.2.69.142")).orElseThrow().getAsn()).isNull();
    }

    @Test
    void withoutZones() throws IOException {
        databases = new GeoDatabases(TestDatabases.properties(TestDatabases.CITY, "", List.of()));
        GeoLookupService geo = new GeoLookupService(databases, NearestZonesTest.zones(List.of()));
        GeoLocation london = geo.lookup(IpAddresses.parse("81.2.69.142")).orElseThrow();
        assertThat(london.getLat()).isEqualTo(51.5142);
        assertThat(london.getNearestZone()).isNull();
        assertThat(london.getZones()).isEmpty();
    }

    @Test
    void unknownAddress() {
        assertThat(service(TestDatabases.ASN).lookup(IpAddresses.parse("10.0.0.1"))).isEmpty();
    }

    @Test
    void refusesAMissingDatabase() {
        assertThatThrownBy(() -> new GeoDatabases(TestDatabases.properties("no/such.mmdb", "", ZONES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("city database not found: no/such.mmdb");
    }

    @Test
    void refusesADatabaseOfTheWrongType() {
        assertThatThrownBy(() -> new GeoDatabases(TestDatabases.properties(TestDatabases.ASN, "", ZONES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("city database " + TestDatabases.ASN + " is GeoLite2-ASN: ");
        assertThatThrownBy(() -> new GeoDatabases(
                TestDatabases.properties(TestDatabases.CITY, TestDatabases.CITY, ZONES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("asn database " + TestDatabases.CITY + " is GeoLite2-City: ");
    }

    private GeoLookupService service(String asn) {
        try {
            databases = new GeoDatabases(TestDatabases.properties(TestDatabases.CITY, asn, ZONES));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return new GeoLookupService(databases, NearestZonesTest.zones(ZONES));
    }
}
