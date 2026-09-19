package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.CreateInvitationRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.InvitationResponse;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final CurrentUserProvider currentUserProvider;

    public InvitationController(InvitationService invitationService, CurrentUserProvider currentUserProvider) {
        this.invitationService = invitationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/rooms/{roomId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@PathVariable String roomId,
            @Valid @RequestBody CreateInvitationRequest request) {

        return invitationService.invite(currentUserProvider.requireUserId(), roomId, request);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public InvitationResponse accept(@PathVariable String invitationId) {
        return invitationService.accept(currentUserProvider.requireUserId(), invitationId);
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public InvitationResponse reject(@PathVariable String invitationId) {
        return invitationService.reject(currentUserProvider.requireUserId(), invitationId);
    }
}
