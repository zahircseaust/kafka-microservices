package com.example.notificationservice.config;

import com.example.notificationservice.dto.InventoryEvent;
import com.example.notificationservice.dto.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * This service listens to two topics carrying two unrelated event shapes
 * (InventoryEvent from inventory-service, UserRegisteredEvent from auth-service).
 * Spring Boot's auto-configured consumer factory only supports one default JSON
 * type, so here we build one ConsumerFactory + ListenerContainerFactory per event
 * type, and each @KafkaListener picks its factory via the `containerFactory` attribute.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    private <T> JsonDeserializer<T> jsonDeserializer(Class<T> targetType) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(targetType);
        // Ignore the type header the producer added (it names ITS OWN package's class,
        // e.g. com.example.inventoryservice.dto.InventoryEvent) and always deserialize
        // into this service's local copy of the class instead.
        deserializer.setRemoveTypeHeaders(true);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);
        return deserializer;
    }

    @Bean
    public ConsumerFactory<String, InventoryEvent> inventoryEventConsumerFactory() {
        Map<String, Object> props = baseConsumerProps("notification-service-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer(InventoryEvent.class)));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> inventoryEventListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryEventConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> userEventConsumerFactory() {
        Map<String, Object> props = baseConsumerProps("notification-service-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer(UserRegisteredEvent.class)));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> userEventListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userEventConsumerFactory());
        return factory;
    }
}
