package com.farmlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ListingCreateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,
        @NotBlank(message = "category is required")
        @Size(max = 80, message = "category must be at most 80 characters")
        String category,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity,
        @NotNull(message = "pricePerUnit is required")
        @Positive(message = "pricePerUnit must be greater than 0")
        BigDecimal pricePerUnit,
        @NotBlank(message = "location is required")
        @Size(max = 120, message = "location must be at most 120 characters")
        String location,
        @NotNull(message = "availableFrom is required")
        LocalDate availableFrom,
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description) {
}
