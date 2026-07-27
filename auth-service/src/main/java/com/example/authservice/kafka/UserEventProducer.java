package com.example.authservice.kafka;

import com.example.authservice.dto.UserRegisteredEvent;
import com.example.authservice.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.user-events}")
    private String userEventsTopic;

    public void publishUserRegistered(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();

        kafkaTemplate.send(userEventsTopic, String.valueOf(user.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for userId={}", user.getId(), ex);
                    } else {
                        log.info("Published UserRegisteredEvent userId={} to partition={} offset={}",
                                user.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
