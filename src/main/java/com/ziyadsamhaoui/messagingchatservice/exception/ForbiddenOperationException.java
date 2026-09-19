package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ChatServiceException {

    public ForbiddenOperationException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
