package com.ziyadsamhaoui.messagingchatservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "badrlink.chat")
public record ChatProperties(@DefaultValue("30") int defaultPageSize, @DefaultValue("100") int maxPageSize,
        @DefaultValue("100") int maxGroupSize, @DefaultValue("4000") int maxMessageLength,
        @DefaultValue("72h") Duration defaultInvitationTtl, @DefaultValue("30d") Duration maxInvitationTtl,
        @DefaultValue("true") boolean ensureIndexes, String internalServiceToken) {
}
