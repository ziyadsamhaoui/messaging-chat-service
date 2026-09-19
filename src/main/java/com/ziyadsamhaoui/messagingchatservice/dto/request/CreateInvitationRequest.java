package com.ziyadsamhaoui.messagingchatservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;

public record CreateInvitationRequest(@NotBlank(message = "invitedUserId is required") String invitedUserId,
        Duration ttl) {
}
