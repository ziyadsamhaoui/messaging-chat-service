package com.ziyadsamhaoui.messagingchatservice.security;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ziyadsamhaoui.messagingchatservice.ChatIntegrationTest;
import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RoomAccessAuditTest extends ChatIntegrationTest {

    private static final String OUTSIDER = "user-outsider";

    @Test
    void returnsForbiddenWhenReadingARoomWithoutMembership() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithoutMembership(room);

        mockMvc.perform(get("/rooms/{roomId}", room.getId()).with(authenticatedAs(OUTSIDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));
    }

    @Test
    void returnsForbiddenWhenReadingMessageHistoryWithoutMembership() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithoutMembership(room);

        mockMvc.perform(get("/rooms/{roomId}/messages", room.getId()).with(authenticatedAs(OUTSIDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));
    }

    @Test
    void returnsForbiddenWhenSendingAMessageWithoutMembership() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithoutMembership(room);

        mockMvc.perform(post("/rooms/{roomId}/messages", room.getId()).with(authenticatedAs(OUTSIDER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"TEXT","content":"should not be stored"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));

        verify(messageRepository, never()).insert(any(Message.class));
    }

    @Test
    void returnsForbiddenWhenReadingParticipantsWithoutMembership() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithoutMembership(room);

        mockMvc.perform(get("/rooms/{roomId}/participants", room.getId()).with(authenticatedAs(OUTSIDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));
    }

    @Test
    void returnsNotFoundWhenTheRoomDoesNotExist() throws Exception {
        when(chatRoomRepository.findById("64b7f0c2f1a2b3c4d5e6f0000")).thenReturn(Optional.empty());

        mockMvc.perform(get("/rooms/{roomId}", "64b7f0c2f1a2b3c4d5e6f0000").with(authenticatedAs("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void returnsUnauthorizedWhenNoBearerTokenIsPresent() throws Exception {
        mockMvc.perform(get("/rooms/{roomId}", ChatTestFixtures.ROOM_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void allowsAParticipantToReadTheRoom() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithParticipants(room, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));

        mockMvc.perform(get("/rooms/{roomId}", room.getId()).with(authenticatedAs("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId()))
                .andExpect(jsonPath("$.callerRole").value("OWNER"))
                .andExpect(jsonPath("$.isFavorited").value(false));
    }

    @Test
    void listsParticipantsForMembers() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        Participant owner = ChatTestFixtures.participant("user-1", ParticipantRole.OWNER);
        givenRoomWithParticipants(room, owner);

        when(participantRepository.findPageByRoom(room.getId(), null, 31)).thenReturn(List.of(owner));

        mockMvc.perform(get("/rooms/{roomId}/participants", room.getId()).with(authenticatedAs("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value("user-1"))
                .andExpect(jsonPath("$.items[0].role").value("OWNER"))
                .andExpect(jsonPath("$.items[0].isMuted").value(false))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void returnsDeletedMessagesAsTombstones() throws Exception {
        ChatRoom room = ChatTestFixtures.groupRoom("user-1");
        givenRoomWithParticipants(room, ChatTestFixtures.participant("user-1", ParticipantRole.OWNER));

        Message deletedMessage = ChatTestFixtures.textMessage("user-1");
        deletedMessage.setDeleted(true);
        deletedMessage.setContent(null);
        when(messageRepository.findPageByRoom(room.getId(), null, null, 31)).thenReturn(List.of(deletedMessage));

        mockMvc.perform(get("/rooms/{roomId}/messages", room.getId()).with(authenticatedAs("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ChatTestFixtures.MESSAGE_ID))
                .andExpect(jsonPath("$.items[0].isDeleted").value(true))
                .andExpect(jsonPath("$.items[0].content").value(nullValue()))
                .andExpect(jsonPath("$.items[0].isEdited").value(false))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void deniesAccessToInternalEndpointsWithoutTheInternalToken() throws Exception {
        mockMvc.perform(post("/internal/messages/{messageId}/attachments", ChatTestFixtures.MESSAGE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"attachmentIds":["attachment-1"]}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsTheAttachmentServiceToUpdateAttachmentsWithTheInternalToken() throws Exception {
        Message message = ChatTestFixtures.textMessage("user-1");
        when(messageRepository.findById(ChatTestFixtures.MESSAGE_ID)).thenReturn(Optional.of(message));

        mockMvc.perform(patch("/internal/messages/{messageId}/attachments", ChatTestFixtures.MESSAGE_ID)
                .header("X-BadrLink-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"attachmentIds":["attachment-1"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentIds[0]").value("attachment-1"));
    }

    private void givenRoomWithoutMembership(ChatRoom room) {
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), OUTSIDER)).thenReturn(Optional.empty());
    }
}
