package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryEvent;
import com.example.inventoryservice.dto.OrderCreatedEvent;
import com.example.inventoryservice.kafka.InventoryEventProducer;
import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryEventProducer inventoryEventProducer;

    @Transactional
    public void reserveStock(OrderCreatedEvent event) {
        Product product = productRepository.findByIdForUpdate(event.getProductId()).orElse(null);

        if (product == null) {
            publishFailure(event, "Product " + event.getProductId() + " does not exist");
            return;
        }

        if (product.getStockQuantity() < event.getQuantity()) {
            publishFailure(event, "Insufficient stock for product " + product.getId()
                    + " (requested=" + event.getQuantity() + ", available=" + product.getStockQuantity() + ")");
            return;
        }

        product.setStockQuantity(product.getStockQuantity() - event.getQuantity());
        productRepository.save(product);

        log.info("Reserved {} units of product {} for order {}", event.getQuantity(), product.getId(), event.getOrderId());

        inventoryEventProducer.publish(
                InventoryEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())
                        .quantity(event.getQuantity())
                        .status("RESERVED")
                        .build()
        );
    }

    private void publishFailure(OrderCreatedEvent event, String reason) {
        log.warn("Reservation failed for order {}: {}", event.getOrderId(), reason);
        inventoryEventProducer.publish(
                InventoryEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())
                        .quantity(event.getQuantity())
                        .status("FAILED")
                        .reason(reason)
                        .build()
        );
    }
}
