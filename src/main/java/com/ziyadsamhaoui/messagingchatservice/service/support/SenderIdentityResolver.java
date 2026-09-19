package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.exception.ChatServiceException;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SenderIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(SenderIdentityResolver.class);

    private final UserServiceClient userServiceClient;
    private final CurrentUserProvider currentUserProvider;

    public SenderIdentityResolver(UserServiceClient userServiceClient, CurrentUserProvider currentUserProvider) {
        this.userServiceClient = userServiceClient;
        this.currentUserProvider = currentUserProvider;
    }

    public String resolveUsername(String userId) {
        try {
            return userServiceClient.getProfile(userId).username();
        } catch (ChatServiceException exception) {
            log.warn("Falling back to token claims for the username of user {}: {}", userId, exception.getMessage());
            return currentUserProvider.usernameClaim().orElse(null);
        }
    }
}
