package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepositoryCustomImpl implements InvitationRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public InvitationRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean updateStatus(String invitationId, InvitationStatus expectedStatus, InvitationStatus nextStatus) {
        Query query = Query
                .query(Criteria.where("_id").is(invitationId).and("status").is(expectedStatus));
        Update update = new Update().set("status", nextStatus);
        UpdateResult result = mongoTemplate.updateFirst(query, update, Invitation.class);
        return result.getModifiedCount() == 1L;
    }
}
