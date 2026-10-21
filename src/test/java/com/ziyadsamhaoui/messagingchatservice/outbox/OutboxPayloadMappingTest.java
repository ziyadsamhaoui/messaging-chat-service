package com.ziyadsamhaoui.messagingchatservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import tools.jackson.databind.ObjectMapper;

class OutboxPayloadMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MappingMongoConverter converter;
    private MongoTemplate mongoTemplate;
    private OutboxWriter writer;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());

        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setInitialEntitySet(Set.of(OutboxEvent.class));
        mappingContext.afterPropertiesSet();

        MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
        when(databaseFactory.getExceptionTranslator()).thenReturn(new MongoExceptionTranslator());

        DbRefResolver dbRefResolver = new DefaultDbRefResolver(databaseFactory);
        converter = new MappingMongoConverter(dbRefResolver, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        mongoTemplate = mock(MongoTemplate.class);
        writer = new OutboxWriter(mongoTemplate, MAPPER);
    }

    @Test
    void persistsAndReadsBackASimpleJsonPayload() {
        OutboxEvent saved = appendAndCapture(new ChatEvents.MessageSent(
                "msg-1", "room-1", "user-1", "zara", "TEXT", "hello", Instant.parse("2026-01-01T10:00:00Z")));

        Document stored = write(saved);

        Document storedPayload = stored.get("payload", Document.class);
        assertThat(storedPayload).isNotNull();
        assertThat(storedPayload).containsEntry("content", "hello").doesNotContainKeys("_class", "_children");
        assertThat(storedPayload).containsEntry("messageId", "msg-1").containsEntry("roomId", "room-1");

        OutboxEvent read = converter.read(OutboxEvent.class, stored);
        assertThat(read.getEventId()).isEqualTo(saved.getEventId());
        assertThat(read.getEventType()).isEqualTo(ChatEvents.MESSAGE_SENT);
        assertThat(read.getAggregateType()).isEqualTo("Message");
        assertThat(read.getAggregateId()).isEqualTo("msg-1");
        assertThat(read.getPayload()).isInstanceOf(Document.class);
        assertThat(read.getPayload().getString("content")).isEqualTo("hello");
    }

    @Test
    void preservesNestedObjectsAndArrays() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        nested.put("deep", Map.of("x", 1));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nested", nested);
        payload.put("items", List.of("a", "b", 3));

        OutboxEvent saved = appendAndCapture(payload);
        OutboxEvent read = converter.read(OutboxEvent.class, write(saved));

        Document readPayload = read.getPayload();
        assertThat(readPayload.get("nested", Document.class)).containsEntry("inner", "value");
        assertThat(readPayload.get("nested", Document.class).get("deep", Document.class)).containsEntry("x", 1);
        assertThat(readPayload.get("items", List.class)).containsExactly("a", "b", 3);
    }

    @Test
    void preservesNumericBooleanStringAndNullValues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "hello");
        payload.put("count", 42);
        payload.put("ratio", 1.5);
        payload.put("flag", true);
        payload.put("nothing", null);

        OutboxEvent saved = appendAndCapture(payload);
        OutboxEvent read = converter.read(OutboxEvent.class, write(saved));

        Document readPayload = read.getPayload();
        assertThat(readPayload.getString("text")).isEqualTo("hello");
        assertThat(readPayload.get("count")).isEqualTo(42);
        assertThat(readPayload.get("ratio")).isEqualTo(1.5);
        assertThat(readPayload.getBoolean("flag")).isTrue();
        assertThat(readPayload.containsKey("nothing")).isTrue();
        assertThat(readPayload.get("nothing")).isNull();
    }

    @Test
    void readsLegacyTreeNodesWithoutFailingAndNormalizesThemToPlainJson() {
        Document stored = new Document();
        stored.put("_id", "6d2f2d70-0000-4000-8000-000000000000");
        stored.put("aggregateType", "Message");
        stored.put("aggregateId", "msg-1");
        stored.put("eventType", ChatEvents.MESSAGE_SENT);
        stored.put("eventVersion", 1);
        stored.put("payload", Document.parse("""
                {"_children":{"text":{"_value":"hello","_class":"tools.jackson.databind.node.StringNode"},
                "count":{"_value":42,"_class":"tools.jackson.databind.node.IntNode"},
                "flag":{"_value":true,"_class":"tools.jackson.databind.node.BooleanNode"},
                "nothing":{"_class":"tools.jackson.databind.node.NullNode"},
                "items":{"_children":[{"_value":"a","_class":"tools.jackson.databind.node.StringNode"},
                {"_value":3,"_class":"tools.jackson.databind.node.IntNode"}],"_nodeFactory":{},
                "_class":"tools.jackson.databind.node.ArrayNode"},
                "nested":{"_children":{"inner":{"_value":"value",
                "_class":"tools.jackson.databind.node.StringNode"}},"_nodeFactory":{},
                "_class":"tools.jackson.databind.node.ObjectNode"}},"_nodeFactory":{},
                "_class":"tools.jackson.databind.node.ObjectNode"}"""));
        stored.put("createdAt", new java.util.Date());

        OutboxEvent read = converter.read(OutboxEvent.class, stored);

        assertThat(read.getPayload()).isInstanceOf(Document.class);
        Document normalized = OutboxRelay.normalizePayload(read.getPayload());
        assertThat(normalized.getString("text")).isEqualTo("hello");
        assertThat(normalized.get("count")).isEqualTo(42);
        assertThat(normalized.getBoolean("flag")).isTrue();
        assertThat(normalized.containsKey("nothing")).isTrue();
        assertThat(normalized.get("nothing")).isNull();
        assertThat(normalized.get("items", List.class)).containsExactly("a", 3);
        assertThat(normalized.get("nested", Document.class)).containsEntry("inner", "value");
        assertThat(normalized).doesNotContainKeys("_children", "_nodeFactory", "_class");
    }

    @Test
    void leavesAlreadyDocumentShapedPayloadsUnchangedWhenNormalizing() {
        Document payload = Document.parse("{\"content\":\"hello\",\"items\":[1,2]}");

        Document normalized = OutboxRelay.normalizePayload(payload);

        assertThat(normalized).isEqualTo(payload);
    }

    private OutboxEvent appendAndCapture(Object payload) {
        writer.append("Message", "msg-1", ChatEvents.MESSAGE_SENT, payload);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(mongoTemplate).save(captor.capture());
        return captor.getValue();
    }

    private Document write(OutboxEvent entity) {
        Document document = new Document();
        converter.write(entity, document);
        return document;
    }
}
