package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of inventory-service's InventoryEvent. Consumed from the "inventory-events" topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String status;
    private String reason;
}
