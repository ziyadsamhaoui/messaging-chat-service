package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class UnauthenticatedRequestException extends ChatServiceException {

    public UnauthenticatedRequestException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }
}
