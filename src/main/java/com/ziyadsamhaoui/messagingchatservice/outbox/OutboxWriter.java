package com.ziyadsamhaoui.messagingchatservice.outbox;

import com.ziyadsamhaoui.messagingchatservice.web.CorrelationContext;
import lombok.RequiredArgsConstructor;
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
                .payload(objectMapper.valueToTree(payload))
                .correlationId(CorrelationContext.current())
                .createdAt(Instant.now())
                .build();
        mongoTemplate.save(event);
    }
}
