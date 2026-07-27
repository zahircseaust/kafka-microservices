package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.InventoryEvent;
import com.example.notificationservice.model.NotificationLog;
import com.example.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final NotificationLogRepository notificationLogRepository;

    @KafkaListener(topics = "${app.kafka.topic.inventory-events}", containerFactory = "inventoryEventListenerFactory")
    public void onInventoryEvent(InventoryEvent event) {
        String message = "RESERVED".equalsIgnoreCase(event.getStatus())
                ? "Your order #" + event.getOrderId() + " is confirmed and being processed."
                : "Your order #" + event.getOrderId() + " could not be fulfilled: " + event.getReason();

        // Simulates sending an email/WhatsApp/SMS notification - in a real system this
        // would call an email/WhatsApp gateway (this mirrors the CRM automation pattern
        // of triggering messages off a lead/order-stage change).
        log.info("Sending notification for order {}: {}", event.getOrderId(), message);

        NotificationLog entry = NotificationLog.builder()
                .eventType("ORDER")
                .referenceId(event.getOrderId())
                .channel("EMAIL")
                .status(event.getStatus())
                .message(message)
                .build();

        notificationLogRepository.save(entry);
    }
}
