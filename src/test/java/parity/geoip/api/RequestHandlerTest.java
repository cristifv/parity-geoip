package parity.geoip.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import parity.geoip.geo.GeoLocation;
import parity.geoip.geo.GeoLookupService;
import parity.geoip.geo.IpAddresses;
import parity.geoip.geo.ZoneDistance;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RequestHandlerTest {

    private static final GeoLocation LONDON = GeoLocation.builder()
            .ip("81.2.69.142").country("GB").countryName("United Kingdom").region("ENG").regionName("England")
            .city("London").lat(51.5142).lon(-0.0931).accuracyKm(10).asn(20712L).asOrg("Andrews & Arnold Ltd")
            .nearestZone("eu1").zones(List.of(new ZoneDistance("eu1", 635), new ZoneDistance("us1", 5920)))
            .build();
    private static final String LONDON_JSON = "{\"ip\":\"81.2.69.142\",\"country\":\"GB\","
            + "\"country_name\":\"United Kingdom\",\"region\":\"ENG\",\"region_name\":\"England\","
            + "\"city\":\"London\",\"lat\":51.5142,\"lon\":-0.0931,\"accuracy_km\":10,\"asn\":20712,"
            + "\"as_org\":\"Andrews & Arnold Ltd\",\"nearest_zone\":\"eu1\","
            + "\"zones\":[{\"name\":\"eu1\",\"distance_km\":635},{\"name\":\"us1\",\"distance_km\":5920}]}";

    @Mock
    private GeoLookupService geo;

    private RequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RequestHandler(
                geo, JsonMapper.builder().build(), Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void ping() {
        RequestHandler.Reply reply = handler.handle("{\"id\":\"1\",\"cmd\":\"ping\"}");
        assertThat(reply.getBody()).isEqualTo("{\"id\":\"1\",\"ok\":true,\"result\":{\"pong\":true}}");
        assertThat(reply.getKey()).isEqualTo("1");
        assertThat(reply.getReplyTo()).isNull();
    }

    @Test
    void lookup() {
        when(geo.lookup(IpAddresses.parse("81.2.69.142"))).thenReturn(Optional.of(LONDON));
        RequestHandler.Reply reply = handler.handle("{\"id\":\"g1\",\"cmd\":\"lookup\",\"ip\":\"81.2.69.142\"}");
        assertThat(reply.getBody()).isEqualTo("{\"id\":\"g1\",\"ok\":true,\"result\":" + LONDON_JSON + "}");
    }

    @Test
    void lookupLeavesOutWhatIsUnknown() {
        when(geo.lookup(IpAddresses.parse("1.128.0.1"))).thenReturn(Optional.of(
                GeoLocation.builder().ip("1.128.0.1").asn(1221L).asOrg("Telstra Pty Ltd").zones(List.of()).build()));
        assertThat(handler.handle("{\"id\":2,\"cmd\":\"lookup\",\"ip\":\" 1.128.0.1 \"}").getBody()).isEqualTo(
                "{\"id\":2,\"ok\":true,\"result\":{\"ip\":\"1.128.0.1\",\"asn\":1221,\"as_org\":\"Telstra Pty Ltd\"}}");
    }

    @Test
    void lookupNotFound() {
        when(geo.lookup(any())).thenReturn(Optional.empty());
        assertThat(handler.handle("{\"id\":3,\"cmd\":\"lookup\",\"ip\":\"::ffff:10.0.0.1\"}").getBody()).isEqualTo(
                "{\"id\":3,\"ok\":false,\"error\":{\"code\":\"not_found\",\"message\":\"no location for 10.0.0.1\"}}");
    }

    @Test
    void lookupNeedsAnIp() {
        assertThat(handler.handle("{\"id\":4,\"cmd\":\"lookup\"}").getBody()).isEqualTo(
                "{\"id\":4,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"ip is required\"}}");
        assertThat(handler.handle("{\"id\":5,\"cmd\":\"lookup\",\"ip\":\"example.com\"}").getBody()).isEqualTo(
                "{\"id\":5,\"ok\":false,\"error\":{\"code\":\"bad_request\","
                        + "\"message\":\"not an IP address: example.com\"}}");
        verifyNoInteractions(geo);
    }

    @Test
    void aFailedLookupIsAnInternalError() {
        when(geo.lookup(any())).thenThrow(new UncheckedIOException("database read failed", new IOException("eof")));
        assertThat(handler.handle("{\"id\":6,\"cmd\":\"lookup\",\"ip\":\"81.2.69.142\"}").getBody()).isEqualTo(
                "{\"id\":6,\"ok\":false,\"error\":{\"code\":\"internal\","
                        + "\"message\":\"java.io.UncheckedIOException: database read failed\"}}");
    }

    @Test
    void replyToAndIdKinds() {
        RequestHandler.Reply reply = handler.handle(
                "{\"id\":{\"n\":1},\"cmd\":\"ping\",\"reply_to\":\"my.replies\"}");
        assertThat(reply.getReplyTo()).isEqualTo("my.replies");
        assertThat(reply.getKey()).isEqualTo("{\"n\":1}");
        assertThat(handler.handle("{\"cmd\":\"ping\"}").getKey()).isNull();
        assertThat(handler.handle("{\"id\":7,\"cmd\":\"ping\"}").getKey()).isEqualTo("7");
    }

    @Test
    void badRequests() {
        assertThat(handler.handle("").getBody())
                .isEqualTo("{\"id\":null,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"empty request\"}}");
        assertThat(handler.handle("[1]").getBody()).isEqualTo("{\"id\":null,\"ok\":false,\"error\":"
                + "{\"code\":\"bad_request\",\"message\":\"request must be a JSON object\"}}");
        assertThat(handler.handle("{nope").getBody()).startsWith(
                "{\"id\":null,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"invalid JSON: ");
        assertThat(handler.handle("{\"id\":8}").getBody()).isEqualTo(
                "{\"id\":8,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"cmd is required\"}}");
        assertThat(handler.handle("{\"id\":9,\"cmd\":\"locate\"}").getBody()).isEqualTo(
                "{\"id\":9,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"unknown cmd: locate\"}}");
        assertThat(handler.handle("{\"id\":10,\"cmd\":\"lookup\",\"ip\":[1]}").getBody()).startsWith(
                "{\"id\":10,\"ok\":false,\"error\":{\"code\":\"bad_request\",\"message\":\"invalid request: ");
        verifyNoInteractions(geo);
    }
}
