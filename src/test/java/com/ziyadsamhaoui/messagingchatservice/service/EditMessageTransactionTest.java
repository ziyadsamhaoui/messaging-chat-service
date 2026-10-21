package com.ziyadsamhaoui.messagingchatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.dto.request.EditMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.outbox.ChatEvents;
import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxEvent;
import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxWriter;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec;
import com.ziyadsamhaoui.messagingchatservice.service.support.PageSizeResolver;
import com.ziyadsamhaoui.messagingchatservice.service.support.SenderIdentityResolver;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

@SpringJUnitConfig(EditMessageTransactionTest.TransactionTestConfiguration.class)
class EditMessageTransactionTest {

    private static final String CALLER = "user-1";

    @Autowired
    private MessageService messageService;

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TrackingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        when(chatRoomRepository.findById(ChatTestFixtures.ROOM_ID))
                .thenReturn(Optional.of(ChatTestFixtures.groupRoom(CALLER)));
        when(participantRepository.findByRoomIdAndUserId(ChatTestFixtures.ROOM_ID, CALLER))
                .thenReturn(Optional.of(ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST)));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, ChatTestFixtures.ROOM_ID))
                .thenReturn(Optional.of(ChatTestFixtures.textMessage(CALLER)));
        doNothing().when(messageRepository).updateContent(any(), any(), any());
    }

    @Test
    void editMessageOpensTheTransactionThatTheMandatoryOutboxWriteJoins() {
        messageService.editMessage(CALLER, ChatTestFixtures.ROOM_ID, ChatTestFixtures.MESSAGE_ID,
                new EditMessageRequest("edited"));

        verify(messageRepository).updateContent(any(), any(), any());
        verify(mongoTemplate).save(any(OutboxEvent.class));
        assertThat(transactionManager.transactionBegan())
                .as("editMessage must enter a transaction so the MANDATORY outbox write participates")
                .isTrue();
    }

    @Test
    void outboxWriteWithoutAnActiveTransactionIsRejectedByTheMandatoryPropagation() {
        assertThatThrownBy(() -> outboxWriter.append("Message", "msg-1", ChatEvents.MESSAGE_EDITED, "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aFailureDuringTheOutboxWriteRollsBackTheEditTransaction() {
        doThrow(new IllegalStateException("outbox unavailable")).when(mongoTemplate).save(any(OutboxEvent.class));

        assertThatThrownBy(() -> messageService.editMessage(CALLER, ChatTestFixtures.ROOM_ID,
                ChatTestFixtures.MESSAGE_ID, new EditMessageRequest("edited")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(transactionManager.rollbackOccurred()).isTrue();
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionTestConfiguration {

        @Bean
        TrackingTransactionManager transactionManager() {
            return new TrackingTransactionManager();
        }

        @Bean
        MongoTemplate mongoTemplate() {
            return mock(MongoTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        OutboxWriter outboxWriter(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
            return new OutboxWriter(mongoTemplate, objectMapper);
        }

        @Bean
        ChatRoomRepository chatRoomRepository() {
            return mock(ChatRoomRepository.class);
        }

        @Bean
        ParticipantRepository participantRepository() {
            return mock(ParticipantRepository.class);
        }

        @Bean
        MessageRepository messageRepository() {
            return mock(MessageRepository.class);
        }

        @Bean
        RoomAccessService roomAccessService(ChatRoomRepository chatRoomRepository,
                ParticipantRepository participantRepository) {
            return new RoomAccessService(chatRoomRepository, participantRepository);
        }

        @Bean
        BlockPolicy blockPolicy() {
            return mock(BlockPolicy.class);
        }

        @Bean
        SenderIdentityResolver senderIdentityResolver() {
            return mock(SenderIdentityResolver.class);
        }

        @Bean
        CursorCodec cursorCodec() {
            return new CursorCodec();
        }

        @Bean
        ChatProperties chatProperties() {
            return new ChatProperties(30, 100, 100, 4000, Duration.ofHours(72), Duration.ofDays(30), false, "token");
        }

        @Bean
        PageSizeResolver pageSizeResolver(ChatProperties chatProperties) {
            return new PageSizeResolver(chatProperties);
        }

        @Bean
        MessageService messageService(MessageRepository messageRepository, ChatRoomRepository chatRoomRepository,
                ParticipantRepository participantRepository, RoomAccessService roomAccessService,
                BlockPolicy blockPolicy, SenderIdentityResolver senderIdentityResolver, CursorCodec cursorCodec,
                PageSizeResolver pageSizeResolver, ChatProperties chatProperties, OutboxWriter outboxWriter) {

            return new MessageService(messageRepository, chatRoomRepository, participantRepository, roomAccessService,
                    blockPolicy, senderIdentityResolver, cursorCodec, pageSizeResolver, chatProperties, outboxWriter);
        }
    }

    static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
        private boolean began;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return depth.get() > 0;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            depth.set(depth.get() + 1);
            began = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            rolledBack = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            depth.set(depth.get() - 1);
        }

        boolean transactionBegan() {
            return began;
        }

        boolean rollbackOccurred() {
            return rolledBack;
        }
    }
}
