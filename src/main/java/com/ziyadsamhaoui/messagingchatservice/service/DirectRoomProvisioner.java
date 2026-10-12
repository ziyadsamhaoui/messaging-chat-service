package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.service.support.DirectKeyFactory;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Ensures two connected users share a single active DIRECT room. Used by the
 * {@code USER_CONNECTION_ACCEPTED} consumer so a connection and its conversation
 * are created without synchronous service-to-service coupling — the User service
 * owns connections, Chat owns rooms.
 *
 * <p>Idempotent by construction: the {@code ux_chatrooms_direct_key} unique index
 * lets repeated deliveries, concurrent creations, and the REST create-room path
 * all converge on exactly one room. The {@code RoomWriter} call is the only
 * transactional boundary, so a losing race that trips the unique index simply
 * rolls that insert back and is swallowed here.
 */
@Service
public class DirectRoomProvisioner {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomWriter roomWriter;
    private final DirectKeyFactory directKeyFactory;

    public DirectRoomProvisioner(ChatRoomRepository chatRoomRepository, RoomWriter roomWriter,
            DirectKeyFactory directKeyFactory) {
        this.chatRoomRepository = chatRoomRepository;
        this.roomWriter = roomWriter;
        this.directKeyFactory = directKeyFactory;
    }

    /**
     * @return {@code true} when this call created the room, {@code false} when an
     *         active room already existed (or a concurrent creation won the race).
     */
    public boolean ensureDirectRoom(String firstUserId, String secondUserId) {
        if (firstUserId == null || secondUserId == null || firstUserId.equals(secondUserId)) {
            return false;
        }
        String directKey = directKeyFactory.create(firstUserId, secondUserId);
        if (chatRoomRepository.findActiveByDirectKey(directKey).isPresent()) {
            return false;
        }
        try {
            roomWriter.insertDirectRoom(firstUserId, secondUserId, directKey, Instant.now());
            return true;
        } catch (DuplicateKeyException alreadyCreated) {
            return false;
        }
    }
}
