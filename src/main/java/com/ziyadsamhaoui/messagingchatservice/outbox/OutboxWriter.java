package com.ziyadsamhaoui.messagingchatservice.outbox;

import com.ziyadsamhaoui.messagingchatservice.web.CorrelationContext;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, String eventType, Object payload) {
        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .eventVersion(EventEnvelope.VERSION_1)
                .payload(toPayloadDocument(payload))
                .correlationId(CorrelationContext.current())
                .createdAt(Instant.now())
                .build();
        mongoTemplate.save(event);
    }

    /**
     * The payload is persisted as a native BSON document so the event JSON keeps
     * its nested objects, arrays, numbers, booleans, strings and nulls. Storing a
     * Jackson tree node instead makes Spring Data persist the node's internal
     * shape and fail to instantiate it again on read.
     */
    private Document toPayloadDocument(Object payload) {
        return Document.parse(objectMapper.writeValueAsString(payload));
    }
}
