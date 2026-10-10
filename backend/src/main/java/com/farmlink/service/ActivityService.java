package com.farmlink.service;

import com.farmlink.entity.ActivityEvent;
import com.farmlink.entity.ActivityType;
import com.farmlink.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

    public void record(ActivityType type, Long userId, Long entityId, String description) {
        activityRepository.save(ActivityEvent.builder()
                .type(type)
                .userId(userId)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .description(description)
                .timestamp(Instant.now())
                .build());
    }
}
