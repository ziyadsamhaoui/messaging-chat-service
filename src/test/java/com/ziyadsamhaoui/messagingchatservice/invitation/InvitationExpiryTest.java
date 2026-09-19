package com.ziyadsamhaoui.messagingchatservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ziyadsamhaoui.messagingchatservice.ChatIntegrationTest;
import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class InvitationExpiryTest extends ChatIntegrationTest {

    private static final String INVITED_USER = "user-2";

    private ChatRoom givenInvitableGroupRoom() {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithParticipants(room, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));
        givenUsername(INVITED_USER, "omar");
        when(invitationRepository.existsByRoomIdAndInvitedIdAndStatus(ChatTestFixtures.ROOM_ID, INVITED_USER,
                InvitationStatus.PENDING)).thenReturn(false);
        when(invitationRepository.insert(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation invitation = invocation.getArgument(0);
            invitation.setId(ChatTestFixtures.INVITATION_ID);
            return invitation;
        });
        return room;
    }

    @Test
    void rejectsAcceptanceOfAnExpiredInvitationEvenBeforeTheTtlSweep() throws Exception {
        Invitation expiredInvitation = ChatTestFixtures.pendingInvitation(INVITED_USER,
                Instant.now().minus(Duration.ofHours(1)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID))
                .thenReturn(Optional.of(expiredInvitation));

        mockMvc.perform(post("/invitations/{invitationId}/accept", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(INVITED_USER)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));

        verify(invitationRepository, never()).updateStatus(anyString(), any(), any());
        verify(participantRepository, never()).insert(any(Participant.class));
    }

    @Test
    void acceptsAPendingInvitationAndAddsTheInvitedUserAsGuest() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        Invitation invitation = ChatTestFixtures.pendingInvitation(INVITED_USER,
                Instant.now().plus(Duration.ofHours(2)));

        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(chatRoomRepository.findById(ChatTestFixtures.ROOM_ID)).thenReturn(Optional.of(room));
        when(invitationRepository.updateStatus(ChatTestFixtures.INVITATION_ID, InvitationStatus.PENDING,
                InvitationStatus.ACCEPTED)).thenReturn(true);
        when(participantRepository.existsByRoomIdAndUserId(ChatTestFixtures.ROOM_ID, INVITED_USER))
                .thenReturn(false);
        when(participantRepository.insert(any(Participant.class))).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            participant.setId("part-invited");
            return participant;
        });

        mockMvc.perform(post("/invitations/{invitationId}/accept", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(INVITED_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        ArgumentCaptor<Participant> capturedParticipant = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).insert(capturedParticipant.capture());

        assertThat(capturedParticipant.getValue().getUserId()).isEqualTo(INVITED_USER);
        assertThat(capturedParticipant.getValue().getRole()).isEqualTo(ParticipantRole.GUEST);
    }

    @Test
    void rejectsRejectionOfAnExpiredInvitation() throws Exception {
        Invitation expiredInvitation = ChatTestFixtures.pendingInvitation(INVITED_USER,
                Instant.now().minus(Duration.ofMinutes(5)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID))
                .thenReturn(Optional.of(expiredInvitation));

        mockMvc.perform(post("/invitations/{invitationId}/reject", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(INVITED_USER)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));

        verify(invitationRepository, never()).updateStatus(anyString(), any(), any());
    }

    @Test
    void rejectsResponsesFromUsersOtherThanTheInvitee() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(INVITED_USER,
                Instant.now().plus(Duration.ofHours(2)));
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));

        mockMvc.perform(post("/invitations/{invitationId}/accept", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs("user-3")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));

        verify(participantRepository, never()).insert(any(Participant.class));
    }

    @Test
    void rejectsAnInvitationThatWasAlreadyAnswered() throws Exception {
        Invitation invitation = ChatTestFixtures.pendingInvitation(INVITED_USER, Instant.now().plus(Duration.ofHours(2)));
        invitation.setStatus(InvitationStatus.REJECTED);
        when(invitationRepository.findById(ChatTestFixtures.INVITATION_ID)).thenReturn(Optional.of(invitation));

        mockMvc.perform(post("/invitations/{invitationId}/accept", ChatTestFixtures.INVITATION_ID)
                .with(authenticatedAs(INVITED_USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_HANDLED"));
    }

    @Test
    void createsAnInvitationWithAnIsoEightSixZeroOneTtl() throws Exception {
        ChatRoom room = givenInvitableGroupRoom();

        mockMvc.perform(post("/rooms/{roomId}/invitations", room.getId()).with(authenticatedAs("user-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invitedUserId":"user-2","ttl":"PT48H"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<Invitation> capturedInvitation = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).insert(capturedInvitation.capture());

        assertThat(Duration.between(capturedInvitation.getValue().getSentAt(),
                capturedInvitation.getValue().getExpiresAt())).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void capsTheRequestedTtlAtTheConfiguredMaximum() throws Exception {
        ChatRoom room = givenInvitableGroupRoom();

        mockMvc.perform(post("/rooms/{roomId}/invitations", room.getId()).with(authenticatedAs("user-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invitedUserId":"user-2","ttl":"PT1000H"}
                        """))
                .andExpect(status().isCreated());

        ArgumentCaptor<Invitation> capturedInvitation = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).insert(capturedInvitation.capture());

        assertThat(capturedInvitation.getValue().getExpiresAt()
                .isAfter(Instant.now().plus(Duration.ofDays(30)).minusSeconds(5))).isTrue();
    }

    @Test
    void rejectsInvitationsForDirectRooms() throws Exception {
        ChatRoom directRoom = ChatTestFixtures.directRoom("user-1", "direct-key");

        // Direct rooms hold no owner role, so the caller is promoted only to reach the room type guard.
        givenRoomWithParticipants(directRoom, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));

        mockMvc.perform(post("/rooms/{roomId}/invitations", directRoom.getId())
                .with(authenticatedAs("user-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invitedUserId":"user-2"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIRECT_ROOM_INVITATION_NOT_SUPPORTED"));
    }
}
