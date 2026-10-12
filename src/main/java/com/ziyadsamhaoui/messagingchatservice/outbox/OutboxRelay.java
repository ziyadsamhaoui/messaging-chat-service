package com.ziyadsamhaoui.messagingchatservice.outbox;

import com.ziyadsamhaoui.messagingchatservice.config.KafkaOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    /**
     * Node class prefix used by Jackson 3 ({@code tools.jackson}) and Jackson 2
     * ({@code com.fasterxml.jackson}) tree nodes. Payloads written by the previous
     * implementation were persisted as a serialized tree node, so records already
     * sitting in the outbox still carry this shape and are unwrapped on read.
     */
    private static final String JACKSON_3_NODE_PREFIX = "tools.jackson.databind.node.";
    private static final String JACKSON_2_NODE_PREFIX = "com.fasterxml.jackson.databind.node.";

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaOutboxProperties properties;

    @Scheduled(fixedDelayString = "${badrlink.kafka.outbox.relay-interval:500ms}")
    public void publishPending() {
        if (!properties.enabled()) {
            return;
        }
        Query query = Query.query(Criteria.where("publishedAt").isNull())
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(properties.batchSize());
        List<OutboxEvent> batch = mongoTemplate.find(query, OutboxEvent.class, OutboxEvent.COLLECTION);
        for (OutboxEvent event : batch) {
            send(event);
        }
        if (!batch.isEmpty()) {
            log.debug("outbox relay published {} event(s)", batch.size());
        }
    }

    private void send(OutboxEvent event) {
        try {
            String topic = topicFor(event.getAggregateType());
            String envelopeJson = objectMapper.writeValueAsString(toEnvelope(event));
            kafkaTemplate.send(topic, event.getAggregateId(), envelopeJson).get(10, TimeUnit.SECONDS);
            Query mark = Query.query(Criteria.where("_id").is(event.getEventId()));
            mongoTemplate.updateFirst(mark, Update.update("publishedAt", Instant.now()), OutboxEvent.COLLECTION);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox relay interrupted while publishing " + event.getEventId(), ex);
        } catch (Exception ex) {
            log.error("outbox relay failed to publish {}; will retry next tick", event.getEventId(), ex);
        }
    }

    private String topicFor(String aggregateType) {
        return switch (aggregateType) {
            case "Message" -> ChatEvents.TOPIC_MESSAGE;
            case "MessageReaction" -> ChatEvents.TOPIC_MESSAGE;
            case "ChatRoom" -> ChatEvents.TOPIC_ROOM;
            case "Participant" -> ChatEvents.TOPIC_ROOM;
            case "Invitation" -> ChatEvents.TOPIC_INVITATION;
            default -> throw new IllegalStateException("No topic mapped for aggregate " + aggregateType);
        };
    }

    private EventEnvelope toEnvelope(OutboxEvent event) {
        return new EventEnvelope(
                UUID.fromString(event.getEventId()),
                event.getEventType(),
                event.getEventVersion(),
                event.getCreatedAt(),
                EventEnvelope.PRODUCER,
                event.getCorrelationId(),
                event.getAggregateId(),
                normalizePayload(event.getPayload()));
    }

    /**
     * Returns the payload as a plain BSON document, unwrapping the legacy tree-node
     * representation when a record was written by the previous implementation. New
     * records are already stored as documents and pass through unchanged.
     */
    static Document normalizePayload(Document payload) {
        if (payload == null) {
            return null;
        }
        Object normalized = unwrapNode(payload);
        return normalized instanceof Document document ? document : new Document("value", normalized);
    }

    private static Object unwrapNode(Object value) {
        if (value instanceof Document document) {
            return unwrapDocumentNode(document);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(OutboxRelay::unwrapNode).toList();
        }
        return value;
    }

    private static Object unwrapDocumentNode(Document node) {
        String nodeClass = node.getString("_class");
        if (nodeClass == null
                || !(nodeClass.startsWith(JACKSON_3_NODE_PREFIX) || nodeClass.startsWith(JACKSON_2_NODE_PREFIX))) {
            return node;
        }
        if (nodeClass.endsWith("NullNode") || nodeClass.endsWith("MissingNode")) {
            return null;
        }
        if (nodeClass.endsWith("ArrayNode")) {
            Object children = node.get("_children");
            return children instanceof List<?> list
                    ? list.stream().map(OutboxRelay::unwrapNode).toList()
                    : List.of();
        }
        if (nodeClass.endsWith("ObjectNode")) {
            Object children = node.get("_children");
            if (children instanceof Document childDocument) {
                Document result = new Document();
                for (Map.Entry<String, Object> entry : childDocument.entrySet()) {
                    result.put(entry.getKey(), unwrapNode(entry.getValue()));
                }
                return result;
            }
            return new Document();
        }
        return node.containsKey("_value") ? unwrapNode(node.get("_value")) : node;
    }
}
