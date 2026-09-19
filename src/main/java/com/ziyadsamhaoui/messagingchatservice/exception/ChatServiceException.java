package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class ChatServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ChatServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
