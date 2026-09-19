package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import java.time.Instant;
import java.util.List;

public record MessageResponse(String id, String roomId, String senderId, String senderUsername, MessageType type,
        String content, List<String> attachmentIds, @JsonProperty("isDeleted") boolean deleted, Instant deletedAt,
        @JsonProperty("isEdited") boolean edited, Instant editedAt, Instant createdAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getRoomId(), message.getSenderId(),
                message.getSenderUsername(), message.getType(), message.getContent(),
                message.getAttachmentIds() == null ? List.of() : List.copyOf(message.getAttachmentIds()),
                message.isDeleted(), message.getDeletedAt(), message.isEdited(), message.getEditedAt(),
                message.getCreatedAt());
    }
}
