package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationDirection;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import java.time.Instant;

/**
 * One row of the caller's pending-invitation inbox. {@code direction} is
 * viewer-relative (like {@code ConnectionDto.direction}): {@code INCOMING} rows
 * are actionable by the caller, {@code OUTGOING} rows were sent by the caller.
 * {@code roomName} is resolved server-side because the recipient is not yet a
 * participant of the room and therefore cannot read it over REST.
 */
public record PendingInvitationResponse(String id, String roomId, String roomName, String inviterId,
        String invitedId, InvitationStatus status, InvitationDirection direction, Instant sentAt, Instant expiresAt) {

    public static PendingInvitationResponse of(Invitation invitation, InvitationDirection direction, String roomName) {
        return new PendingInvitationResponse(invitation.getId(), invitation.getRoomId(), roomName,
                invitation.getInviterId(), invitation.getInvitedId(), invitation.getStatus(), direction,
                invitation.getSentAt(), invitation.getExpiresAt());
    }
}
