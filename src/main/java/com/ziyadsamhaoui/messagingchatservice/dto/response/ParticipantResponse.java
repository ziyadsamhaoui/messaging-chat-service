package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;

public record ParticipantResponse(String id, String roomId, String userId, ParticipantRole role, String nickname,
        Instant joinedAt, @JsonProperty("isMuted") boolean muted, Instant mutedUntil) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getRoomId(), participant.getUserId(),
                participant.getRole(), participant.getNickname(), participant.getJoinedAt(), participant.isMuted(),
                participant.getMutedUntil());
    }
}
