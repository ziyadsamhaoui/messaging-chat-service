package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;
import java.time.Instant;

public record ReadCursorResponse(String id, String roomId, String userId, String lastReadMessageId,
        Instant lastReadAt) {

    public static ReadCursorResponse from(ReadCursor cursor) {
        return new ReadCursorResponse(cursor.getId(), cursor.getRoomId(), cursor.getUserId(),
                cursor.getLastReadMessageId(), cursor.getLastReadAt());
    }
}
