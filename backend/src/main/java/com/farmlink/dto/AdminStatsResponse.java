package com.farmlink.dto;

public record AdminStatsResponse(
        long users,
        long farmers,
        long buyers,
        long activeListings,
        long purchaseRequests,
        long pendingRequests) {
}
