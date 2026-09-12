package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryEvent;
import com.example.inventoryservice.dto.OrderCreatedEvent;
import com.example.inventoryservice.dto.ProductRequest;
import com.example.inventoryservice.dto.ProductResponse;
import com.example.inventoryservice.exception.ProductNotFoundException;
import com.example.inventoryservice.kafka.InventoryEventProducer;
import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.inventoryservice.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryEventProducer inventoryEventProducer;
    private final Util util;

    // =========================================================
    // Kafka-driven stock reservation (order-events consumer)
    // =========================================================

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

    // =========================================================
    // CRUD operations (REST API - ProductController)
    // =========================================================

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        Product product = Product.builder()
                .name(request.getName())
                .stockQuantity(request.getStockQuantity())
                .build();

        Product savedProduct = productRepository.save(product);

        log.info("Created product {} with initial stock {}",
                savedProduct.getId(),
                savedProduct.getStockQuantity());

        return util.mapToResponse(savedProduct);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.getName());
        product.setStockQuantity(request.getStockQuantity());

        log.info("Updating product {}: name={}, stockQuantity={}", id, request.getName(), request.getStockQuantity());
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        log.info("Deleting product {}", id);
        productRepository.deleteById(id);
    }
}