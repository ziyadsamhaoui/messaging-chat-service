package com.ziyadsamhaoui.messagingchatservice.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class BlockCacheService {

    public static final String COLLECTION = "block_cache";

    private final MongoTemplate mongoTemplate;

    public void recordBlocked(String blockerId, String blockedId) {
        BlockCacheEntry existing = findByPair(blockerId, blockedId);
        if (existing != null) {
            return;
        }
        mongoTemplate.insert(BlockCacheEntry.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .build(), COLLECTION);
        log.debug("block_cache + {} -> {}", blockerId, blockedId);
    }

    public void recordUnblocked(String blockerId, String blockedId) {
        Query query = Query.query(Criteria.where("blockerId").is(blockerId).and("blockedId").is(blockedId));
        mongoTemplate.remove(query, COLLECTION);
        log.debug("block_cache - {} -> {}", blockerId, blockedId);
    }

    public boolean isKnownBlocked(String firstUserId, String secondUserId) {
        return findByPair(firstUserId, secondUserId) != null
                || findByPair(secondUserId, firstUserId) != null;
    }

    private BlockCacheEntry findByPair(String blockerId, String blockedId) {
        Query query = Query.query(Criteria.where("blockerId").is(blockerId).and("blockedId").is(blockedId));
        return mongoTemplate.findOne(query, BlockCacheEntry.class, COLLECTION);
    }
}
