package com.ziyadsamhaoui.messagingchatservice;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.InvitationRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageReactionRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ReadCursorRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ChatIntegrationTest.NoOpTransactionConfiguration.class)
public abstract class ChatIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected ChatRoomRepository chatRoomRepository;

    @MockitoBean
    protected ParticipantRepository participantRepository;

    @MockitoBean
    protected MessageRepository messageRepository;

    @MockitoBean
    protected MessageReactionRepository messageReactionRepository;

    @MockitoBean
    protected ReadCursorRepository readCursorRepository;

    @MockitoBean
    protected InvitationRepository invitationRepository;

    @MockitoBean
    protected UserServiceClient userServiceClient;

    protected static JwtRequestPostProcessor authenticatedAs(String userId) {
        return jwt().jwt(jwt -> jwt.subject(userId));
    }

    protected void givenRoomWithParticipants(ChatRoom room, Participant... participants) {
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        for (Participant participant : participants) {
            when(participantRepository.findByRoomIdAndUserId(room.getId(), participant.getUserId()))
                    .thenReturn(Optional.of(participant));
        }
    }

    protected void givenUsername(String userId, String username) {
        when(userServiceClient.getProfile(userId)).thenReturn(new UserProfileResponse(userId, username));
    }

    /**
     * MongoDB transactions require a replica set, which is not available in the test environment, so the transactional
     * boundary is replaced with a resource-less manager. Transactional composition itself is exercised against a
     * replica set in integration environments.
     */
    @TestConfiguration
    public static class NoOpTransactionConfiguration {

        @Bean
        @Primary
        PlatformTransactionManager chatServiceTestTransactionManager() {
            return new NoOpTransactionManager();
        }
    }

    static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
