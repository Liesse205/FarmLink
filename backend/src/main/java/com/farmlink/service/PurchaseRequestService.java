package com.farmlink.service;

import com.farmlink.dto.AppEvent;
import com.farmlink.dto.PurchaseRequestCreateRequest;
import com.farmlink.dto.PurchaseRequestResponse;
import com.farmlink.dto.RequestContactResponse;
import com.farmlink.entity.ActivityType;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.RequestStatus;
import com.farmlink.entity.User;
import com.farmlink.exception.ApiException;
import com.farmlink.repository.ProduceListingRepository;
import com.farmlink.repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private final PurchaseRequestRepository requestRepository;
    private final ProduceListingRepository listingRepository;
    private final ActivityService activityService;
    private final AppEventPublisher eventPublisher;

    @Transactional
    public PurchaseRequestResponse create(PurchaseRequestCreateRequest request, User buyer) {
        ProduceListing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> ApiException.notFound("Listing " + request.listingId() + " not found"));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw ApiException.conflict("Listing is not active");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw ApiException.badRequest("Quantity must be greater than 0");
        }
        if (request.quantity() > listing.getQuantity()) {
            throw ApiException.badRequest("Requested quantity " + request.quantity()
                    + " exceeds the available quantity of " + listing.getQuantity());
        }

        PurchaseRequest purchaseRequest = requestRepository.save(PurchaseRequest.builder()
                .listing(listing)
                .buyer(buyer)
                .quantity(request.quantity())
                .message(request.message())
                .status(RequestStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        activityService.record(ActivityType.PURCHASE_REQUEST_CREATED, buyer.getId(), purchaseRequest.getId(),
                "Buyer requested " + purchaseRequest.getQuantity() + " of '" + listing.getName() + "'");
        eventPublisher.publish(new AppEvent(
                ActivityType.PURCHASE_REQUEST_CREATED,
                purchaseRequest.getId(),
                listing.getId(),
                listing.getFarmer().getId(),
                "New purchase request for your " + listing.getName() + " listing.",
                Instant.now()));
        return PurchaseRequestResponse.from(purchaseRequest);
    }

    @Transactional
    public PurchaseRequestResponse accept(Long id, User farmer) {
        PurchaseRequest request = find(id);
        ensureOwner(request, farmer);
        ensurePending(request);

        ProduceListing listing = request.getListing();
        if (request.getQuantity() > listing.getQuantity()) {
            throw ApiException.conflict("Requested quantity is no longer available");
        }
        listing.setQuantity(listing.getQuantity() - request.getQuantity());
        if (listing.getQuantity() == 0) {
            listing.setStatus(ListingStatus.INACTIVE);
        }
        request.setStatus(RequestStatus.ACCEPTED);
        listingRepository.save(listing);
        requestRepository.save(request);

        activityService.record(ActivityType.PURCHASE_REQUEST_ACCEPTED, farmer.getId(), request.getId(),
                "Request for " + request.getQuantity() + " of '" + listing.getName() + "' accepted");
        eventPublisher.publish(new AppEvent(
                ActivityType.PURCHASE_REQUEST_ACCEPTED,
                request.getId(),
                listing.getId(),
                request.getBuyer().getId(),
                "Your purchase request for " + request.getQuantity() + " of '" + listing.getName() + "' has been accepted.",
                Instant.now()));
        return PurchaseRequestResponse.from(request);
    }

    @Transactional
    public PurchaseRequestResponse reject(Long id, User farmer) {
        PurchaseRequest request = find(id);
        ensureOwner(request, farmer);
        ensurePending(request);

        request.setStatus(RequestStatus.REJECTED);
        requestRepository.save(request);

        activityService.record(ActivityType.PURCHASE_REQUEST_REJECTED, farmer.getId(), request.getId(),
                "Request for " + request.getQuantity() + " of '" + request.getListing().getName() + "' rejected");
        eventPublisher.publish(new AppEvent(
                ActivityType.PURCHASE_REQUEST_REJECTED,
                request.getId(),
                request.getListing().getId(),
                request.getBuyer().getId(),
                "Your purchase request for " + request.getQuantity() + " of '"
                        + request.getListing().getName() + "' has been rejected.",
                Instant.now()));
        return PurchaseRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestResponse> list(User user) {
        if (user.getRole() == null) {

            return List.of();
        }
        List<PurchaseRequest> requests = switch (user.getRole()) {
            case FARMER -> requestRepository.findByListingFarmerIdOrderByCreatedAtDesc(user.getId());
            case BUYER -> requestRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId());
            case ADMIN -> requestRepository.findAll();
        };
        return requests.stream().map(PurchaseRequestResponse::from).toList();
    }

    /**
     * Contact details of the two participants of an <b>ACCEPTED</b> purchase request.
     * Enforced here in the service layer (not just the frontend): only the buyer and
     * the farmer who owns the listing may see each other's details, and only once the
     * request has been accepted. Everyone else — including admins and other farmers —
     * receives 403; pending and rejected requests share nothing.
     */
    @Transactional(readOnly = true)
    public RequestContactResponse contact(Long id, User actor) {
        PurchaseRequest request = find(id);
        User farmer = request.getListing().getFarmer();
        User buyer = request.getBuyer();

        boolean isParticipant = farmer.getId().equals(actor.getId()) || buyer.getId().equals(actor.getId());
        if (!isParticipant) {
            throw ApiException.forbidden("Only the buyer and the listing owner can view contact details "
                    + "for this request");
        }
        if (request.getStatus() != RequestStatus.ACCEPTED) {
            throw ApiException.forbidden("Contact details are shared only once a request has been accepted");
        }
        return RequestContactResponse.from(request, actor.getId());
    }

    private PurchaseRequest find(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase request " + id + " not found"));
    }

    private void ensureOwner(PurchaseRequest request, User actor) {
        if (!request.getListing().getFarmer().getId().equals(actor.getId())) {
            throw ApiException.forbidden("Only the listing owner can process this request");
        }
    }

    private void ensurePending(PurchaseRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw ApiException.conflict("Request has already been processed");
        }
    }
}
