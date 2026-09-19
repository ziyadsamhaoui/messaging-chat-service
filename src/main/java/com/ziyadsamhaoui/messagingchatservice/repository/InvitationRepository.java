package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvitationRepository extends MongoRepository<Invitation, String>, InvitationRepositoryCustom {

    List<Invitation> findByInvitedIdAndStatus(String invitedId, InvitationStatus status);

    List<Invitation> findByRoomIdAndStatus(String roomId, InvitationStatus status);

    boolean existsByRoomIdAndInvitedIdAndStatus(String roomId, String invitedId, InvitationStatus status);

    long deleteByRoomIdAndInvitedIdAndStatusAndExpiresAtLessThan(String roomId, String invitedId,
            InvitationStatus status, Instant expiresAt);
}
