package com.ziyadsamhaoui.messagingchatservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.config.KafkaOutboxProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sprint 6 §3.3/§3.5 (no Docker): the relay routes each aggregate to its topic,
 * stamps {@code publishedAt} only after the Kafka ack, and leaves the row
 * unpublished when the send fails so the next tick retries it (at-least-once).
 */
class OutboxRelayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final KafkaOutboxProperties PROPERTIES =
            new KafkaOutboxProperties(true, 100, "localhost:9092");

    private MongoTemplate mongoTemplate;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        relay = new OutboxRelay(mongoTemplate, kafkaTemplate, MAPPER, PROPERTIES);
    }

    @Test
    void publishesMessageEventToMessageTopicWithEnvelopeAndMarksPublished() throws Exception {
        OutboxEvent event = eventFor("Message", ChatEvents.MESSAGE_SENT,
                new ChatEvents.MessageSent("msg-1", "room-1", "user-1", "zara", "TEXT", "hello", Instant.now()));
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), body.capture());
        assertThat(topic.getValue()).isEqualTo(ChatEvents.TOPIC_MESSAGE);
        assertThat(key.getValue()).isEqualTo("msg-1");

        JsonNode envelope = MAPPER.readTree(body.getValue());
        assertThat(envelope.get("eventType").asString()).isEqualTo(ChatEvents.MESSAGE_SENT);
        assertThat(envelope.get("producer").asString()).isEqualTo("messaging-chat-service");
        assertThat(envelope.get("aggregateId").asString()).isEqualTo("msg-1");
        assertThat(envelope.get("eventId").asString()).isEqualTo(event.getEventId());
        assertThat(envelope.get("payload").get("content").asString()).isEqualTo("hello");

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(OutboxEvent.COLLECTION));
    }

    @Test
    void routesRoomAndInvitationAggregatesToTheirOwnTopics() {
        assertThat(topicUsedFor("ChatRoom")).isEqualTo(ChatEvents.TOPIC_ROOM);
        assertThat(topicUsedFor("Participant")).isEqualTo(ChatEvents.TOPIC_ROOM);
        assertThat(topicUsedFor("MessageReaction")).isEqualTo(ChatEvents.TOPIC_MESSAGE);
        assertThat(topicUsedFor("Invitation")).isEqualTo(ChatEvents.TOPIC_INVITATION);
    }

    @Test
    void failedPublishDoesNotMarkPublished() {
        OutboxEvent event = eventFor("Message", ChatEvents.MESSAGE_SENT,
                new ChatEvents.MessageSent("msg-1", "room-1", "user-1", "zara", "TEXT", "hello", Instant.now()));
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        relay.publishPending();

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }

    @Test
    void emptyBatchDoesNothing() {
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private String topicUsedFor(String aggregateType) {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(eventFor(aggregateType, "SOMETHING_HAPPENED", new Object())));
        OutboxRelay localRelay = new OutboxRelay(mongoTemplate, template, MAPPER, PROPERTIES);

        localRelay.publishPending();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(template).send(topic.capture(), anyString(), anyString());
        return topic.getValue();
    }

    private OutboxEvent eventFor(String aggregateType, String eventType, Object payload) {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(aggregateType)
                .aggregateId("msg-1")
                .eventType(eventType)
                .eventVersion(1)
                .payload(MAPPER.valueToTree(payload))
                .createdAt(Instant.now())
                .build();
    }
}
