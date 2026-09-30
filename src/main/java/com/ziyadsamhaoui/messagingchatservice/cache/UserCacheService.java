package com.ziyadsamhaoui.messagingchatservice.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;


@Component
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    public static final String COLLECTION = "user_cache";

    private final MongoTemplate mongoTemplate;

    public void upsert(String userId, String username, Instant updatedAt) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("username", username)
                .set("updatedAt", updatedAt);
        mongoTemplate.upsert(query, update, UserCacheEntry.class, COLLECTION);
        log.debug("user_cache updated: {} -> {}", userId, username);
    }

    public Optional<String> lookup(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        return Optional.ofNullable(mongoTemplate.findOne(query, UserCacheEntry.class, COLLECTION))
                .map(UserCacheEntry::getUsername);
    }
}
