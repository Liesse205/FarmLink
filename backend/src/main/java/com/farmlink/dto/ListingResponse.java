package com.farmlink.dto;

import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ListingResponse(
        Long id,
        Long farmerId,
        String farmerName,
        String name,
        String category,
        Integer quantity,
        BigDecimal pricePerUnit,
        String location,
        LocalDate availableFrom,
        String description,
        ListingStatus status) {

    public static ListingResponse from(ProduceListing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getFarmer().getId(),
                listing.getFarmer().getName(),
                listing.getName(),
                listing.getCategory(),
                listing.getQuantity(),
                listing.getPricePerUnit(),
                listing.getLocation(),
                listing.getAvailableFrom(),
                listing.getDescription(),
                listing.getStatus());
    }
}
