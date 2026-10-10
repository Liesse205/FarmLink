package com.farmlink.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleSelectRequest(
        @NotBlank(message = "role is required")
        String role) {
}
