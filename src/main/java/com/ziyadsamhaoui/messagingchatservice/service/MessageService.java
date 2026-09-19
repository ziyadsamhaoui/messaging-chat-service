package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.dto.request.EditMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.SendMessageRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.CursorPage;
import com.ziyadsamhaoui.messagingchatservice.dto.response.MessageResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.ConflictException;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.ParticipantRepository;
import com.ziyadsamhaoui.messagingchatservice.service.RoomAccessService.RoomContext;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec.Cursor;
import com.ziyadsamhaoui.messagingchatservice.service.support.PageSizeResolver;
import com.ziyadsamhaoui.messagingchatservice.service.support.SenderIdentityResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomAccessService roomAccessService;
    private final BlockPolicy blockPolicy;
    private final SenderIdentityResolver senderIdentityResolver;
    private final CursorCodec cursorCodec;
    private final PageSizeResolver pageSizeResolver;
    private final ChatProperties chatProperties;

    public MessageService(MessageRepository messageRepository, ChatRoomRepository chatRoomRepository,
            ParticipantRepository participantRepository, RoomAccessService roomAccessService, BlockPolicy blockPolicy,
            SenderIdentityResolver senderIdentityResolver, CursorCodec cursorCodec, PageSizeResolver pageSizeResolver,
            ChatProperties chatProperties) {

        this.messageRepository = messageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.roomAccessService = roomAccessService;
        this.blockPolicy = blockPolicy;
        this.senderIdentityResolver = senderIdentityResolver;
        this.cursorCodec = cursorCodec;
        this.pageSizeResolver = pageSizeResolver;
        this.chatProperties = chatProperties;
    }

    public MessageResponse sendMessage(String callerId, String roomId, SendMessageRequest request) {
        RoomContext context = roomAccessService.requireParticipant(roomId, callerId);
        roomAccessService.requireNotMuted(context);

        String content = validateContent(request);

        if (context.room().getType() == RoomType.DIRECT) {
            blockPolicy.assertNotBlocked(callerId, counterpartParticipantId(roomId, callerId));
        }

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(callerId);
        message.setSenderUsername(senderIdentityResolver.resolveUsername(callerId));
        message.setType(request.type());
        message.setContent(content);
        message.setCreatedAt(Instant.now());

        Message saved = messageRepository.insert(message);
        refreshLastMessage(roomId, saved.getId());

        return MessageResponse.from(saved);
    }

    public CursorPage<MessageResponse> getHistory(String callerId, String roomId, String cursor, Integer limit) {
        int pageSize = pageSizeResolver.resolve(limit);
        roomAccessService.requireParticipant(roomId, callerId);

        Optional<Cursor> decodedCursor = cursorCodec.decode(cursor);
        List<Message> messages = messageRepository.findPageByRoom(roomId,
                decodedCursor.map(Cursor::createdAt).orElse(null), decodedCursor.map(Cursor::id).orElse(null),
                pageSize + 1);

        boolean hasMore = messages.size() > pageSize;
        List<Message> page = hasMore ? messages.subList(0, pageSize) : messages;

        List<MessageResponse> items = page.stream().map(MessageResponse::from).toList();
        String nextCursor = null;

        if (hasMore && !page.isEmpty()) {
            Message lastMessage = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(lastMessage.getCreatedAt(), lastMessage.getId());
        }

        return CursorPage.of(items, nextCursor, hasMore);
    }

    public MessageResponse editMessage(String callerId, String roomId, String messageId, EditMessageRequest request) {
        roomAccessService.requireParticipant(roomId, callerId);
        Message message = requireMessage(roomId, messageId);

        if (!callerId.equals(message.getSenderId())) {
            throw new ForbiddenOperationException("MESSAGE_EDIT_DENIED", "Only the sender can edit a message");
        }

        if (message.isDeleted()) {
            throw new ConflictException("MESSAGE_DELETED", "A deleted message cannot be edited");
        }

        if (request.content().length() > chatProperties.maxMessageLength()) {
            throw new InvalidRequestException("MESSAGE_TOO_LONG",
                    "content must not exceed " + chatProperties.maxMessageLength() + " characters");
        }

        Instant editedAt = Instant.now();
        messageRepository.updateContent(messageId, request.content(), editedAt);

        message.setContent(request.content());
        message.setEdited(true);
        message.setEditedAt(editedAt);

        return MessageResponse.from(message);
    }

    public void deleteMessage(String callerId, String roomId, String messageId) {
        RoomContext context = roomAccessService.requireParticipant(roomId, callerId);
        Message message = requireMessage(roomId, messageId);

        boolean callerIsSender = callerId.equals(message.getSenderId());

        if (!callerIsSender && !context.participant().isOwner()) {
            throw new ForbiddenOperationException("MESSAGE_DELETE_DENIED",
                    "Only the sender or the room owner can delete a message");
        }

        if (message.isDeleted()) {
            return;
        }

        messageRepository.softDelete(messageId);

        if (messageId.equals(context.room().getLastMessageId())) {
            String replacementId = messageRepository.findLatestActiveInRoom(roomId).map(Message::getId).orElse(null);
            refreshLastMessage(roomId, replacementId);
        }
    }

    public MessageResponse appendAttachments(String messageId, List<String> attachmentIds) {
        List<String> newAttachmentIds = attachmentIds.stream().filter(StringUtils::hasText).toList();

        if (newAttachmentIds.isEmpty()) {
            throw new InvalidRequestException("INVALID_ATTACHMENTS", "At least one attachment identifier is required");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Message not found"));

        if (message.isDeleted()) {
            throw new ConflictException("MESSAGE_DELETED", "Attachments cannot be added to a deleted message");
        }

        messageRepository.addAttachments(messageId, newAttachmentIds);

        List<String> merged = new ArrayList<>(
                message.getAttachmentIds() == null ? List.of() : message.getAttachmentIds());
        newAttachmentIds.stream().filter(attachmentId -> !merged.contains(attachmentId)).forEach(merged::add);
        message.setAttachmentIds(merged);

        return MessageResponse.from(message);
    }

    private Message requireMessage(String roomId, String messageId) {
        return messageRepository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND",
                        "Message not found in this room"));
    }

    private String counterpartParticipantId(String roomId, String callerId) {
        return participantRepository.findByRoomId(roomId).stream().map(Participant::getUserId)
                .filter(userId -> !userId.equals(callerId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_COUNTERPART_MISSING",
                        "The direct room is missing its counterpart participant"));
    }

    private String validateContent(SendMessageRequest request) {
        String content = request.content();

        if (request.type() == MessageType.TEXT && !StringUtils.hasText(content)) {
            throw new InvalidRequestException("MESSAGE_CONTENT_REQUIRED", "A text message requires content");
        }

        if (content != null && content.length() > chatProperties.maxMessageLength()) {
            throw new InvalidRequestException("MESSAGE_TOO_LONG",
                    "content must not exceed " + chatProperties.maxMessageLength() + " characters");
        }

        return content;
    }

    private void refreshLastMessage(String roomId, String lastMessageId) {
        try {
            chatRoomRepository.setLastMessage(roomId, lastMessageId);
        } catch (DataAccessException exception) {
            log.warn("Unable to refresh lastMessageId for room {}", roomId, exception);
        }
    }
}
