package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import java.time.Instant;

public record InvitationResponse(String id, String roomId, String inviterId, String invitedId, InvitationStatus status,
        Instant sentAt, Instant expiresAt) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getRoomId(), invitation.getInviterId(),
                invitation.getInvitedId(), invitation.getStatus(), invitation.getSentAt(), invitation.getExpiresAt());
    }
}
