package com.farmlink.controller;

import com.farmlink.dto.AdminStatsResponse;
import com.farmlink.dto.ListingResponse;
import com.farmlink.dto.UserResponse;
import com.farmlink.entity.ActivityEvent;
import com.farmlink.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/activity")
    public List<ActivityEvent> activity() {
        return adminService.recentActivity();
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.users();
    }

    @GetMapping("/listings")
    public List<ListingResponse> listings() {
        return adminService.listings();
    }
}
