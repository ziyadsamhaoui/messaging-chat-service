package com.ziyadsamhaoui.messagingchatservice.exception;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String userId) {
        super("USER_NOT_FOUND", "User " + userId + " does not exist");
    }
}
