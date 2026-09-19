package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.dto.request.UpdateReadCursorRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ReadCursorResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ReadCursorRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ReadCursorService {

    private final MessageRepository messageRepository;
    private final ReadCursorRepository readCursorRepository;
    private final RoomAccessService roomAccessService;

    public ReadCursorService(MessageRepository messageRepository, ReadCursorRepository readCursorRepository,
            RoomAccessService roomAccessService) {

        this.messageRepository = messageRepository;
        this.readCursorRepository = readCursorRepository;
        this.roomAccessService = roomAccessService;
    }

    public ReadCursorResponse markAsRead(String callerId, String roomId, UpdateReadCursorRequest request) {
        roomAccessService.requireParticipant(roomId, callerId);

        String lastReadMessageId = request.lastReadMessageId();
        messageRepository.findByIdAndRoomId(lastReadMessageId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND",
                        "The message referenced by the cursor is not part of this room"));

        ReadCursor cursor = new ReadCursor();
        cursor.setRoomId(roomId);
        cursor.setUserId(callerId);
        cursor.setLastReadMessageId(lastReadMessageId);
        cursor.setLastReadAt(Instant.now());

        return ReadCursorResponse.from(readCursorRepository.upsert(cursor));
    }
}
