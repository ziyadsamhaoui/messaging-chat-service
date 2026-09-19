package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.exception.RoomAccessDeniedException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class RoomAccessService {

    private static final String ROOM_NOT_FOUND_MESSAGE = "Room not found";

    private final ChatRoomRepository chatRoomRepository;
    private final ParticipantRepository participantRepository;

    public RoomAccessService(ChatRoomRepository chatRoomRepository, ParticipantRepository participantRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
    }

    public RoomContext requireParticipant(String roomId, String userId) {
        ChatRoom room = requireActiveRoom(roomId);
        Participant participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RoomAccessDeniedException("The caller is not a participant of this room"));

        return new RoomContext(room, participant);
    }

    public RoomContext requireOwnerOrAdmin(String roomId, String userId) {
        RoomContext context = requireParticipant(roomId, userId);

        if (!context.participant().isOwnerOrAdmin()) {
            throw new RoomAccessDeniedException("The caller requires the owner or admin role for this room");
        }

        return context;
    }

    public RoomContext requireOwner(String roomId, String userId) {
        RoomContext context = requireParticipant(roomId, userId);

        if (!context.participant().isOwner()) {
            throw new RoomAccessDeniedException("The caller requires the owner role for this room");
        }

        return context;
    }

    public void requireNotMuted(RoomContext context) {
        if (context.participant().isMuteActive(Instant.now())) {
            throw new ForbiddenOperationException("PARTICIPANT_MUTED", "The caller is muted in this room");
        }
    }

    private ChatRoom requireActiveRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .filter(room -> !room.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", ROOM_NOT_FOUND_MESSAGE));
    }

    public record RoomContext(ChatRoom room, Participant participant) {
    }
}
