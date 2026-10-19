package parity.geoip.geo;

import java.time.Duration;
import java.util.List;
import parity.geoip.config.GeoipProperties;

/**
 * MaxMind's test databases (from github.com/maxmind/MaxMind-DB, test-data). Some of the
 * networks they hold:
 * <ul>
 *   <li>City: 81.2.69.142/31 London (GB, ENG), 216.160.83.56/29 Milton (US, WA),
 *       67.43.156.0/24 Bhutan (country only), 2001:218::/32 Japan (country only);</li>
 *   <li>ASN: 1.128.0.0/11 AS1221 Telstra Pty Ltd, 12.81.96.0/19 AS7018 (no organization).</li>
 * </ul>
 */
public final class TestDatabases {

    public static final String CITY = "src/test/resources/mmdb/GeoLite2-City-Test.mmdb";
    public static final String ASN = "src/test/resources/mmdb/GeoLite2-ASN-Test.mmdb";

    private TestDatabases() {
    }

    public static GeoipProperties properties(String city, String asn, List<GeoipProperties.Zone> zones) {
        return new GeoipProperties(
                new GeoipProperties.Topics("geoip.requests", "geoip.responses"),
                new GeoipProperties.Databases(city, asn),
                zones,
                new GeoipProperties.Lifecycle("/tmp/test.healthy", "/tmp/test.drained", Duration.ofSeconds(30)));
    }
}
