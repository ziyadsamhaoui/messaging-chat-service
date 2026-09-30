package com.ziyadsamhaoui.messagingchatservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.ChatTestFixtures;
import com.ziyadsamhaoui.messagingchatservice.dto.request.EditMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.SendMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.MessageService;
import com.ziyadsamhaoui.messagingchatservice.service.RoomAccessService;
import com.ziyadsamhaoui.messagingchatservice.service.RoomWriter;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec;
import com.ziyadsamhaoui.messagingchatservice.service.support.PageSizeResolver;
import com.ziyadsamhaoui.messagingchatservice.service.support.SenderIdentityResolver;
import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sprint 6 §3.5 (no Docker): every Chat domain write that should produce an event
 * appends exactly one outbox document with the right aggregate, event type, and
 * payload — and writes with no domain effect append none.
 */
class ChatOutboxFlowTest {

    private static final String CALLER = "user-1";
    private static final String COUNTERPART = "user-2";

    private MessageRepository messageRepository;
    private ChatRoomRepository chatRoomRepository;
    private ParticipantRepository participantRepository;
    private OutboxWriter outboxWriter;

    private MessageService messageService;
    private RoomWriter roomWriter;

    @BeforeEach
    void setUp() {
        ChatProperties properties = new ChatProperties(30, 100, 100, 4000, Duration.ofHours(72),
                Duration.ofDays(30), false, "token");
        messageRepository = mock(MessageRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        participantRepository = mock(ParticipantRepository.class);
        outboxWriter = mock(OutboxWriter.class);

        messageService = new MessageService(messageRepository, chatRoomRepository, participantRepository,
                new RoomAccessService(chatRoomRepository, participantRepository), mock(BlockPolicy.class),
                mock(SenderIdentityResolver.class), new CursorCodec(), new PageSizeResolver(properties), properties,
                outboxWriter);
        roomWriter = new RoomWriter(chatRoomRepository, participantRepository, outboxWriter);
    }

    @Test
    void sendMessageAppendsMessageSentWithFullContent() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Participant caller = ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST);
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), CALLER)).thenReturn(Optional.of(caller));
        when(messageRepository.insert(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(ChatTestFixtures.MESSAGE_ID);
            return message;
        });

        messageService.sendMessage(CALLER, room.getId(), new SendMessageRequest(MessageType.TEXT, "hello world"));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).append(eq("Message"), eq(ChatTestFixtures.MESSAGE_ID), eq(ChatEvents.MESSAGE_SENT),
                payload.capture());
        ChatEvents.MessageSent sent = (ChatEvents.MessageSent) payload.getValue();
        assertThat(sent.content()).isEqualTo("hello world");
        assertThat(sent.roomId()).isEqualTo(room.getId());
        assertThat(sent.senderId()).isEqualTo(CALLER);
    }

    @Test
    void editMessageAppendsMessageEdited() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Message message = ChatTestFixtures.textMessage(CALLER);
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), CALLER))
                .thenReturn(Optional.of(ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST)));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(message));

        messageService.editMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID,
                new EditMessageRequest("edited body"));

        verify(outboxWriter).append(eq("Message"), eq(ChatTestFixtures.MESSAGE_ID), eq(ChatEvents.MESSAGE_EDITED),
                any(ChatEvents.MessageEdited.class));
    }

    @Test
    void deleteMessageAppendsMessageDeleted() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Message message = ChatTestFixtures.textMessage(CALLER);
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), CALLER))
                .thenReturn(Optional.of(ChatTestFixtures.participant(CALLER, ParticipantRole.GUEST)));
        when(messageRepository.findByIdAndRoomId(ChatTestFixtures.MESSAGE_ID, room.getId()))
                .thenReturn(Optional.of(message));

        messageService.deleteMessage(CALLER, room.getId(), ChatTestFixtures.MESSAGE_ID);

        verify(outboxWriter).append(eq("Message"), eq(ChatTestFixtures.MESSAGE_ID), eq(ChatEvents.MESSAGE_DELETED),
                any(ChatEvents.MessageDeleted.class));
    }

    @Test
    void directRoomCreationAppendsRoomCreatedAndParticipantAddedPerMember() {
        when(chatRoomRepository.insert(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(ChatTestFixtures.ROOM_ID);
            return room;
        });

        roomWriter.insertDirectRoom(CALLER, COUNTERPART, "direct-key", ChatTestFixtures.REFERENCE_TIME);

        verify(outboxWriter).append(eq("ChatRoom"), eq(ChatTestFixtures.ROOM_ID), eq(ChatEvents.ROOM_CREATED),
                any(ChatEvents.RoomCreated.class));
        verify(outboxWriter).append(eq("Participant"), eq(CALLER), eq(ChatEvents.PARTICIPANT_ADDED),
                any(ChatEvents.ParticipantAdded.class));
        verify(outboxWriter).append(eq("Participant"), eq(COUNTERPART), eq(ChatEvents.PARTICIPANT_ADDED),
                any(ChatEvents.ParticipantAdded.class));
        verify(participantRepository).insert(anyList());
    }

    @Test
    void groupRoomCreationAppendsRoomCreatedPlusCreatorAndMembers() {
        when(chatRoomRepository.insert(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(ChatTestFixtures.ROOM_ID);
            return room;
        });

        roomWriter.insertGroupRoom(CALLER, "core", Set.of(COUNTERPART, "user-3"),
                ChatTestFixtures.REFERENCE_TIME);

        verify(outboxWriter).append(eq("ChatRoom"), eq(ChatTestFixtures.ROOM_ID), eq(ChatEvents.ROOM_CREATED),
                any(ChatEvents.RoomCreated.class));
        verify(outboxWriter).append(eq("Participant"), eq(CALLER), eq(ChatEvents.PARTICIPANT_ADDED),
                any(ChatEvents.ParticipantAdded.class));
        verify(outboxWriter, org.mockito.Mockito.times(3))
                .append(eq("Participant"), anyString(), eq(ChatEvents.PARTICIPANT_ADDED),
                        any(ChatEvents.ParticipantAdded.class));
    }

    @Test
    void rejectedSendAppendsNothing() {
        ChatRoom room = ChatTestFixtures.groupRoom(CALLER);
        Participant muted = ChatTestFixtures.mutedParticipant(CALLER, ParticipantRole.GUEST,
                java.time.Instant.now().plus(Duration.ofHours(1)));
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(room.getId(), CALLER)).thenReturn(Optional.of(muted));

        try {
            messageService.sendMessage(CALLER, room.getId(), new SendMessageRequest(MessageType.TEXT, "hi"));
        } catch (RuntimeException expected) {
            // muted participant is rejected before any write
        }

        verify(outboxWriter, never()).append(anyString(), anyString(), anyString(), any());
    }
}
