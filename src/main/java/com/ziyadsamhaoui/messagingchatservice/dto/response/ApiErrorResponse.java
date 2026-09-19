package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(Instant timestamp, int status, String code, String message, String path,
        Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ApiErrorResponse of(int status, String code, String message, String path,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }
}
