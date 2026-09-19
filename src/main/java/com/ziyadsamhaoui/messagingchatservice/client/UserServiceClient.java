package com.ziyadsamhaoui.messagingchatservice.client;

import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;

public interface UserServiceClient {

    UserProfileResponse getProfile(String userId);

    boolean isBlockedBetween(String firstUserId, String secondUserId);
}
