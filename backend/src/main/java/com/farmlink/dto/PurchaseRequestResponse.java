package com.farmlink.dto;

import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.RequestStatus;

import java.time.Instant;

public record PurchaseRequestResponse(
        Long id,
        Long listingId,
        String listingName,
        Long buyerId,
        String buyerName,
        Integer quantity,
        String message,
        RequestStatus status,
        Instant createdAt) {

    public static PurchaseRequestResponse from(PurchaseRequest request) {
        return new PurchaseRequestResponse(
                request.getId(),
                request.getListing().getId(),
                request.getListing().getName(),
                request.getBuyer().getId(),
                request.getBuyer().getName(),
                request.getQuantity(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt());
    }
}
