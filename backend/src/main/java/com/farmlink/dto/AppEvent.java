package com.farmlink.dto;

import com.farmlink.entity.ActivityType;

import java.time.Instant;

public record AppEvent(
        ActivityType type,
        Long requestId,
        Long listingId,
        Long targetUserId,
        String message,
        Instant timestamp) {
}
