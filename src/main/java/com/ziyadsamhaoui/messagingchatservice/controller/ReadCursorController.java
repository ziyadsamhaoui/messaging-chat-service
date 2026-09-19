package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.UpdateReadCursorRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ReadCursorResponse;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.ReadCursorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{roomId}/read-cursor")
public class ReadCursorController {

    private final ReadCursorService readCursorService;
    private final CurrentUserProvider currentUserProvider;

    public ReadCursorController(ReadCursorService readCursorService, CurrentUserProvider currentUserProvider) {
        this.readCursorService = readCursorService;
        this.currentUserProvider = currentUserProvider;
    }

    @PutMapping
    public ReadCursorResponse markAsRead(@PathVariable String roomId,
            @Valid @RequestBody UpdateReadCursorRequest request) {

        return readCursorService.markAsRead(currentUserProvider.requireUserId(), roomId, request);
    }
}
