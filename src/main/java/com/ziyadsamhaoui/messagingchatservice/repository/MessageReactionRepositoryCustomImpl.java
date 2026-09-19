package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MessageReactionRepositoryCustomImpl implements MessageReactionRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public MessageReactionRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public MessageReaction upsert(MessageReaction reaction) {
        Query query = Query
                .query(Criteria.where("messageId").is(reaction.getMessageId()).and("userId").is(reaction.getUserId()));

        Update update = new Update().set("emoji", reaction.getEmoji()).set("reactedAt", reaction.getReactedAt())
                .setOnInsert("messageId", reaction.getMessageId()).setOnInsert("userId", reaction.getUserId());

        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), MessageReaction.class);
    }
}
