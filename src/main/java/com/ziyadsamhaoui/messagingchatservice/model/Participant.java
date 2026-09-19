package com.ziyadsamhaoui.messagingchatservice.model;

import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = Participant.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class Participant {

    public static final String COLLECTION = "participants";

    @Id
    private String id;

    private String roomId;

    private String userId;

    private ParticipantRole role;

    private String nickname;

    private Instant joinedAt;

    private Instant mutedUntil;

    @Field("isMuted")
    private boolean muted;

    public static Participant member(String roomId, String userId, ParticipantRole role, Instant joinedAt) {
        Participant participant = new Participant();
        participant.roomId = roomId;
        participant.userId = userId;
        participant.role = role;
        participant.joinedAt = joinedAt;
        return participant;
    }

    public boolean isOwner() {
        return role == ParticipantRole.OWNER;
    }

    public boolean isOwnerOrAdmin() {
        return role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN;
    }

    public boolean isMuteActive(Instant reference) {
        if (!muted) {
            return false;
        }
        return mutedUntil == null || mutedUntil.isAfter(reference);
    }
}
