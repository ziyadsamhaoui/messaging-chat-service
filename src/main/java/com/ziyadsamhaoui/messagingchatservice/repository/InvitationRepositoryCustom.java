package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;

public interface InvitationRepositoryCustom {

    boolean updateStatus(String invitationId, InvitationStatus expectedStatus, InvitationStatus nextStatus);
}
