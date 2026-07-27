package com.example.orderservice.kafka;

import com.example.orderservice.dto.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.order-events}")
    private String orderEventsTopic;

    public void publishOrderCreated(OrderCreatedEvent event) {
        // Key by orderId (as string) so all events for the same order land on the same partition,
        // preserving per-order ordering.
        String key = String.valueOf(event.getOrderId());
        kafkaTemplate.send(orderEventsTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for orderId={}", event.getOrderId(), ex);
                    } else {
                        log.info("Published OrderCreatedEvent orderId={} to partition={} offset={}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
