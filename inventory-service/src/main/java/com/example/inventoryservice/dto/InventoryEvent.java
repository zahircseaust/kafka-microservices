package com.example.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to the "inventory-events" topic after attempting to reserve stock.
 * Consumed by order-service (to update order status) and notification-service (to notify the customer).
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
    private String reason;
}
