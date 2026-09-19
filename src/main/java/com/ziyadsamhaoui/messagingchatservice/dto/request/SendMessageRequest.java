package com.ziyadsamhaoui.messagingchatservice.dto.request;

import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(@NotNull(message = "type is required") MessageType type, String content) {
}
