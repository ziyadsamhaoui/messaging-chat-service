package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import java.time.Instant;

public record RoomResponse(String id, RoomType type, String name, @JsonProperty("isFavorited") boolean favorited,
        String createdBy, Instant createdAt, String lastMessageId, ParticipantRole callerRole) {

    public static RoomResponse from(ChatRoom room, ParticipantRole callerRole) {
        return new RoomResponse(room.getId(), room.getType(), room.getName(), room.isFavorited(), room.getCreatedBy(),
                room.getCreatedAt(), room.getLastMessageId(), callerRole);
    }
}
