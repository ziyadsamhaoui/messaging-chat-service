package com.ziyadsamhaoui.messagingchatservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "badrlink.user-service")
public record UserServiceProperties(@DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("/internal/users/{userId}") String profilePath,
        @DefaultValue("/internal/users/block-status") String blockStatusPath,
        @DefaultValue("X-BadrLink-Internal-Token") String internalTokenHeader, String internalToken,
        @DefaultValue("2s") Duration connectTimeout, @DefaultValue("3s") Duration readTimeout) {
}
