package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.outbox.ChatEvents;
import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxWriter;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomWriter {

    private final ChatRoomRepository chatRoomRepository;
    private final ParticipantRepository participantRepository;
    private final OutboxWriter outboxWriter;

    public RoomWriter(ChatRoomRepository chatRoomRepository, ParticipantRepository participantRepository,
            OutboxWriter outboxWriter) {
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public ChatRoom insertDirectRoom(String creatorId, String counterpartId, String directKey, Instant createdAt) {
        ChatRoom saved = chatRoomRepository.insert(ChatRoom.direct(directKey, creatorId, createdAt));

        participantRepository.insert(List.of(
                Participant.member(saved.getId(), creatorId, ParticipantRole.GUEST, createdAt),
                Participant.member(saved.getId(), counterpartId, ParticipantRole.GUEST, createdAt)));

        outboxWriter.append("ChatRoom", saved.getId(), ChatEvents.ROOM_CREATED,
                new ChatEvents.RoomCreated(saved.getId(), "DIRECT", creatorId, createdAt));
        appendParticipantAdded(saved.getId(), creatorId, ParticipantRole.GUEST);
        appendParticipantAdded(saved.getId(), counterpartId, ParticipantRole.GUEST);

        return saved;
    }

    @Transactional
    public ChatRoom insertGroupRoom(String creatorId, String name, Set<String> memberIds, Instant createdAt) {
        ChatRoom saved = chatRoomRepository.insert(ChatRoom.group(name, creatorId, createdAt));

        List<Participant> participants = new ArrayList<>();
        participants.add(Participant.member(saved.getId(), creatorId, ParticipantRole.OWNER, createdAt));
        memberIds.forEach(userId -> participants
                .add(Participant.member(saved.getId(), userId, ParticipantRole.GUEST, createdAt)));

        participantRepository.insert(participants);

        outboxWriter.append("ChatRoom", saved.getId(), ChatEvents.ROOM_CREATED,
                new ChatEvents.RoomCreated(saved.getId(), "GROUP", creatorId, createdAt));
        appendParticipantAdded(saved.getId(), creatorId, ParticipantRole.OWNER);
        memberIds.forEach(userId -> appendParticipantAdded(saved.getId(), userId, ParticipantRole.GUEST));

        return saved;
    }

    private void appendParticipantAdded(String roomId, String userId, ParticipantRole role) {
        outboxWriter.append("Participant", userId, ChatEvents.PARTICIPANT_ADDED,
                new ChatEvents.ParticipantAdded(roomId, userId, role.name()));
    }
}
