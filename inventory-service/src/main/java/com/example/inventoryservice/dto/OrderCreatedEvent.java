package com.example.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of order-service's OrderCreatedEvent. Field-for-field identical JSON shape.
 * Consumed from the "order-events" topic.
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
