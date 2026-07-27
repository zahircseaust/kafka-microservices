package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.UserRegisteredEvent;
import com.example.notificationservice.model.NotificationLog;
import com.example.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final NotificationLogRepository notificationLogRepository;

    @KafkaListener(topics = "${app.kafka.topic.user-events}", containerFactory = "userEventListenerFactory")
    public void onUserRegistered(UserRegisteredEvent event) {
        String message = "Welcome " + event.getUsername() + "! Your account (" + event.getEmail() + ") is ready.";

        log.info("Sending welcome notification to userId={}: {}", event.getUserId(), message);

        NotificationLog entry = NotificationLog.builder()
                .eventType("USER_REGISTRATION")
                .referenceId(event.getUserId())
                .channel("EMAIL")
                .status("REGISTERED")
                .message(message)
                .build();

        notificationLogRepository.save(entry);
    }
}
