package com.ziyadsamhaoui.messagingchatservice.kafka;

import com.ziyadsamhaoui.messagingchatservice.cache.BlockCacheService;
import com.ziyadsamhaoui.messagingchatservice.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;


@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "badrlink.kafka", name = "enabled", havingValue = "true")
public class UserProfileEventsConsumer {

    public static final String TOPIC = "badrlink.user.profile.v1";
    public static final String GROUP_ID = "messaging-chat-service";

    private final UserCacheService userCacheService;
    private final BlockCacheService blockCacheService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload = envelope.get("payload");
            switch (envelope.get("eventType").asString()) {
                case "USER_PROFILE_CREATED" -> userCacheService.upsert(
                        payload.get("userId").asString(),
                        payload.get("username").asString(),
                        Instant.parse(payload.get("createdAt").asString()));
                case "USER_USERNAME_CHANGED" -> userCacheService.upsert(
                        payload.get("userId").asString(),
                        payload.get("newUsername").asString(),
                        Instant.parse(payload.get("changedAt").asString()));
                case "USER_BLOCKED" -> blockCacheService.recordBlocked(
                        payload.get("blockerId").asString(),
                        payload.get("blockedId").asString());
                case "USER_UNBLOCKED" -> blockCacheService.recordUnblocked(
                        payload.get("blockerId").asString(),
                        payload.get("blockedId").asString());
                default -> log.debug("ignoring event type {} on {}",
                        envelope.get("eventType").asString(), TOPIC);
            }
        } catch (RuntimeException ex) {
            log.error("failed to process event from {}: {}", UserProfileEventsConsumer.TOPIC, record.value(), ex);
        }
    }
}
