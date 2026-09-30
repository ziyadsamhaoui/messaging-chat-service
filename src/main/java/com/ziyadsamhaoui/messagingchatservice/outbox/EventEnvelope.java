package com.ziyadsamhaoui.messagingchatservice.outbox;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        Object payload) {

    public static final String PRODUCER = "messaging-chat-service";
    public static final int VERSION_1 = 1;
}
