package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;

public interface MessageReactionRepositoryCustom {

    MessageReaction upsert(MessageReaction reaction);
}
