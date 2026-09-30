package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.cache.UserCacheService;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sprint 6 §2.3 — sender display-name resolution, cache-first:
 *
 * <ol>
 *   <li>local {@code user_cache} (fed by USER_PROFILE_CREATED / USER_USERNAME_CHANGED)
 *       — the normal path, zero network hops;</li>
 *   <li>JWT-claim fallback for the cold-start case: a brand-new user's first message
 *       may arrive before their USER_PROFILE_CREATED event has been consumed; the
 *       token itself still carries a correct display name. This fallback is exactly
 *       what makes it safe to drop the synchronous User call for username
 *       resolution entirely (guide §2.3).</li>
 * </ol>
 */
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
