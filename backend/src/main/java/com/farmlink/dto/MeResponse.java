package com.farmlink.dto;

import com.farmlink.entity.Role;
import com.farmlink.entity.User;

/**
 * The signed-in user's own profile. Deliberately separate from {@link UserResponse}
 * (used for the admin user list) so the optional phone number is only ever returned
 * to the user themselves — never to admins or other users.
 */
public record MeResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole());
    }
}
