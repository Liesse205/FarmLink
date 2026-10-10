package com.farmlink.service;

import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.exception.ApiException;
import com.farmlink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${farmlink.farmer-emails:}")
    private String farmerEmails;

    @Value("${farmlink.admin-emails:}")
    private String adminEmails;

    @Transactional
    public User upsertGoogleUser(String name, String email) {
        User user = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                        .name(name)
                        .email(email)
                        .role(initialRole(email))
                        .build()));

        if (user.getRole() != Role.ADMIN && contains(adminEmails, email)) {
            user.setRole(Role.ADMIN);
            return userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public User selectRole(Principal principal, String roleName) {
        User user = getCurrentUser(principal);
        if (user.getRole() != null) {
            throw ApiException.conflict("Your role is already set to " + user.getRole());
        }

        Role role;
        try {
            role = Role.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("Unknown role '" + roleName + "'. Choose FARMER or BUYER.");
        }
        if (role == Role.ADMIN) {
            throw ApiException.badRequest("Admin accounts are assigned by the administrators and cannot be chosen here.");
        }

        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(Principal principal) {
        String email = emailOf(principal);
        if (email == null || email.isBlank()) {
            throw ApiException.unauthorized("Login required");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("Unknown user: " + email));
    }

    private String emailOf(Principal principal) {
        if (principal instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttribute("email");
            return email == null ? null : email.toString();
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal == null ? null : principal.getName();
    }

    private Role initialRole(String email) {
        if (contains(adminEmails, email)) {
            return Role.ADMIN;
        }
        if (contains(farmerEmails, email)) {
            return Role.FARMER;
        }
        return null;
    }

    private boolean contains(String configuredEmails, String email) {
        if (configuredEmails == null || configuredEmails.isBlank()) {
            return false;
        }
        for (String configured : configuredEmails.split(",")) {
            if (configured.trim().equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }
}
