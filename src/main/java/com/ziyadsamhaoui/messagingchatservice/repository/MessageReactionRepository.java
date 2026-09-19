package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageReactionRepository
        extends MongoRepository<MessageReaction, String>, MessageReactionRepositoryCustom {

    Optional<MessageReaction> findByMessageIdAndUserId(String messageId, String userId);

    List<MessageReaction> findByMessageId(String messageId);

    long deleteByMessageIdAndUserId(String messageId, String userId);
}
