package com.ziyadsamhaoui.messagingchatservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bson.Document;
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        ProducerRecord<String, String> record = capturedRecord();
        assertThat(record.topic()).isEqualTo(ChatEvents.TOPIC_MESSAGE);
        assertThat(record.key()).isEqualTo("msg-1");

        JsonNode envelope = MAPPER.readTree(record.value());
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
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        relay.publishPending();

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }

    @Test
    void emptyBatchDoesNothing() {
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void publishesLegacyStoredPayloadAsPlainJson() throws Exception {
        Document legacyPayload = Document.parse("""
                {"_children":{"content":{"_value":"hello",
                "_class":"tools.jackson.databind.node.StringNode"},"count":{"_value":42,
                "_class":"tools.jackson.databind.node.IntNode"}},"_nodeFactory":{},
                "_class":"tools.jackson.databind.node.ObjectNode"}""");
        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType("Message")
                .aggregateId("msg-1")
                .eventType(ChatEvents.MESSAGE_SENT)
                .eventVersion(1)
                .payload(legacyPayload)
                .createdAt(Instant.now())
                .build();
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        ProducerRecord<String, String> record = capturedRecord();
        assertThat(record.topic()).isEqualTo(ChatEvents.TOPIC_MESSAGE);
        assertThat(record.key()).isEqualTo("msg-1");
        JsonNode envelope = MAPPER.readTree(record.value());
        assertThat(envelope.get("payload").get("content").asString()).isEqualTo("hello");
        assertThat(envelope.get("payload").get("count").asInt()).isEqualTo(42);
        assertThat(envelope.get("payload").has("_children")).isFalse();
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(OutboxEvent.COLLECTION));
    }

    @Test
    void unmappedAggregateKeepsTheEventPendingAndDoesNotBreakTheTick() {
        OutboxEvent event = eventFor("Unknown", "SOMETHING_HAPPENED", new Object());
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }

    @Test
    void storedTraceParentIsInjectedIntoTheKafkaRecord() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        OutboxEvent event = eventFor("Message", ChatEvents.MESSAGE_SENT,
                new ChatEvents.MessageSent("msg-1", "room-1", "user-1", "zara", "TEXT", "hello", Instant.now()));
        event.setTraceParent(traceParent);
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        ProducerRecord<String, String> record = capturedRecord();
        assertThat(record.headers().lastHeader("traceparent")).isNotNull();
        assertThat(new String(record.headers().lastHeader("traceparent").value())).isEqualTo(traceParent);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(OutboxEvent.COLLECTION));
    }

    @Test
    void documentsWithoutTraceContextPublishNoTraceparentHeader() {
        OutboxEvent event = eventFor("Message", ChatEvents.MESSAGE_SENT,
                new ChatEvents.MessageSent("msg-1", "room-1", "user-1", "zara", "TEXT", "hello", Instant.now()));
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        assertThat(capturedRecord().headers().lastHeader("traceparent")).isNull();
    }

    private String topicUsedFor(String aggregateType) {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq(OutboxEvent.COLLECTION)))
                .thenReturn(List.of(eventFor(aggregateType, "SOMETHING_HAPPENED", new Object())));
        OutboxRelay localRelay = new OutboxRelay(mongoTemplate, template, MAPPER, PROPERTIES);

        localRelay.publishPending();

        return capturedRecord(template).topic();
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> capturedRecord() {
        return capturedRecord(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    private static ProducerRecord<String, String> capturedRecord(KafkaTemplate<String, String> template) {
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                (ArgumentCaptor<ProducerRecord<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor
                        .forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }

    private OutboxEvent eventFor(String aggregateType, String eventType, Object payload) {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(aggregateType)
                .aggregateId("msg-1")
                .eventType(eventType)
                .eventVersion(1)
                .payload(Document.parse(MAPPER.writeValueAsString(payload)))
                .createdAt(Instant.now())
                .build();
    }
}
