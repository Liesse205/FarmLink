package com.farmlink.service;

import com.farmlink.config.RabbitConfig;
import com.farmlink.dto.AppEvent;
import com.farmlink.dto.NotificationResponse;
import com.farmlink.entity.Notification;
import com.farmlink.entity.User;
import com.farmlink.repository.NotificationRepository;
import com.farmlink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onAppEvent(AppEvent event) {
        if (event == null || event.targetUserId() == null) {
            return;
        }
        User target = userRepository.findById(event.targetUserId()).orElse(null);
        if (target == null) {
            log.warn("Dropping event {}: target user {} does not exist", event.type(), event.targetUserId());
            return;
        }
        Notification notification = notificationRepository.save(Notification.builder()
                .user(target)
                .message(event.message())
                .createdAt(Instant.now())
                .build());
        log.info("Notification #{} for user {}: {}", notification.getId(), target.getId(), notification.getMessage());
    }

    public List<NotificationResponse> listFor(User user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
