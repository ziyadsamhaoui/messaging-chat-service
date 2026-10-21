package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.cache.BlockCacheService;
import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        if (userServiceClient.isBlockedBetween(firstUserId, secondUserId)) {
            throw new ForbiddenOperationException("BLOCKED_RELATIONSHIP",
                    "A block exists between the requested participants");
        }
    }
}
