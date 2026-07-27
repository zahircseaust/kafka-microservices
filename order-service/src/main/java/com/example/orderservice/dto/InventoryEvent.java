package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event consumed from the "inventory-events" Kafka topic.
 * Published by inventory-service after it attempts to reserve stock for an order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String status; // "RESERVED" or "FAILED"
    private String reason; // populated when status = FAILED
}
