package com.ziyadsamhaoui.messagingchatservice.client;

import com.ziyadsamhaoui.messagingchatservice.client.dto.BlockStatusResponse;
import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;
import com.ziyadsamhaoui.messagingchatservice.config.UserServiceProperties;
import com.ziyadsamhaoui.messagingchatservice.exception.DependencyUnavailableException;
import com.ziyadsamhaoui.messagingchatservice.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpUserServiceClient implements UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpUserServiceClient.class);

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public HttpUserServiceClient(RestClient restClient, UserServiceProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public UserProfileResponse getProfile(String userId) {
        try {
            UserProfileResponse profile = restClient.get().uri(properties.profilePath(), userId).retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                        throw new UserNotFoundException(userId);
                    }).body(UserProfileResponse.class);

            if (profile == null || !StringUtils.hasText(profile.username())) {
                throw new DependencyUnavailableException(
                        "The user service returned an incomplete profile for user " + userId);
            }

            return profile;
        } catch (RestClientException exception) {
            log.warn("User profile lookup failed for user {}", userId, exception);
            throw new DependencyUnavailableException("The user service is unavailable");
        }
    }

    @Override
    public boolean isBlockedBetween(String firstUserId, String secondUserId) {
        try {
            BlockStatusResponse blockStatus = restClient.get()
                    .uri(builder -> builder.path(properties.blockStatusPath()).queryParam("userId", firstUserId)
                            .queryParam("otherUserId", secondUserId).build())
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                    }).body(BlockStatusResponse.class);

            return blockStatus != null && blockStatus.blocked();
        } catch (RestClientException exception) {
            log.warn("Block status verification failed between {} and {}", firstUserId, secondUserId, exception);
            throw new DependencyUnavailableException("Block status verification is unavailable");
        }
    }
}
