package com.example.inventoryservice.kafka;

import com.example.inventoryservice.dto.OrderCreatedEvent;
import com.example.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "${app.kafka.topic.order-events}", groupId = "inventory-service-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent orderId={} productId={} qty={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());
        inventoryService.reserveStock(event);
    }
}
