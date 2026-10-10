package com.farmlink.service;

import com.farmlink.dto.AdminStatsResponse;
import com.farmlink.dto.ListingResponse;
import com.farmlink.dto.UserResponse;
import com.farmlink.entity.ActivityEvent;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.RequestStatus;
import com.farmlink.entity.Role;
import com.farmlink.repository.ActivityRepository;
import com.farmlink.repository.ProduceListingRepository;
import com.farmlink.repository.PurchaseRequestRepository;
import com.farmlink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProduceListingRepository listingRepository;
    private final PurchaseRequestRepository requestRepository;
    private final ActivityRepository activityRepository;
    private final ListingService listingService;

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByRole(Role.FARMER),
                userRepository.countByRole(Role.BUYER),
                listingRepository.countByStatus(ListingStatus.ACTIVE),
                requestRepository.count(),
                requestRepository.countByStatus(RequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> recentActivity() {
        return activityRepository.findTop20ByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> listings() {
        return listingService.all();
    }
}
