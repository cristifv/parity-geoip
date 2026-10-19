package parity.geoip.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import parity.geoip.geo.GeoLookupService;
import parity.geoip.geo.IpAddresses;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs one JSON request and builds its response. Every failure becomes an error response. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestHandler {

    /** The response to send: its topic ({@code reply_to}, or null for the default), key and JSON. */
    @Value
    public static class Reply {
        String replyTo;
        String key;
        String body;
    }

    private final GeoLookupService geo;
    private final JsonMapper json;
    private final Validator validator;

    public Reply handle(String payload) {
        JsonNode id = null;
        String replyTo = null;
        Response response;
        try {
            JsonNode tree = parse(payload);
            id = tree.get("id");
            replyTo = text(tree.get("reply_to"));
            response = Response.ok(id, dispatch(convert(tree)));
        } catch (ApiException e) {
            response = Response.error(id, e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("request failed: {}", payload, e);
            response = Response.error(id, "internal", e.toString());
        }
        return new Reply(replyTo, key(id), json.writeValueAsString(response));
    }

    private Object dispatch(GeoRequest request) {
        validate(request, Default.class);
        return switch (request.getCmd()) {
            case "ping" -> Map.of("pong", true);
            case "lookup" -> {
                validate(request, GeoRequest.Lookup.class);
                InetAddress address = address(request.getIp());
                yield geo.lookup(address)
                        .orElseThrow(() -> ApiException.notFound("no location for " + address.getHostAddress()));
            }
            default -> throw ApiException.badRequest("unknown cmd: " + request.getCmd());
        };
    }

    private static InetAddress address(String ip) {
        try {
            return IpAddresses.parse(ip.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private void validate(GeoRequest request, Class<?> group) {
        Set<ConstraintViolation<GeoRequest>> violations = validator.validate(request, group);
        if (!violations.isEmpty()) {
            throw ApiException.badRequest(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw ApiException.badRequest("empty request");
        }
        JsonNode tree;
        try {
            tree = json.readTree(payload);
        } catch (JacksonException e) {
            throw ApiException.badRequest("invalid JSON: " + e.getOriginalMessage());
        }
        if (tree == null || !tree.isObject()) {
            throw ApiException.badRequest("request must be a JSON object");
        }
        return tree;
    }

    private GeoRequest convert(JsonNode tree) {
        try {
            return json.treeToValue(tree, GeoRequest.class);
        } catch (JacksonException e) {
            throw ApiException.badRequest("invalid request: " + e.getOriginalMessage());
        }
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }

    private static String key(JsonNode id) {
        if (id == null || id.isNull()) {
            return null;
        }
        return id.isValueNode() ? id.asString() : id.toString();
    }
}
