package com.farmlink.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PurchaseRequestCreateRequest(
        @NotNull(message = "listingId is required")
        Long listingId,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity,
        @Size(max = 500, message = "message must be at most 500 characters")
        String message) {
}
