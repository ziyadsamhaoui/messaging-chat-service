package com.ziyadsamhaoui.messagingchatservice.invitation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ziyadsamhaoui.messagingchatservice.ChatIntegrationTest;
import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * INC-22 follow-up: the invitation inbox is server-truth. The sender sees their
 * outgoing invitation and the recipient sees the same invitation as incoming, both
 * sourced from one query that only returns {@code PENDING} rows so a completed
 * invitation disappears for both users without any client-side bookkeeping.
 */
class PendingInvitationsTest extends ChatIntegrationTest {

    private static final String SENDER = "user-1";
    private static final String RECIPIENT = "user-2";

    @Test
    void senderSeesTheInvitationAsOutgoing() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(RECIPIENT,
                Instant.now().plus(Duration.ofHours(48)));
        givenPendingInbox(SENDER, List.of(), List.of(invitation));

        mockMvc.perform(get("/invitations/pending").with(authenticatedAs(SENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ChatTestFixtures.INVITATION_ID))
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$[0].inviterId").value(SENDER))
                .andExpect(jsonPath("$[0].invitedId").value(RECIPIENT))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].roomId").value(ChatTestFixtures.ROOM_ID))
                .andExpect(jsonPath("$[0].roomName").value("badrlink-core"));
    }

    @Test
    void recipientSeesTheSameInvitationAsIncoming() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(RECIPIENT,
                Instant.now().plus(Duration.ofHours(48)));
        givenPendingInbox(RECIPIENT, List.of(invitation), List.of());

        mockMvc.perform(get("/invitations/pending").with(authenticatedAs(RECIPIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ChatTestFixtures.INVITATION_ID))
                .andExpect(jsonPath("$[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$[0].inviterId").value(SENDER))
                .andExpect(jsonPath("$[0].invitedId").value(RECIPIENT));
    }

    @Test
    void onlyPendingInvitationsAreQueriedInEitherDirection() throws Exception {
        givenPendingInbox(SENDER, List.of(), List.of());

        mockMvc.perform(get("/invitations/pending").with(authenticatedAs(SENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Completed (ACCEPTED/REJECTED) invitations can never reach the inbox because
        // both lookups are filtered on PENDING.
        verify(invitationRepository).findByInvitedIdAndStatus(SENDER, InvitationStatus.PENDING);
        verify(invitationRepository).findByInviterIdAndStatus(SENDER, InvitationStatus.PENDING);
    }

    @Test
    void incomingAndOutgoingAreMergedNewestFirst() throws Exception {
        Invitation olderIncoming = invitation("inv-older", SENDER, RECIPIENT,
                Instant.parse("2026-01-01T09:00:00Z"));
        Invitation newerOutgoing = invitation("inv-newer", RECIPIENT, "user-3",
                Instant.parse("2026-01-01T11:00:00Z"));
        givenPendingInbox(RECIPIENT, List.of(olderIncoming), List.of(newerOutgoing));

        mockMvc.perform(get("/invitations/pending").with(authenticatedAs(RECIPIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("inv-newer"))
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$[1].id").value("inv-older"))
                .andExpect(jsonPath("$[1].direction").value("INCOMING"));
    }

    @Test
    void rejectingAnInvitationMarksItRejectedWithoutAddingMembership() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(RECIPIENT,
                Instant.now().plus(Duration.ofHours(2)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(invitationRepository.updateStatus(ChatTestFixtures.INVITATION_ID, InvitationStatus.PENDING,
                InvitationStatus.REJECTED)).thenReturn(true);

        mockMvc.perform(post("/invitations/{invitationId}/reject", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(RECIPIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(participantRepository, never()).insert(any(Participant.class));
    }

    @Test
    void rejectsRejectionFromUsersOtherThanTheInvitee() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(RECIPIENT,
                Instant.now().plus(Duration.ofHours(2)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));

        mockMvc.perform(post("/invitations/{invitationId}/reject", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs("user-3")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));

        verify(invitationRepository, never()).updateStatus(anyString(), any(), any());
    }

    @Test
    void concurrentAcceptLosesTheRaceWithoutAddingMembershipTwice() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom(SENDER);
        Invitation invitation = ChatTestFixtures.pendingInvitation(RECIPIENT,
                Instant.now().plus(Duration.ofHours(2)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(chatRoomRepository.findById(ChatTestFixtures.ROOM_ID)).thenReturn(Optional.of(room));
        // Another request already flipped PENDING -> ACCEPTED, so this one must not insert a participant.
        when(invitationRepository.updateStatus(ChatTestFixtures.INVITATION_ID, InvitationStatus.PENDING,
                InvitationStatus.ACCEPTED)).thenReturn(false);

        mockMvc.perform(post("/invitations/{invitationId}/accept", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(RECIPIENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_HANDLED"));

        verify(participantRepository, never()).insert(any(Participant.class));
    }

    private void givenPendingInbox(String viewer, List<Invitation> incoming, List<Invitation> outgoing) {
        ChatRoom room = ChatTestFixtures.groupRoom(SENDER);
        when(chatRoomRepository.findById(ChatTestFixtures.ROOM_ID)).thenReturn(Optional.of(room));
        when(invitationRepository.findByInvitedIdAndStatus(viewer, InvitationStatus.PENDING)).thenReturn(incoming);
        when(invitationRepository.findByInviterIdAndStatus(viewer, InvitationStatus.PENDING)).thenReturn(outgoing);
    }

    private static Invitation invitation(String id, String inviterId, String invitedId, Instant sentAt) {
        Invitation invitation = new Invitation();
        invitation.setId(id);
        invitation.setRoomId(ChatTestFixtures.ROOM_ID);
        invitation.setInviterId(inviterId);
        invitation.setInvitedId(invitedId);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(sentAt);
        invitation.setExpiresAt(sentAt.plus(Duration.ofHours(48)));
        return invitation;
    }
}
