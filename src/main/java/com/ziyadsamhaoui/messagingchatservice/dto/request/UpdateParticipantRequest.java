package com.ziyadsamhaoui.messagingchatservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;

public record UpdateParticipantRequest(ParticipantRole role,

        @JsonProperty("isMuted") Boolean muted,

        Instant mutedUntil) {

    public boolean hasRoleChange() {
        return role != null;
    }

    public boolean hasMuteChange() {
        return muted != null || mutedUntil != null;
    }
}
