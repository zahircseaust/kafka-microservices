package com.example.inventoryservice.kafka;

import com.example.inventoryservice.dto.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.inventory-events}")
    private String inventoryEventsTopic;

    public void publish(InventoryEvent event) {
        String key = String.valueOf(event.getOrderId());
        kafkaTemplate.send(inventoryEventsTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryEvent for orderId={}", event.getOrderId(), ex);
                    } else {
                        log.info("Published InventoryEvent orderId={} status={} to partition={} offset={}",
                                event.getOrderId(), event.getStatus(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
