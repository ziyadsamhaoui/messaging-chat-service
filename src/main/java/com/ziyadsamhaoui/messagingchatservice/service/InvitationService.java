package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.dto.request.CreateInvitationRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.InvitationResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.ConflictException;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingchatservice.exception.InvitationExpiredException;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.exception.RoomAccessDeniedException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.InvitationRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.RoomAccessService.RoomContext;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final ParticipantRepository participantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomAccessService roomAccessService;
    private final UserServiceClient userServiceClient;
    private final ChatProperties chatProperties;

    public InvitationService(InvitationRepository invitationRepository, ParticipantRepository participantRepository,
            ChatRoomRepository chatRoomRepository, RoomAccessService roomAccessService,
            UserServiceClient userServiceClient, ChatProperties chatProperties) {

        this.invitationRepository = invitationRepository;
        this.participantRepository = participantRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.roomAccessService = roomAccessService;
        this.userServiceClient = userServiceClient;
        this.chatProperties = chatProperties;
    }

    public InvitationResponse invite(String callerId, String roomId, CreateInvitationRequest request) {
        RoomContext context = roomAccessService.requireOwnerOrAdmin(roomId, callerId);

        if (context.room().getType() != RoomType.GROUP) {
            throw new InvalidRequestException("DIRECT_ROOM_INVITATION_NOT_SUPPORTED",
                    "Invitations are only supported for group rooms");
        }

        String invitedId = request.invitedUserId();

        if (invitedId.equals(callerId)) {
            throw new InvalidRequestException("INVALID_INVITED_USER", "The caller cannot invite themselves");
        }

        if (participantRepository.existsByRoomIdAndUserId(roomId, invitedId)) {
            throw new ConflictException("ALREADY_PARTICIPANT", "The invited user is already a participant");
        }

        userServiceClient.getProfile(invitedId);

        Instant now = Instant.now();
        invitationRepository.deleteByRoomIdAndInvitedIdAndStatusAndExpiresAtLessThan(roomId, invitedId,
                InvitationStatus.PENDING, now);

        if (invitationRepository.existsByRoomIdAndInvitedIdAndStatus(roomId, invitedId, InvitationStatus.PENDING)) {
            throw new ConflictException("INVITATION_ALREADY_PENDING",
                    "A pending invitation already exists for this user");
        }

        Invitation invitation = new Invitation();
        invitation.setRoomId(roomId);
        invitation.setInviterId(callerId);
        invitation.setInvitedId(invitedId);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(now);
        invitation.setExpiresAt(now.plus(resolveTtl(request.ttl())));

        return InvitationResponse.from(invitationRepository.insert(invitation));
    }

    @Transactional
    public InvitationResponse accept(String callerId, String invitationId) {
        Invitation invitation = requirePendingInvitation(callerId, invitationId);
        ChatRoom room = requireActiveRoom(invitation.getRoomId());

        boolean statusUpdated = invitationRepository.updateStatus(invitationId, InvitationStatus.PENDING,
                InvitationStatus.ACCEPTED);

        if (!statusUpdated) {
            throw new ConflictException("INVITATION_ALREADY_HANDLED",
                    "The invitation has already been answered by another request");
        }

        if (!participantRepository.existsByRoomIdAndUserId(room.getId(), callerId)) {
            participantRepository
                    .insert(Participant.member(room.getId(), callerId, ParticipantRole.GUEST, Instant.now()));
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);

        return InvitationResponse.from(invitation);
    }

    @Transactional
    public InvitationResponse reject(String callerId, String invitationId) {
        Invitation invitation = requirePendingInvitation(callerId, invitationId);

        boolean statusUpdated = invitationRepository.updateStatus(invitationId, InvitationStatus.PENDING,
                InvitationStatus.REJECTED);

        if (!statusUpdated) {
            throw new ConflictException("INVITATION_ALREADY_HANDLED",
                    "The invitation has already been answered by another request");
        }

        invitation.setStatus(InvitationStatus.REJECTED);

        return InvitationResponse.from(invitation);
    }

    private Invitation requirePendingInvitation(String callerId, String invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));

        if (!invitation.getInvitedId().equals(callerId)) {
            throw new RoomAccessDeniedException("Only the invited user can answer this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_ALREADY_HANDLED", "The invitation is no longer pending");
        }

        if (invitation.isExpiredAt(Instant.now())) {
            throw new InvitationExpiredException("The invitation expired at " + invitation.getExpiresAt());
        }

        return invitation;
    }

    private ChatRoom requireActiveRoom(String roomId) {
        return chatRoomRepository.findById(roomId).filter(room -> !room.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND",
                        "The room of this invitation no longer exists"));
    }

    private Duration resolveTtl(Duration requestedTtl) {
        Duration ttl = requestedTtl == null ? chatProperties.defaultInvitationTtl() : requestedTtl;

        if (ttl.isNegative() || ttl.isZero()) {
            throw new InvalidRequestException("INVALID_INVITATION_TTL", "The invitation ttl must be positive");
        }

        return ttl.compareTo(chatProperties.maxInvitationTtl()) > 0 ? chatProperties.maxInvitationTtl() : ttl;
    }
}
