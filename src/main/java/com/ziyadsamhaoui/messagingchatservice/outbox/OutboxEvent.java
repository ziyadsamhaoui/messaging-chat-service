package com.ziyadsamhaoui.messagingchatservice.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

@Document(collection = OutboxEvent.COLLECTION)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    public static final String COLLECTION = "outbox_events";

    @Id
    private String eventId;

    @Field("aggregateType")
    private String aggregateType;

    @Field("aggregateId")
    private String aggregateId;

    @Field("eventType")
    private String eventType;

    @Field("eventVersion")
    private int eventVersion;

    @Field("payload")
    private Object payload;

    @Field("correlationId")
    private String correlationId;

    @Field("createdAt")
    private Instant createdAt;

    @Field("publishedAt")
    private Instant publishedAt;

    @Transient
    private boolean published;
}
