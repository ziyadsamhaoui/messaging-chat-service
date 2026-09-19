package com.ziyadsamhaoui.messagingchatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.dto.request.EditMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.SendMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec;
import com.ziyadsamhaoui.messagingchatservice.service.support.PageSizeResolver;
import com.ziyadsamhaoui.messagingchatservice.service.support.SenderIdentityResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageServiceTest {

    private static final String CALLER = "user-1";
    private static final String COUNTERPART = "user-2";

    private MessageRepository messageRepository;
    private ChatRoomRepository chatRoomRepository;
    private ParticipantRepository participantRepository;
    private BlockPolicy blockPolicy;
    private SenderIdentityResolver senderIdentityResolver;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        ChatProperties chatProperties = new ChatProperties(30, 100, 100, 4000, Duration.ofHours(72),
                Duration.ofDays(30), true, "token");

        messageRepository = mock(MessageRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        participantRepository = mock(ParticipantRepository.class);
        blockPolicy = mock(BlockPolicy.class);
        senderIdentityResolver = mock(SenderIdentityResolver.class);

        messageService = new MessageService(messageRepository, chatRoomRepository, participantRepository,
                new RoomAccessService(chatRoomRepository, participantRepository), blockPolicy, senderIdentityResolver,
                new CursorCodec(), new PageSizeResolver(chatProperties), chatProperties);
    }

    @Test
    void rejectsMessagesFromMutedParticipants() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Participant muted = ChatTestFixtures.mutedParticipant(CALLER, ParticipantRole.GUEST,
                Instant.now().plus(Duration.ofHours(1)));
        givenRoomContext(room, muted);

        assertThatThrownBy(
                () -> messageService.sendMessage(CALLER, room.getId(), new SendMessageRequest(MessageType.TEXT, "hi")))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(messageRepository, never()).insert(any(Message.class));
    }

    @Test
    void allowsMessagesOnceTheMuteWindowHasPassed() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Participant formerlyMuted = ChatTestFixtures.mutedParticipant(CALLER, ParticipantRole.GUEST,
                Instant.now().minus(Duration.ofMinutes(1)));
        givenRoomContext(room, formerlyMuted);
        givenSuccessfulInsert();

        var response = messageService.sendMessage(CALLER, room.getId(),
                new SendMessageRequest(MessageType.TEXT, "hi"));

        assertThat(response.senderId()).isEqualTo(CALLER);
        assertThat(response.senderUsername()).isEqualTo("zara");
        verify(chatRoomRepository).setLastMessage(room.getId(), ChatTestFixtures.MESSAGE_ID);
    }

    @Test
    void requiresContentForTextMessages() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST));

        assertThatThrownBy(
                () -> messageService.sendMessage(CALLER, room.getId(), new SendMessageRequest(MessageType.TEXT, "   ")))
                .isInstanceOf(InvalidRequestException.class);

        verify(messageRepository, never()).insert(any(Message.class));
    }

    @Test
    void rejectsMessagesThatExceedTheConfiguredLength() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST));

        String oversizedContent = "a".repeat(4001);

        assertThatThrownBy(() -> messageService.sendMessage(CALLER, room.getId(),
                new SendMessageRequest(MessageType.TEXT, oversizedContent)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void verifiesBlocksForDirectRooms() {
        ChatRoom directRoom = ChatTestFixtures.directRoom(CALLER, "direct-key");
        givenRoomContext(directRoom, ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST));
        when(participantRepository.findByRoomId(directRoom.getId()))
                .thenReturn(List.of(ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST),
                        ChatTestFixtures.participant(COUNTERPART, ParticipantRole.GUEST)));
        givenSuccessfulInsert();

        messageService.sendMessage(CALLER, directRoom.getId(), new SendMessageRequest(MessageType.TEXT, "hi"));

        verify(blockPolicy).assertNotBlocked(CALLER, COUNTERPART);
    }

    @Test
    void skipsBlockVerificationForGroupRooms() {
        ChatRoom groupRoom = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(groupRoom, ChatTestFixtures.participant(CALLER, ParticipantRole.OWNER));
        givenSuccessfulInsert();

        messageService.sendMessage(CALLER, groupRoom.getId(), new SendMessageRequest(MessageType.TEXT, "hi"));

        verifyNoInteractions(blockPolicy);
    }

    @Test
    void rejectsEditsFromParticipantsOtherThanTheSender() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.ADMIN));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(ChatTestFixtures.textMessage("user-9")));

        assertThatThrownBy(() -> messageService.editMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID,
                new EditMessageRequest("edited"))).isInstanceOf(ForbiddenOperationException.class);

        verify(messageRepository, never()).updateContent(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void marksTheMessageAsEditedForTheSender() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(ChatTestFixtures.textMessage(CALLER)));

        var response = messageService.editMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID,
                new EditMessageRequest("edited"));

        assertThat(response.content()).isEqualTo("edited");
        assertThat(response.edited()).isTrue();
        verify(messageRepository).updateContent(ChatTestFixtures.MESSAGE_ID, "edited", response.editedAt());
    }

    @Test
    void allowsTheRoomOwnerToDeleteForeignMessages() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.OWNER));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(ChatTestFixtures.textMessage("user-9")));

        messageService.deleteMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID);

        verify(messageRepository).softDelete(ChatTestFixtures.MESSAGE_ID);
    }

    @Test
    void rejectsDeletionByGuestsOfForeignMessages() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        givenRoomContext(room, ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(ChatTestFixtures.textMessage("user-9")));

        assertThatThrownBy(() -> messageService.deleteMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(messageRepository, never()).softDelete(anyString());
    }

    @Test
    void appendsAttachmentsWithoutDuplicatingThem() {
        Message message = ChatTestFixtures.textMessage(CALLER);
        message.setAttachmentIds(List.of("attachment-1"));
        when(messageRepository.findById(ChatTestFixtures.MESSAGE_ID)).thenReturn(Optional.of(message));

        var response = messageService.appendAttachments(ChatTestFixtures.MESSAGE_ID,
                List.of("attachment-1", "attachment-2"));

        assertThat(response.attachmentIds()).containsExactly("attachment-1", "attachment-2");
        verify(messageRepository, times(1)).addAttachments(ChatTestFixtures.MESSAGE_ID,
                List.of("attachment-1", "attachment-2"));
    }

    private void givenRoomContext(ChatRoom room, Participant participant) {
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), participant.getUserId()))
                .thenReturn(Optional.of(participant));
    }

    private void givenSuccessfulInsert() {
        when(senderIdentityResolver.resolveUsername(CALLER)).thenReturn("zara");
        when(messageRepository.insert(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(ChatTestFixtures.MESSAGE_ID);
            return message;
        });
    }
}
