package com.ziyadsamhaoui.messagingchatservice.model.enums;

/**
 * Viewer-relative direction of an invitation, mirroring the User service's
 * {@code ConnectionDirection}: an {@code INCOMING} invitation is addressed to the
 * viewer (only they may accept or reject it), an {@code OUTGOING} invitation was
 * sent by the viewer.
 */
public enum InvitationDirection {

    INCOMING,
    OUTGOING
}
