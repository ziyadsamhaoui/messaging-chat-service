package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.cache.UserCacheService;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SenderIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(SenderIdentityResolver.class);

    private final UserCacheService userCacheService;
    private final CurrentUserProvider currentUserProvider;

    public SenderIdentityResolver(UserCacheService userCacheService, CurrentUserProvider currentUserProvider) {
        this.userCacheService = userCacheService;
        this.currentUserProvider = currentUserProvider;
    }

    public String resolveUsername(String userId) {
        return userCacheService.lookup(userId)
                .orElseGet(() -> {
                    String fallback = currentUserProvider.usernameClaim().orElse(null);
                    if (fallback == null) {
                        log.warn("No cached username and no token claim for user {}", userId);
                    }
                    return fallback;
                });
    }
}
