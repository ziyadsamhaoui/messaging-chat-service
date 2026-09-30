package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.cache.BlockCacheService;
import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sprint 6 §2.3 — block enforcement, cache-first:
 *
 * <ol>
 *   <li>cache hit ("blocked") → reject immediately;</li>
 *   <li>cache miss ("unknown") → the synchronous User call runs once, exactly as
 *       before — and it fails closed (DependencyUnavailableException on outage),
 *       so an unconfirmed block is never treated as "not blocked". Blocks are
 *       security-relevant: unlike username eventual-consistency (cosmetic), block
 *       eventual-consistency is a policy bypass, so we deliberately do NOT drop
 *       the synchronous call here.</li>
 * </ol>
 */
@Component
public class BlockPolicy {

    private static final Logger log = LoggerFactory.getLogger(BlockPolicy.class);

    private final UserServiceClient userServiceClient;
    private final BlockCacheService blockCacheService;

    public BlockPolicy(UserServiceClient userServiceClient, BlockCacheService blockCacheService) {
        this.userServiceClient = userServiceClient;
        this.blockCacheService = blockCacheService;
    }

    public void assertNotBlocked(String firstUserId, String secondUserId) {
        if (blockCacheService.isKnownBlocked(firstUserId, secondUserId)) {
            throw new ForbiddenOperationException("BLOCKED_RELATIONSHIP",
                    "A block exists between the requested participants");
        }
        // Cache miss = "unknown", not "not blocked": verify synchronously (fail closed).
        if (userServiceClient.isBlockedBetween(firstUserId, secondUserId)) {
            throw new ForbiddenOperationException("BLOCKED_RELATIONSHIP",
                    "A block exists between the requested participants");
        }
    }
}
