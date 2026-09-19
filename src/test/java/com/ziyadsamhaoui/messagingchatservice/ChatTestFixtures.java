package com.ziyadsamhaoui.messagingchatservice;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;

public final class ChatTestFixtures {

    public static final String ROOM_ID = "64b7f0c2f1a2b3c4d5e6f7a8";
    public static final String MESSAGE_ID = "64b7f0c2f1a2b3c4d5e6f7a9";
    public static final String INVITATION_ID = "64b7f0c2f1a2b3c4d5e6f7aa";
    public static final Instant REFERENCE_TIME = Instant.parse("2026-01-01T10:00:00Z");

    private ChatTestFixtures() {
    }

    public static ChatRoom groupRoom(String createdBy) {
        ChatRoom room = ChatRoom.group("badrlink-core", createdBy, REFERENCE_TIME);
        room.setId(ROOM_ID);
        return room;
    }

    public static ChatRoom directRoom(String createdBy, String directKey) {
        ChatRoom room = ChatRoom.direct(directKey, createdBy, REFERENCE_TIME);
        room.setId(ROOM_ID);
        return room;
    }

    public static Participant participant(String userId, ParticipantRole role) {
        Participant participant = Participant.member(ROOM_ID, userId, role, REFERENCE_TIME);
        participant.setId("part-" + userId);
        return participant;
    }

    public static Participant mutedParticipant(String userId, ParticipantRole role, Instant mutedUntil) {
        Participant participant = participant(userId, role);
        participant.setMuted(true);
        participant.setMutedUntil(mutedUntil);
        return participant;
    }

    public static Message textMessage(String senderId) {
        Message message = new Message();
        message.setId(MESSAGE_ID);
        message.setRoomId(ROOM_ID);
        message.setSenderId(senderId);
        message.setSenderUsername("zara");
        message.setType(MessageType.TEXT);
        message.setContent("hello");
        message.setCreatedAt(REFERENCE_TIME);
        return message;
    }

    public static Invitation pendingInvitation(String invitedId, Instant expiresAt) {
        Invitation invitation = new Invitation();
        invitation.setId(INVITATION_ID);
        invitation.setRoomId(ROOM_ID);
        invitation.setInviterId("user-1");
        invitation.setInvitedId(invitedId);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(REFERENCE_TIME);
        invitation.setExpiresAt(expiresAt);
        return invitation;
    }
}
