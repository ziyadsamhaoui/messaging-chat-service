package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ReadCursorRepositoryCustomImpl implements ReadCursorRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ReadCursorRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ReadCursor upsert(ReadCursor cursor) {
        Query query = Query.query(Criteria.where("roomId").is(cursor.getRoomId()).and("userId").is(cursor.getUserId()));

        Update update = new Update().set("lastReadMessageId", cursor.getLastReadMessageId())
                .set("lastReadAt", cursor.getLastReadAt()).setOnInsert("roomId", cursor.getRoomId())
                .setOnInsert("userId", cursor.getUserId());

        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), ReadCursor.class);
    }
}
