package com.ziyadsamhaoui.messagingchatservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileResponse(String id, String username) {
}
