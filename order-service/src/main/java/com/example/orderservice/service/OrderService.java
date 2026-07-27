package com.example.orderservice.service;

import com.example.orderservice.dto.OrderCreatedEvent;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public OrderResponse createOrder(OrderRequest request, String customerId) {
        Order order = Order.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .customerId(customerId)
                .status(OrderStatus.CREATED)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order {} created, publishing OrderCreatedEvent", saved.getId());

        // Note: for strict "no lost events" guarantees in production you'd use the
        // transactional outbox pattern here instead of publishing directly after commit.
        orderEventProducer.publishOrderCreated(
                OrderCreatedEvent.builder()
                        .orderId(saved.getId())
                        .productId(saved.getProductId())
                        .quantity(saved.getQuantity())
                        .customerId(saved.getCustomerId())
                        .build()
        );

        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public java.util.List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }
}
