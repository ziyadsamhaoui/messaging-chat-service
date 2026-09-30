package com.ziyadsamhaoui.messagingchatservice.config;

import com.ziyadsamhaoui.messagingchatservice.outbox.OutboxRelay;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;


@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "badrlink.kafka", name = "enabled", havingValue = "true")
@Import(KafkaAutoConfiguration.class)
public class KafkaOutboxConfig {

    @Bean(name = "outboxKafkaTemplate")
    KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties();
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @Bean
    OutboxRelay outboxRelay(MongoTemplate mongoTemplate,
                            @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate,
                            ObjectMapper objectMapper,
                            KafkaOutboxProperties properties) {
        return new OutboxRelay(mongoTemplate, outboxKafkaTemplate, objectMapper, properties);
    }
}
