package com.example.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topic.inventory-events}")
    private String inventoryEventsTopic;

    // order-service publishes here
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // order-service also consumes this one (declared here too so it exists even
    // if inventory-service hasn't started yet)
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(inventoryEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
