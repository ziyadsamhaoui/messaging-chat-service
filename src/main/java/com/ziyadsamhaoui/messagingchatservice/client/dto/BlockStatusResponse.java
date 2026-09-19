package com.ziyadsamhaoui.messagingchatservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlockStatusResponse(boolean blocked) {
}
