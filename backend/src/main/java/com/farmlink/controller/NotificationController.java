package com.farmlink.controller;

import com.farmlink.dto.NotificationResponse;
import com.farmlink.service.NotificationService;
import com.farmlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public List<NotificationResponse> list(Principal principal) {
        return notificationService.listFor(userService.getCurrentUser(principal));
    }
}
