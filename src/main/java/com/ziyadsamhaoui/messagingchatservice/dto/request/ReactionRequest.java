package com.ziyadsamhaoui.messagingchatservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(
        @NotBlank(message = "emoji is required") @Size(max = 32, message = "emoji must not exceed 32 characters") String emoji) {
}
