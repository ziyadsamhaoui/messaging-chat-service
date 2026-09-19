package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.EditMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.SendMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.CursorPage;
import com.ziyadsamhaoui.messagingchatservice.dto.response.MessageResponse;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{roomId}/messages")
public class MessageController {

    private final MessageService messageService;
    private final CurrentUserProvider currentUserProvider;

    public MessageController(MessageService messageService, CurrentUserProvider currentUserProvider) {
        this.messageService = messageService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(@PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request) {

        return messageService.sendMessage(currentUserProvider.requireUserId(), roomId, request);
    }

    @GetMapping
    public CursorPage<MessageResponse> getHistory(@PathVariable String roomId,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {

        return messageService.getHistory(currentUserProvider.requireUserId(), roomId, cursor, limit);
    }

    @PatchMapping("/{messageId}")
    public MessageResponse editMessage(@PathVariable String roomId, @PathVariable String messageId,
            @Valid @RequestBody EditMessageRequest request) {

        return messageService.editMessage(currentUserProvider.requireUserId(), roomId, messageId, request);
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String roomId, @PathVariable String messageId) {
        messageService.deleteMessage(currentUserProvider.requireUserId(), roomId, messageId);
    }
}
