package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import org.springframework.stereotype.Component;

@Component
public class BlockPolicy {

    private final UserServiceClient userServiceClient;

    public BlockPolicy(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    public void assertNotBlocked(String firstUserId, String secondUserId) {
        if (userServiceClient.isBlockedBetween(firstUserId, secondUserId)) {
            throw new ForbiddenOperationException("BLOCKED_RELATIONSHIP",
                    "A block exists between the requested participants");
        }
    }
}
