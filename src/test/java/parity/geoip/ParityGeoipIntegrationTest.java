package parity.geoip;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import parity.geoip.geo.TestDatabases;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** End to end: requests over an embedded Kafka broker, lookups in MaxMind's test databases. */
// The embedded broker logs storage errors while the JVM deletes its log dir at exit.
@SpringBootTest(properties = {
        "logging.level.kafka=off", "logging.level.org.apache.kafka.storage=off",
        "parity-geoip.databases.city=" + TestDatabases.CITY,
        "parity-geoip.databases.asn=" + TestDatabases.ASN,
        "parity-geoip.zones[0].name=us1", "parity-geoip.zones[0].lat=39.04", "parity-geoip.zones[0].lon=-77.49",
        "parity-geoip.zones[1].name=eu1", "parity-geoip.zones[1].lat=50.11", "parity-geoip.zones[1].lon=8.68",
        "parity-geoip.lifecycle.healthy-file=" + ParityGeoipIntegrationTest.HEALTHY,
        "parity-geoip.lifecycle.drained-file=target/parity-geoip-test.drained"})
@EmbeddedKafka(
        partitions = 1,
        topics = {ParityGeoipIntegrationTest.REQUESTS, ParityGeoipIntegrationTest.RESPONSES,
                  ParityGeoipIntegrationTest.REPLIES},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ParityGeoipIntegrationTest {

    static final String REQUESTS = "geoip.requests";
    static final String RESPONSES = "geoip.responses";
    static final String REPLIES = "test.replies";
    static final String HEALTHY = "target/parity-geoip-test.healthy";

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private JsonMapper json;

    private Consumer<String, String> consumer;
    private final Map<String, Map<String, JsonNode>> byTopicAndKey = new HashMap<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                new StringDeserializer(), new StringDeserializer());
        List<TopicPartition> partitions = Stream.of(RESPONSES, REPLIES)
                .map(topic -> new TopicPartition(topic, 0))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void createsTheHealthyFileOnceStarted() {
        assertThat(Path.of(HEALTHY)).exists().isEmptyFile();
    }

    @Test
    void looksUpLocationAsnAndNearestZone() {
        JsonNode london = request("{\"id\": \"1\", \"cmd\": \"lookup\", \"ip\": \"81.2.69.142\"}");
        assertThat(london.get("ok").asBoolean()).isTrue();
        assertThat(london.at("/result/country").asString()).isEqualTo("GB");
        assertThat(london.at("/result/region").asString()).isEqualTo("ENG");
        assertThat(london.at("/result/city").asString()).isEqualTo("London");
        assertThat(london.at("/result/nearest_zone").asString()).isEqualTo("eu1");
        assertThat(london.at("/result/zones/0/distance_km").asLong()).isEqualTo(635);
        assertThat(london.at("/result/zones/1/name").asString()).isEqualTo("us1");

        JsonNode milton = request("{\"id\": \"2\", \"cmd\": \"lookup\", \"ip\": \"216.160.83.56\"}");
        assertThat(milton.at("/result/region").asString()).isEqualTo("WA");
        assertThat(milton.at("/result/nearest_zone").asString()).isEqualTo("us1");

        JsonNode telstra = request("{\"id\": \"3\", \"cmd\": \"lookup\", \"ip\": \"1.128.0.1\"}");
        assertThat(telstra.at("/result/asn").asLong()).isEqualTo(1221);
        assertThat(telstra.at("/result/as_org").asString()).isEqualTo("Telstra Pty Ltd");
        assertThat(telstra.at("/result").has("country")).isFalse();

        JsonNode unknown = request("{\"id\": \"4\", \"cmd\": \"lookup\", \"ip\": \"10.0.0.1\"}");
        assertThat(unknown.at("/error/code").asString()).isEqualTo("not_found");
    }

    @Test
    void replyToAndBadRequests() {
        String id = UUID.randomUUID().toString();
        kafka.send(REQUESTS, "{\"id\": \"%s\", \"cmd\": \"ping\", \"reply_to\": \"%s\"}".formatted(id, REPLIES));
        assertThat(await(REPLIES, id).at("/result/pong").asBoolean()).isTrue();

        JsonNode bad = request("{\"id\": \"b1\", \"cmd\": \"lookup\", \"ip\": \"localhost\"}");
        assertThat(bad.at("/error/code").asString()).isEqualTo("bad_request");
        assertThat(bad.at("/error/message").asString()).isEqualTo("not an IP address: localhost");
    }

    /** Sends a request (ids are made unique per call) and waits for its response. */
    private JsonNode request(String body) {
        JsonNode tree = json.readTree(body);
        String id = tree.get("id").asString() + "-" + UUID.randomUUID();
        ((ObjectNode) tree).put("id", id);
        kafka.send(REQUESTS, json.writeValueAsString(tree));
        return await(RESPONSES, id);
    }

    private JsonNode await(String topic, String key) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = byTopicAndKey.getOrDefault(topic, Map.of()).get(key);
            if (found != null) {
                return found;
            }
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                byTopicAndKey.computeIfAbsent(record.topic(), t -> new HashMap<>())
                        .put(record.key(), json.readTree(record.value()));
            }
        }
        throw new AssertionError("no message with key " + key + " on " + topic);
    }
}
