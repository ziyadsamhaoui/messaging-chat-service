package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.service.support.DirectKeyFactory;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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
