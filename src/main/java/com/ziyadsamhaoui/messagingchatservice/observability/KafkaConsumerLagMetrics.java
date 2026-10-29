package com.ziyadsamhaoui.messagingchatservice.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "badrlink.kafka", name = "enabled", havingValue = "true")
@Slf4j
public class KafkaConsumerLagMetrics {

    static final String LAG_METRIC = "badrlink.kafka.consumer.lag";

    private static final long ADMIN_TIMEOUT_SECONDS = 10;

    private final AdminClient adminClient;
    private final MeterRegistry meterRegistry;
    private final String groupId;
    private final Map<String, AtomicLong> lagByTopic = new ConcurrentHashMap<>();

    public KafkaConsumerLagMetrics(KafkaProperties kafkaProperties,
                                   MeterRegistry meterRegistry,
                                   @Value("${badrlink.observability.kafka-lag.group-id}") String groupId) {
        this.adminClient = AdminClient.create(
                Map.of("bootstrap.servers", String.join(",", kafkaProperties.getBootstrapServers())));
        this.meterRegistry = meterRegistry;
        this.groupId = groupId;
    }

    @Scheduled(fixedDelayString = "${badrlink.observability.kafka-lag.refresh-interval:15s}")
    public void refresh() {
        try {
            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (committed.isEmpty()) {
                return;
            }

            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            committed.keySet().forEach(partition -> request.put(partition, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient
                    .listOffsets(request)
                    .all()
                    .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, Long> lagByTopic = new HashMap<>();
            committed.forEach((partition, offset) -> {
                ListOffsetsResult.ListOffsetsResultInfo end = endOffsets.get(partition);
                if (end == null || offset.offset() < 0) {
                    return;
                }
                long lag = Math.max(0, end.offset() - offset.offset());
                lagByTopic.merge(partition.topic(), lag, Long::sum);
            });
            lagByTopic.forEach((topic, lag) -> gaugeFor(topic).set(lag));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.debug("consumer lag refresh interrupted");
        } catch (Exception ex) {
            log.debug("could not refresh consumer lag for group {}: {}", groupId, ex.getMessage());
        }
    }

    private AtomicLong gaugeFor(String topic) {
        return lagByTopic.computeIfAbsent(topic, name -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(LAG_METRIC, value, AtomicLong::doubleValue)
                    .description("Consumer group lag (end offset minus committed offset) per topic")
                    .tags("topic", name, "group", groupId)
                    .register(meterRegistry);
            return value;
        });
    }

    @PreDestroy
    void close() {
        adminClient.close();
    }
}
