package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class DependencyUnavailableException extends ChatServiceException {

    public DependencyUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", message);
    }
}
