package com.ziyadsamhaoui.messagingchatservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EditMessageRequest(@NotBlank(message = "content is required") String content) {
}
