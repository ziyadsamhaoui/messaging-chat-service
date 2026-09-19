package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class RoomAccessDeniedException extends ChatServiceException {

    public RoomAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", message);
    }
}
