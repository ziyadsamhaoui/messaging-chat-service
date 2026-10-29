package com.ziyadsamhaoui.messagingchatservice.observability;

import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class OutboxBacklogMetrics {

    static final String UNPUBLISHED_METRIC = "badrlink.outbox.unpublished";
    static final String OLDEST_AGE_METRIC = "badrlink.outbox.oldest.age.seconds";

    private final MongoTemplate mongoTemplate;
    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxBacklogMetrics(MongoTemplate mongoTemplate, MeterRegistry meterRegistry) {
        this.mongoTemplate = mongoTemplate;
        Gauge.builder(UNPUBLISHED_METRIC, unpublished, AtomicLong::doubleValue)
                .description("Outbox documents that have not been published to Kafka yet")
                .register(meterRegistry);
        Gauge.builder(OLDEST_AGE_METRIC, oldestAgeSeconds, AtomicLong::doubleValue)
                .description("Age in seconds of the oldest unpublished outbox document")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${badrlink.observability.outbox.refresh-interval:15s}")
    public void refresh() {
        try {
            Query unpublishedQuery = Query.query(Criteria.where("publishedAt").isNull());
            unpublished.set(mongoTemplate.count(unpublishedQuery, OutboxEvent.COLLECTION));

            Query oldestQuery = Query.query(Criteria.where("publishedAt").isNull())
                    .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                    .limit(1);
            OutboxEvent oldest = mongoTemplate.findOne(oldestQuery, OutboxEvent.class, OutboxEvent.COLLECTION);
            oldestAgeSeconds.set(oldest == null || oldest.getCreatedAt() == null
                    ? 0
                    : Math.max(0, Duration.between(oldest.getCreatedAt(), Instant.now()).getSeconds()));
        } catch (RuntimeException ex) {
            log.warn("could not refresh outbox backlog metrics: {}", ex.getMessage());
        }
    }

    long unpublishedCount() {
        return unpublished.get();
    }

    long oldestAgeSeconds() {
        return oldestAgeSeconds.get();
    }
}
