package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Message;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepositoryCustomImpl implements MessageRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public MessageRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Message> findPageByRoom(String roomId, Instant cursorCreatedAt, String cursorId, int limit) {

        Criteria criteria = Criteria.where("roomId").is(roomId);

        if (cursorCreatedAt != null && cursorId != null) {
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("createdAt").lt(cursorCreatedAt),
                    new Criteria().andOperator(Criteria.where("createdAt").is(cursorCreatedAt),
                            Criteria.where("_id").lt(cursorId))));
        }

        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .limit(limit);

        return mongoTemplate.find(query, Message.class);
    }

    @Override
    public Optional<Message> findLatestActiveInRoom(String roomId) {
        Query query = Query.query(Criteria.where("roomId").is(roomId).and("deleted").is(false))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, Message.class));
    }

    @Override
    public void updateContent(String messageId, String content, Instant editedAt) {
        Update update = new Update().set("content", content).set("edited", true).set("editedAt", editedAt);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(messageId)), update, Message.class);
    }

    @Override
    public void softDelete(String messageId) {
        Update update = new Update().set("deleted", true).set("deletedAt", Instant.now()).unset("content")
                .set("attachmentIds", List.of());
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(messageId)), update, Message.class);
    }

    @Override
    public void addAttachments(String messageId, Collection<String> attachmentIds) {
        Update update = new Update().addToSet("attachmentIds").each(attachmentIds);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(messageId)), update, Message.class);
    }
}
