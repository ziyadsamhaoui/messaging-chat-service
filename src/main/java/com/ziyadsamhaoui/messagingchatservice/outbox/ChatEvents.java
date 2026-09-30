package com.ziyadsamhaoui.messagingchatservice.outbox;

import java.time.Instant;
import java.util.List;


public final class ChatEvents {

    private ChatEvents() {
    }

    public static final String TOPIC_MESSAGE = "badrlink.chat.message.v1";
    public static final String TOPIC_ROOM = "badrlink.chat.room.v1";
    public static final String TOPIC_INVITATION = "badrlink.chat.invitation.v1";

    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String MESSAGE_EDITED = "MESSAGE_EDITED";
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String REACTION_ADDED = "REACTION_ADDED";
    public static final String REACTION_REMOVED = "REACTION_REMOVED";
    public static final String ROOM_CREATED = "ROOM_CREATED";
    public static final String PARTICIPANT_ADDED = "PARTICIPANT_ADDED";
    public static final String PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED";
    public static final String INVITATION_SENT = "INVITATION_SENT";
    public static final String INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
    public static final String INVITATION_REJECTED = "INVITATION_REJECTED";

    public record MessageSent(String messageId, String roomId, String senderId, String senderUsername,
            String type, String content, Instant createdAt) {
    }

    public record MessageEdited(String messageId, String roomId, String newContent, Instant editedAt) {
    }

    public record MessageDeleted(String messageId, String roomId, Instant deletedAt) {
    }

    public record ReactionAdded(String messageId, String roomId, String userId, String emoji, Instant reactedAt) {
    }

    public record ReactionRemoved(String messageId, String roomId, String userId, Instant removedAt) {
    }

    public record RoomCreated(String roomId, String type, String createdBy, Instant createdAt) {
    }

    public record ParticipantAdded(String roomId, String userId, String role) {
    }

    public record ParticipantRemoved(String roomId, String userId) {
    }

    public record InvitationSent(String invitationId, String roomId, String invitedId, String inviterId,
            Instant sentAt) {
    }

    public record InvitationAccepted(String invitationId, String roomId, String invitedId, Instant acceptedAt) {
    }

    public record InvitationRejected(String invitationId, String roomId, String invitedId, Instant rejectedAt) {
    }
}
