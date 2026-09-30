package com.ziyadsamhaoui.messagingchatservice.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ziyadsamhaoui.messagingchatservice.cache.BlockCacheService;
import com.ziyadsamhaoui.messagingchatservice.cache.UserCacheService;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Sprint 6 §2.3/§3.4 (no Docker): the consumer dispatches each User event type to
 * the right cache, ignores topics/events it does not own, survives malformed
 * payloads (poison-pill protection), and applies redelivery through naturally
 * idempotent operations — the second delivery is safe to run.
 */
class UserProfileEventsConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UserCacheService userCacheService;
    private BlockCacheService blockCacheService;
    private UserProfileEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        userCacheService = mock(UserCacheService.class);
        blockCacheService = mock(BlockCacheService.class);
        consumer = new UserProfileEventsConsumer(userCacheService, blockCacheService, MAPPER);
    }

    @Test
    void profileCreatedPopulatesTheUsernameCache() {
        String json = envelope("USER_PROFILE_CREATED", """
                {"userId":"user-1","username":"zara","createdAt":"2026-01-01T10:00:00Z"}
                """);

        consumer.onMessage(record(json));

        verify(userCacheService).upsert("user-1", "zara", Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void usernameChangedUpdatesTheUsernameCache() {
        String json = envelope("USER_USERNAME_CHANGED", """
                {"userId":"user-1","newUsername":"zara2","changedAt":"2026-02-01T10:00:00Z"}
                """);

        consumer.onMessage(record(json));

        verify(userCacheService).upsert("user-1", "zara2", Instant.parse("2026-02-01T10:00:00Z"));
    }

    @Test
    void blockedAndUnblockedMaintainTheBlockCache() {
        consumer.onMessage(record(envelope("USER_BLOCKED", """
                {"blockerId":"a","blockedId":"b","blockedAt":"2026-01-01T10:00:00Z"}
                """)));
        verify(blockCacheService).recordBlocked("a", "b");

        consumer.onMessage(record(envelope("USER_UNBLOCKED", """
                {"blockerId":"a","blockedId":"b"}
                """)));
        verify(blockCacheService).recordUnblocked("a", "b");
    }

    @Test
    void redeliveryOfTheSameEventRunsTheIdempotentOperationAgainWithoutError() {
        String json = envelope("USER_PROFILE_CREATED", """
                {"userId":"user-1","username":"zara","createdAt":"2026-01-01T10:00:00Z"}
                """);

        consumer.onMessage(record(json));
        consumer.onMessage(record(json));

        // Upsert-by-userId is naturally idempotent (§3.4, option 1) — replay is a no-op
        // in effect. The cached value after the second delivery is the same as after the first.
        verify(userCacheService, times(2)).upsert("user-1", "zara", Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void unrelatedEventTypesAreIgnored() {
        consumer.onMessage(record(envelope("USER_ROLE_CHANGED", """
                {"userId":"user-1","role":"ADMIN","changedAt":"2026-01-01T10:00:00Z"}
                """)));

        verify(userCacheService, never()).upsert(anyString(), anyString(), any());
        verify(blockCacheService, never()).recordBlocked(anyString(), anyString());
    }

    @Test
    void malformedPayloadDoesNotThrowOutOfTheListener() {
        assertThatCode(() -> consumer.onMessage(record("not-json")))
                .doesNotThrowAnyException();
        assertThatCode(() -> consumer.onMessage(record(envelope("USER_BLOCKED", """
                {"missing":"fields"}
                """))))
                .doesNotThrowAnyException();
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(UserProfileEventsConsumer.TOPIC, 0, 0L, "key", value);
    }

    private String envelope(String eventType, String payloadJson) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-01-01T10:00:00Z",
                  "producer": "messaging-user-service",
                  "correlationId": "corr-1",
                  "aggregateId": "user-1",
                  "payload": %s
                }
                """.formatted(UUID.randomUUID(), eventType, payloadJson.trim());
    }
}
