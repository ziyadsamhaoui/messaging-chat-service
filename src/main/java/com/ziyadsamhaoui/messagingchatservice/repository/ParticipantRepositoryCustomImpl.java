package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.enums.ParticipantRole;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepositoryCustomImpl implements ParticipantRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ParticipantRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Participant> findPageByRoom(String roomId, String cursorId, int limit) {
        Criteria criteria = Criteria.where("roomId").is(roomId);
        if (cursorId != null) {
            criteria = criteria.and("_id").gt(cursorId);
        }
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.asc("_id"))).limit(limit);
        return mongoTemplate.find(query, Participant.class);
    }

    @Override
    public void updateRole(String roomId, String userId, ParticipantRole role) {
        Update update = new Update().set("role", role);
        mongoTemplate.updateFirst(participantQuery(roomId, userId), update, Participant.class);
    }

    @Override
    public void updateMute(String roomId, String userId, boolean muted, Instant mutedUntil) {
        Update update = new Update().set("muted", muted);
        if (mutedUntil == null) {
            update.unset("mutedUntil");
        } else {
            update.set("mutedUntil", mutedUntil);
        }
        mongoTemplate.updateFirst(participantQuery(roomId, userId), update, Participant.class);
    }

    private Query participantQuery(String roomId, String userId) {
        return Query.query(Criteria.where("roomId").is(roomId).and("userId").is(userId));
    }
}
