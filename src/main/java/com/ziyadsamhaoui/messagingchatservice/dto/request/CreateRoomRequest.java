package com.ziyadsamhaoui.messagingchatservice.dto.request;

import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoomRequest(@NotNull(message = "type is required") RoomType type,

        @Size(max = 100, message = "name must not exceed 100 characters") String name,

        Set<String> participantIds) {
}
