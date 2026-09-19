package com.ziyadsamhaoui.messagingchatservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ziyadsamhaoui.messagingchatservice.ChatIntegrationTest;
import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.exception.DependencyUnavailableException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class SenderImpersonationTest extends ChatIntegrationTest {

    @Test
    void ignoresSenderFieldsSuppliedByTheClient() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithParticipants(room, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));
        givenUsername("user-1", "zara");

        when(messageRepository.insert(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(ChatTestFixtures.MESSAGE_ID);
            return message;
        });

        mockMvc.perform(post("/rooms/{roomId}/messages", room.getId()).with(authenticatedAs("user-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"TEXT","content":"hello","senderId":"user-999","senderUsername":"impostor"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value("user-1"))
                .andExpect(jsonPath("$.senderUsername").value("zara"));

        ArgumentCaptor<Message> capturedMessage = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).insert(capturedMessage.capture());

        assertThat(capturedMessage.getValue().getSenderId()).isEqualTo("user-1");
        assertThat(capturedMessage.getValue().getSenderUsername()).isEqualTo("zara");
    }

    @Test
    void fallsBackToTokenClaimsWhenTheUserServiceIsUnavailable() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithParticipants(room, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));

        when(userServiceClient.getProfile("user-1")).thenThrow(new DependencyUnavailableException("down"));
        when(messageRepository.insert(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(ChatTestFixtures.MESSAGE_ID);
            return message;
        });

        mockMvc.perform(post("/rooms/{roomId}/messages", room.getId())
                .with(jwt().jwt(jwt -> jwt.subject("user-1").claim("preferred_username", "zara-from-token")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"TEXT","content":"hello"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value("user-1"))
                .andExpect(jsonPath("$.senderUsername").value("zara-from-token"));
    }
}
