package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;
import java.util.List;

public interface ParticipantRepositoryCustom {

    List<Participant> findPageByRoom(String roomId, String cursorId, int limit);

    void updateRole(String roomId, String userId, ParticipantRole role);

    void updateMute(String roomId, String userId, boolean muted, Instant mutedUntil);
}
