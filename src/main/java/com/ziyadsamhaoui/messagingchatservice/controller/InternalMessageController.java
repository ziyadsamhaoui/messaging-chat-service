package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.UpdateAttachmentsRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.MessageResponse;
import com.ziyadsamhaoui.messagingchatservice.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/messages")
public class InternalMessageController {

    private final MessageService messageService;

    public InternalMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PatchMapping("/{messageId}/attachments")
    public MessageResponse updateAttachments(@PathVariable String messageId,
            @Valid @RequestBody UpdateAttachmentsRequest request) {

        return messageService.appendAttachments(messageId, request.attachmentIds());
    }
}
