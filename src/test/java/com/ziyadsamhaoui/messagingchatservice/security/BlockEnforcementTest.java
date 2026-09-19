package com.ziyadsamhaoui.messagingchatservice.security;

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
import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.DependencyUnavailableException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BlockEnforcementTest extends ChatIntegrationTest {

    private static final String CALLER = "user-1";
    private static final String COUNTERPART = "user-2";

    @Test
    void rejectsDirectRoomCreationWhenABlockExists() throws Exception {
        when(chatRoomRepository.findActiveByDirectKey(anyString())).thenReturn(Optional.empty());
        when(userServiceClient.getProfile(COUNTERPART))
                .thenReturn(new UserProfileResponse(COUNTERPART, "omar"));
        when(userServiceClient.isBlockedBetween(CALLER, COUNTERPART)).thenReturn(true);

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"DIRECT","participantIds":["user-2"]}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_RELATIONSHIP"));

        verify(chatRoomRepository, never()).insert(any(ChatRoom.class));
        verify(participantRepository, never()).insert(any(Participant.class));
    }

    @Test
    void rejectsDirectMessageWhenABlockExists() throws Exception {
        ChatRoom room = ChatTestFixtures.directRoom(CALLER, "direct-key");
        Participant callerParticipant = ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST);
        Participant counterpartParticipant = ChatTestFixtures.participant(COUNTERPART, ParticipantRole.GUEST);

        givenRoomWithParticipants(room, callerParticipant);
        when(participantRepository.findByRoomId(room.getId()))
                .thenReturn(List.of(callerParticipant, counterpartParticipant));
        when(userServiceClient.isBlockedBetween(CALLER, COUNTERPART)).thenReturn(true);

        mockMvc.perform(post("/rooms/{roomId}/messages", room.getId()).with(authenticatedAs(CALLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"TEXT","content":"should not be stored"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_RELATIONSHIP"));

        verify(messageRepository, never()).insert(any(Message.class));
    }

    @Test
    void failsClosedWhenBlockVerificationIsUnavailable() throws Exception {
        when(chatRoomRepository.findActiveByDirectKey(anyString())).thenReturn(Optional.empty());
        when(userServiceClient.getProfile(COUNTERPART))
                .thenReturn(new UserProfileResponse(COUNTERPART, "omar"));
        when(userServiceClient.isBlockedBetween(CALLER, COUNTERPART))
                .thenThrow(new DependencyUnavailableException("user service down"));

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"DIRECT","participantIds":["user-2"]}
                        """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        verify(chatRoomRepository, never()).insert(any(ChatRoom.class));
    }

    @Test
    void allowsGroupRoomCreationWithoutBlockVerification() throws Exception {
        when(chatRoomRepository.insert(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(ChatTestFixtures.ROOM_ID);
            return room;
        });

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"GROUP","name":"release-crew","participantIds":["user-2"]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.callerRole").value("OWNER"));

        verify(userServiceClient, never()).isBlockedBetween(anyString(), anyString());
    }
}
