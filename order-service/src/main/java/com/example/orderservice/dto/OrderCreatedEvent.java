package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published to the "order-events" Kafka topic whenever a new order is created.
 * Consumed by inventory-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String customerId;
}
