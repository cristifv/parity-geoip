package parity.geoip.api;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import parity.geoip.config.GeoipProperties;

/**
 * The public API: takes requests from Kafka and writes each response, keyed by the request
 * {@code id}. The offset is committed once the response is written (ack mode
 * {@code record}). If the write fails, the request is run again a second later.
 */
@Component
@RequiredArgsConstructor
public class RequestListener {

    private final RequestHandler handler;
    private final KafkaTemplate<String, String> kafka;
    private final GeoipProperties properties;

    public static final String ID = "requests";

    @KafkaListener(id = ID, idIsGroup = false, topics = "${parity-geoip.topics.requests}")
    public void onRequest(ConsumerRecord<String, String> request) {
        RequestHandler.Reply reply = handler.handle(request.value());
        String topic = reply.getReplyTo() != null ? reply.getReplyTo() : properties.topics().responses();
        kafka.send(topic, reply.getKey(), reply.getBody()).join();
    }
}
