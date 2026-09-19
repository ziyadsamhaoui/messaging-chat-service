package com.ziyadsamhaoui.messagingchatservice.exception;

import org.springframework.http.HttpStatus;

public class InvitationExpiredException extends ChatServiceException {

    public InvitationExpiredException(String message) {
        super(HttpStatus.GONE, "INVITATION_EXPIRED", message);
    }
}
