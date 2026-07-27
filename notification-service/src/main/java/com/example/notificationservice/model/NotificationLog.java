package com.example.notificationservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notification_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // What triggered this notification: "ORDER" (from an InventoryEvent) or
    // "USER_REGISTRATION" (from a UserRegisteredEvent).
    @Column(nullable = false)
    private String eventType;

    // The orderId for ORDER events, or the userId for USER_REGISTRATION events.
    @Column(nullable = false)
    private Long referenceId;

    @Column(nullable = false)
    private String channel; // e.g. EMAIL, SMS, WHATSAPP

    @Column(nullable = false)
    private String status; // RESERVED / FAILED / REGISTERED

    @Column(length = 1000)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = Instant.now();
    }
}
