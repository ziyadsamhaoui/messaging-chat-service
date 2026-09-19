package com.ziyadsamhaoui.messagingchatservice.dto.response;

import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;
import java.time.Instant;

public record ReactionResponse(String id, String messageId, String userId, String emoji, Instant reactedAt) {

    public static ReactionResponse from(MessageReaction reaction) {
        return new ReactionResponse(reaction.getId(), reaction.getMessageId(), reaction.getUserId(),
                reaction.getEmoji(), reaction.getReactedAt());
    }
}
