package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.ReactionRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ReactionResponse;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{roomId}/messages/{messageId}/reactions")
public class ReactionController {

    private final ReactionService reactionService;
    private final CurrentUserProvider currentUserProvider;

    public ReactionController(ReactionService reactionService, CurrentUserProvider currentUserProvider) {
        this.reactionService = reactionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ReactionResponse addReaction(@PathVariable String roomId, @PathVariable String messageId,
            @Valid @RequestBody ReactionRequest request) {

        return reactionService.addReaction(currentUserProvider.requireUserId(), roomId, messageId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReaction(@PathVariable String roomId, @PathVariable String messageId) {
        reactionService.removeReaction(currentUserProvider.requireUserId(), roomId, messageId);
    }
}
