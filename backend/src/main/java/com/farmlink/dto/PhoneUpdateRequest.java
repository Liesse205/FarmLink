package com.farmlink.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/me/phone}. The phone number is optional: a null or blank
 * value clears it, any other value is stored trimmed (max 30 characters).
 */
public record PhoneUpdateRequest(
        @Size(max = 30, message = "phone number must be at most 30 characters") String phone) {
}
