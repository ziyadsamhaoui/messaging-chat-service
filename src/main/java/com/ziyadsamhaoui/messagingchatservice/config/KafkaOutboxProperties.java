package com.ziyadsamhaoui.messagingchatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;


@ConfigurationProperties(prefix = "badrlink.kafka.outbox")
public record KafkaOutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("localhost:9092") String bootstrapServers) {
}
