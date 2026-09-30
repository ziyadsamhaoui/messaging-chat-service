package com.ziyadsamhaoui.messagingchatservice.service;

import com.ziyadsamhaoui.messagingchatservice.dto.request.ReactionRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ReactionResponse;
import com.ziyadsamhaoui.messagingchatservice.exception.ConflictException;
import com.ziyadsamhaoui.messagingchatservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;
import com.ziyadsamhaoui.messagingchatservice.outbox.ChatEvents;
import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxWriter;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageReactionRepository;
import com.ziyadsamhaoui.messagingchatservice.repository.MessageRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactionService {

    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final RoomAccessService roomAccessService;
    private final OutboxWriter outboxWriter;

    public ReactionService(MessageRepository messageRepository, MessageReactionRepository messageReactionRepository,
            RoomAccessService roomAccessService, OutboxWriter outboxWriter) {

        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.roomAccessService = roomAccessService;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public ReactionResponse addReaction(String callerId, String roomId, String messageId, ReactionRequest request) {
        roomAccessService.requireParticipant(roomId, callerId);
        Message message = requireMessage(roomId, messageId);

        if (message.isDeleted()) {
            throw new ConflictException("MESSAGE_DELETED", "A deleted message cannot be reacted to");
        }

        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(messageId);
        reaction.setUserId(callerId);
        reaction.setEmoji(request.emoji());
        reaction.setReactedAt(Instant.now());

        MessageReaction saved = messageReactionRepository.upsert(reaction);
        outboxWriter.append("MessageReaction", messageId, ChatEvents.REACTION_ADDED,
                new ChatEvents.ReactionAdded(messageId, roomId, callerId, request.emoji(), saved.getReactedAt()));
        return ReactionResponse.from(saved);
    }

    @Transactional
    public void removeReaction(String callerId, String roomId, String messageId) {
        roomAccessService.requireParticipant(roomId, callerId);
        requireMessage(roomId, messageId);

        messageReactionRepository.deleteByMessageIdAndUserId(messageId, callerId);
        outboxWriter.append("MessageReaction", messageId, ChatEvents.REACTION_REMOVED,
                new ChatEvents.ReactionRemoved(messageId, roomId, callerId, Instant.now()));
    }

    private Message requireMessage(String roomId, String messageId) {
        return messageRepository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND",
                        "Message not found in this room"));
    }
}
