package com.ziyadsamhaoui.messagingchatservice.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ziyadsamhaoui.messagingchatservice.ChatIntegrationTest;
import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.service.support.DirectKeyFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DirectRoomDeduplicationTest extends ChatIntegrationTest {

    private static final String CALLER = "user-1";
    private static final String COUNTERPART = "user-2";
    private static final String DIRECT_ROOM_REQUEST = """
            {"type":"DIRECT","participantIds":["user-2"]}
            """;

    private final DirectKeyFactory directKeyFactory = new DirectKeyFactory();

    @Test
    void returnsTheExistingRoomWithoutCreatingDuplicates() throws Exception {
        String directKey = directKeyFactory.create(CALLER, COUNTERPART);
        ChatRoom existingRoom = ChatTestFixtures.directRoom(CALLER, directKey);
        when(chatRoomRepository.findActiveByDirectKey(directKey)).thenReturn(Optional.of(existingRoom));

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content(DIRECT_ROOM_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingRoom.getId()))
                .andExpect(jsonPath("$.type").value("DIRECT"));

        verify(chatRoomRepository, never()).insert(any(ChatRoom.class));
        verify(userServiceClient, never()).getProfile(anyString());
    }

    @Test
    void createsTheDirectRoomOnlyOnceForRepeatedRequests() throws Exception {
        String directKey = directKeyFactory.create(CALLER, COUNTERPART);

        when(chatRoomRepository.findActiveByDirectKey(directKey)).thenReturn(Optional.empty());
        when(userServiceClient.getProfile(COUNTERPART)).thenReturn(new UserProfileResponse(COUNTERPART, "omar"));
        when(chatRoomRepository.insert(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(ChatTestFixtures.ROOM_ID);
            return room;
        });
        when(participantRepository.insert(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content(DIRECT_ROOM_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ChatTestFixtures.ROOM_ID));

        verify(chatRoomRepository, times(1)).insert(any(ChatRoom.class));

        when(chatRoomRepository.findActiveByDirectKey(directKey))
                .thenReturn(Optional.of(ChatTestFixtures.directRoom(CALLER, directKey)));

        mockMvc.perform(post("/rooms").with(authenticatedAs(CALLER)).contentType(MediaType.APPLICATION_JSON)
                .content(DIRECT_ROOM_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ChatTestFixtures.ROOM_ID));

        verify(chatRoomRepository, times(1)).insert(any(ChatRoom.class));
    }

    @Test
    void hashesTheParticipantPairIndependentlyOfTheirOrder() {
        assertThat(directKeyFactory.create(CALLER, COUNTERPART))
                .isEqualTo(directKeyFactory.create(COUNTERPART, CALLER));
    }
}
