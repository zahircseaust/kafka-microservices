package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to the "user-events" topic whenever a new user registers.
 * Consumed by notification-service to send a welcome message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisteredEvent {
    private Long userId;
    private String username;
    private String email;
}
