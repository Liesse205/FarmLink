package com.farmlink.service;

import com.farmlink.dto.ListingCreateRequest;
import com.farmlink.dto.ListingFacetsResponse;
import com.farmlink.dto.ListingResponse;
import com.farmlink.dto.ListingUpdateRequest;
import com.farmlink.entity.ActivityType;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.exception.ApiException;
import com.farmlink.repository.ProduceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ProduceListingRepository listingRepository;
    private final ActivityService activityService;

    @Transactional
    public ListingResponse create(ListingCreateRequest request, User farmer) {
        if (farmer.getRole() != Role.FARMER) {
            throw ApiException.forbidden("Only farmers can create listings");
        }
        ProduceListing listing = listingRepository.save(ProduceListing.builder()
                .farmer(farmer)
                .name(request.name())
                .category(request.category())
                .quantity(request.quantity())
                .pricePerUnit(request.pricePerUnit())
                .location(request.location())
                .availableFrom(request.availableFrom())
                .description(request.description())
                .status(ListingStatus.ACTIVE)
                .build());
        activityService.record(ActivityType.LISTING_CREATED, farmer.getId(), listing.getId(),
                "Listing '" + listing.getName() + "' created");
        return ListingResponse.from(listing);
    }

    @Transactional
    public ListingResponse update(Long id, ListingUpdateRequest request, User actor) {
        ProduceListing listing = find(id);
        boolean owner = listing.getFarmer().getId().equals(actor.getId());
        if (!owner && actor.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("You can only update your own listings");
        }
        listing.setName(request.name());
        listing.setCategory(request.category());
        listing.setQuantity(request.quantity());
        listing.setPricePerUnit(request.pricePerUnit());
        listing.setLocation(request.location());
        listing.setAvailableFrom(request.availableFrom());
        listing.setDescription(request.description());
        if (request.status() != null) {
            listing.setStatus(request.status());
        }
        listingRepository.save(listing);
        activityService.record(ActivityType.LISTING_UPDATED, actor.getId(), listing.getId(),
                "Listing '" + listing.getName() + "' updated");
        return ListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    public ListingResponse get(Long id) {
        return ListingResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> search(String search, String category, String location) {
        return listingRepository.search(ListingStatus.ACTIVE, blankToEmpty(search), blankToEmpty(category),
                        blankToEmpty(location)).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ListingFacetsResponse facets() {
        return new ListingFacetsResponse(
                listingRepository.findCategoriesByStatus(ListingStatus.ACTIVE),
                listingRepository.findLocationsByStatus(ListingStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> mine(User farmer) {
        return listingRepository.findByFarmerIdOrderByAvailableFromAsc(farmer.getId()).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> all() {
        return listingRepository.findAll().stream()
                .map(ListingResponse::from)
                .toList();
    }

    private ProduceListing find(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Listing " + id + " not found"));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
