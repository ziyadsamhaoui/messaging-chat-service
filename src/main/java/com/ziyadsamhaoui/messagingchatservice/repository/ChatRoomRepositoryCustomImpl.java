package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
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
public class ChatRoomRepositoryCustomImpl implements ChatRoomRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ChatRoomRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ChatRoom> findActiveByDirectKey(String directKey) {
        Query query = Query.query(Criteria.where("directKey").is(directKey).and("deleted").is(false));
        return Optional.ofNullable(mongoTemplate.findOne(query, ChatRoom.class));
    }

    @Override
    public List<ChatRoom> findActivePageByIds(Collection<String> roomIds, Instant cursorCreatedAt, String cursorId,
            int limit) {

        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }

        Criteria criteria = Criteria.where("_id").in(roomIds).and("deleted").is(false);

        if (cursorCreatedAt != null && cursorId != null) {
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("createdAt").lt(cursorCreatedAt),
                    new Criteria().andOperator(Criteria.where("createdAt").is(cursorCreatedAt),
                            Criteria.where("_id").lt(cursorId))));
        }

        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .limit(limit);

        return mongoTemplate.find(query, ChatRoom.class);
    }

    @Override
    public void setLastMessage(String roomId, String lastMessageId) {
        Update update = lastMessageId == null
                ? new Update().unset("lastMessageId")
                : new Update().set("lastMessageId", lastMessageId);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(roomId)), update, ChatRoom.class);
    }

    @Override
    public void softDelete(String roomId, boolean releaseDirectKey) {
        Update update = new Update().set("deleted", true).set("deletedAt", Instant.now());
        if (releaseDirectKey) {
            update.unset("directKey");
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(roomId)), update, ChatRoom.class);
    }
}
