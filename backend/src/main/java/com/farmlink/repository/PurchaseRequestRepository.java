package com.farmlink.repository;

import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    List<PurchaseRequest> findByListingFarmerIdOrderByCreatedAtDesc(Long farmerId);

    List<PurchaseRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    long countByStatus(RequestStatus status);
}
