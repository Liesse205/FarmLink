package com.farmlink.dto;

import com.farmlink.entity.Role;
import com.farmlink.entity.User;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
