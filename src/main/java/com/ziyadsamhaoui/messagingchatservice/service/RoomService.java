package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.dto.request.CreateRoomRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.UpdateParticipantRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.CursorPage;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ParticipantResponse;
import com.ziyadsamhaoui.messagingchatservice.dto.response.RoomResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.ConflictException;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.RoomAccessService.RoomContext;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec.Cursor;
import com.ziyadsamhaoui.messagingchatservice.service.support.DirectKeyFactory;
import com.ziyadsamhaoui.messagingchatservice.service.support.PageSizeResolver;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ParticipantRepository participantRepository;
    private final UserServiceClient userServiceClient;
    private final RoomWriter roomWriter;
    private final RoomAccessService roomAccessService;
    private final BlockPolicy blockPolicy;
    private final DirectKeyFactory directKeyFactory;
    private final CursorCodec cursorCodec;
    private final PageSizeResolver pageSizeResolver;
    private final ChatProperties chatProperties;

    public RoomService(ChatRoomRepository chatRoomRepository, ParticipantRepository participantRepository,
            UserServiceClient userServiceClient, RoomWriter roomWriter, RoomAccessService roomAccessService,
            BlockPolicy blockPolicy, DirectKeyFactory directKeyFactory, CursorCodec cursorCodec,
            PageSizeResolver pageSizeResolver, ChatProperties chatProperties) {

        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.userServiceClient = userServiceClient;
        this.roomWriter = roomWriter;
        this.roomAccessService = roomAccessService;
        this.blockPolicy = blockPolicy;
        this.directKeyFactory = directKeyFactory;
        this.cursorCodec = cursorCodec;
        this.pageSizeResolver = pageSizeResolver;
        this.chatProperties = chatProperties;
    }

    public CreationResult createRoom(String callerId, CreateRoomRequest request) {
        if (request.type() == RoomType.DIRECT) {
            return createDirectRoom(callerId, request);
        }

        return new CreationResult(createGroupRoom(callerId, request), ParticipantRole.OWNER, true);
    }

    public CursorPage<RoomResponse> listRooms(String callerId, String cursor, Integer limit) {
        int pageSize = pageSizeResolver.resolve(limit);
        Optional<Cursor> decodedCursor = cursorCodec.decode(cursor);

        Map<String, ParticipantRole> rolesByRoomId = new LinkedHashMap<>();
        participantRepository.findByUserId(callerId)
                .forEach(membership -> rolesByRoomId.putIfAbsent(membership.getRoomId(), membership.getRole()));

        if (rolesByRoomId.isEmpty()) {
            return CursorPage.empty();
        }

        List<ChatRoom> rooms = chatRoomRepository.findActivePageByIds(rolesByRoomId.keySet(),
                decodedCursor.map(Cursor::createdAt).orElse(null), decodedCursor.map(Cursor::id).orElse(null),
                pageSize + 1);

        boolean hasMore = rooms.size() > pageSize;
        List<ChatRoom> page = hasMore ? rooms.subList(0, pageSize) : rooms;

        List<RoomResponse> items = page.stream()
                .map(room -> RoomResponse.from(room, rolesByRoomId.get(room.getId())))
                .toList();

        return CursorPage.of(items, nextRoomCursor(page, hasMore), hasMore);
    }

    public RoomResponse getRoom(String callerId, String roomId) {
        RoomContext context = roomAccessService.requireParticipant(roomId, callerId);
        return RoomResponse.from(context.room(), context.participant().getRole());
    }

    public CursorPage<ParticipantResponse> listParticipants(String callerId, String roomId, String cursor,
            Integer limit) {

        int pageSize = pageSizeResolver.resolve(limit);
        roomAccessService.requireParticipant(roomId, callerId);

        Optional<String> cursorId = cursorCodec.decodeId(cursor);
        List<Participant> participants = participantRepository.findPageByRoom(roomId, cursorId.orElse(null),
                pageSize + 1);

        boolean hasMore = participants.size() > pageSize;
        List<Participant> page = hasMore ? participants.subList(0, pageSize) : participants;

        List<ParticipantResponse> items = page.stream().map(ParticipantResponse::from).toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? cursorCodec.encodeId(page.get(page.size() - 1).getId())
                : null;

        return CursorPage.of(items, nextCursor, hasMore);
    }

    public ParticipantResponse updateParticipant(String callerId, String roomId, String targetUserId,
            UpdateParticipantRequest request) {

        RoomContext context = roomAccessService.requireOwnerOrAdmin(roomId, callerId);
        Participant target = participantRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PARTICIPANT_NOT_FOUND",
                        "The user is not a participant of this room"));

        if (!request.hasRoleChange() && !request.hasMuteChange()) {
            throw new InvalidRequestException("EMPTY_PARTICIPANT_UPDATE", "Provide a role or a mute change");
        }

        if (target.isOwner()) {
            throw new ForbiddenOperationException("OWNER_IMMUTABLE", "The room owner cannot be modified");
        }

        boolean callerIsOwner = context.participant().isOwner();
        boolean removingSelf = callerId.equals(targetUserId);

        if (removingSelf) {
            if (request.hasRoleChange()) {
                throw new ForbiddenOperationException("SELF_ROLE_CHANGE_DENIED",
                        "A participant cannot change their own role");
            }
        } else if (!callerIsOwner && target.getRole() == ParticipantRole.ADMIN) {
            throw new ForbiddenOperationException("ADMIN_MODIFICATION_DENIED",
                    "Only the room owner can modify another admin");
        }

        if (request.hasRoleChange()) {
            if (request.role() == ParticipantRole.OWNER) {
                throw new InvalidRequestException("ROLE_NOT_ASSIGNABLE",
                        "Ownership cannot be transferred through this endpoint");
            }
            if (request.role() == ParticipantRole.ADMIN && !callerIsOwner) {
                throw new ForbiddenOperationException("ADMIN_GRANT_DENIED",
                        "Only the room owner can grant the admin role");
            }

            participantRepository.updateRole(roomId, targetUserId, request.role());
            target.setRole(request.role());
        }

        if (request.hasMuteChange()) {
            boolean muted = request.muted() == null || request.muted();
            Instant mutedUntil = muted ? request.mutedUntil() : null;

            if (mutedUntil != null && !mutedUntil.isAfter(Instant.now())) {
                throw new InvalidRequestException("INVALID_MUTE_EXPIRY", "mutedUntil must be in the future");
            }

            participantRepository.updateMute(roomId, targetUserId, muted, mutedUntil);
            target.setMuted(muted);
            target.setMutedUntil(mutedUntil);
        }

        return ParticipantResponse.from(target);
    }

    @Transactional
    public void removeParticipant(String callerId, String roomId, String targetUserId) {
        RoomContext context = roomAccessService.requireParticipant(roomId, callerId);
        boolean removingSelf = callerId.equals(targetUserId);

        Participant target = removingSelf
                ? context.participant()
                : participantRepository.findByRoomIdAndUserId(roomId, targetUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("PARTICIPANT_NOT_FOUND",
                                "The user is not a participant of this room"));

        if (!removingSelf && !context.participant().isOwner()) {
            throw new ForbiddenOperationException("OWNER_ROLE_REQUIRED",
                    "Only the room owner can remove another participant");
        }

        participantRepository.deleteByRoomIdAndUserId(roomId, targetUserId);

        List<Participant> remaining = participantRepository.findByRoomId(roomId).stream()
                .filter(participant -> !participant.getUserId().equals(targetUserId))
                .toList();

        if (remaining.isEmpty() || context.room().getType() == RoomType.DIRECT) {
            chatRoomRepository.softDelete(roomId, context.room().getType() == RoomType.DIRECT);
            return;
        }

        if (target.isOwner() && remaining.stream().noneMatch(Participant::isOwner)) {
            Participant successor = remaining.stream().filter(participant -> participant.getJoinedAt() != null)
                    .min(Comparator.comparing(Participant::getJoinedAt))
                    .orElse(remaining.getFirst());

            participantRepository.updateRole(roomId, successor.getUserId(), ParticipantRole.OWNER);
        }
    }

    private CreationResult createDirectRoom(String callerId, CreateRoomRequest request) {
        Set<String> participantIds = normalizeParticipantIds(request.participantIds());

        if (participantIds.size() != 1) {
            throw new InvalidRequestException("INVALID_DIRECT_PARTICIPANTS",
                    "A direct room requires exactly one other participant");
        }

        String counterpartId = participantIds.iterator().next();

        if (counterpartId.equals(callerId)) {
            throw new InvalidRequestException("INVALID_DIRECT_PARTICIPANTS",
                    "A direct room cannot be created with yourself");
        }

        String directKey = directKeyFactory.create(callerId, counterpartId);

        Optional<ChatRoom> existingRoom = chatRoomRepository.findActiveByDirectKey(directKey);
        if (existingRoom.isPresent()) {
            return new CreationResult(existingRoom.get(), ParticipantRole.GUEST, false);
        }

        userServiceClient.getProfile(counterpartId);
        blockPolicy.assertNotBlocked(callerId, counterpartId);

        try {
            ChatRoom created = roomWriter.insertDirectRoom(callerId, counterpartId, directKey, Instant.now());
            return new CreationResult(created, ParticipantRole.GUEST, true);
        } catch (DuplicateKeyException exception) {
            return chatRoomRepository.findActiveByDirectKey(directKey)
                    .map(room -> new CreationResult(room, ParticipantRole.GUEST, false))
                    .orElseThrow(() -> new ConflictException("DIRECT_ROOM_CONFLICT",
                            "The direct room could not be resolved"));
        }
    }

    private ChatRoom createGroupRoom(String callerId, CreateRoomRequest request) {
        String name = request.name() == null ? null : request.name().trim();

        if (!StringUtils.hasText(name)) {
            throw new InvalidRequestException("GROUP_NAME_REQUIRED", "A group room requires a name");
        }

        Set<String> memberIds = normalizeParticipantIds(request.participantIds());
        memberIds.remove(callerId);

        if (memberIds.size() > chatProperties.maxGroupSize() - 1) {
            throw new InvalidRequestException("GROUP_TOO_LARGE",
                    "A group room accepts at most " + chatProperties.maxGroupSize() + " participants");
        }

        return roomWriter.insertGroupRoom(callerId, name, memberIds, Instant.now());
    }

    private Set<String> normalizeParticipantIds(Collection<String> participantIds) {
        if (participantIds == null) {
            return new LinkedHashSet<>();
        }

        return participantIds.stream().filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String nextRoomCursor(List<ChatRoom> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }

        ChatRoom lastRoom = page.get(page.size() - 1);
        return cursorCodec.encode(lastRoom.getCreatedAt(), lastRoom.getId());
    }

    public record CreationResult(ChatRoom room, ParticipantRole callerRole, boolean newlyCreated) {
    }
}
