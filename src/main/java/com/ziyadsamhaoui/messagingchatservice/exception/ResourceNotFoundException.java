package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ChatServiceException {

    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
