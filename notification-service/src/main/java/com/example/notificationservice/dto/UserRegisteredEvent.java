package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of auth-service's UserRegisteredEvent. Consumed from the "user-events" topic.
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
