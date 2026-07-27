package com.example.orderservice.kafka;

import com.example.orderservice.dto.InventoryEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "${app.kafka.topic.inventory-events}", groupId = "order-service-group")
    @Transactional
    public void onInventoryEvent(InventoryEvent event) {
        log.info("Received InventoryEvent for orderId={} status={}", event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("No order found for orderId={} - ignoring event", event.getOrderId());
            return;
        }

        // Idempotency guard: if we've already moved this order to a terminal state, don't reprocess.
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} already in terminal state {}, skipping duplicate event", order.getId(), order.getStatus());
            return;
        }

        if ("RESERVED".equalsIgnoreCase(event.getStatus())) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
        }
        orderRepository.save(order);
        log.info("Order {} updated to status {}", order.getId(), order.getStatus());
    }
}
