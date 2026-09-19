package com.ziyadsamhaoui.messagingchatservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateReadCursorRequest(
        @NotBlank(message = "lastReadMessageId is required") String lastReadMessageId) {
}
