package com.farmlink.service;

import com.farmlink.dto.ListingCreateRequest;
import com.farmlink.dto.ListingResponse;
import com.farmlink.entity.ActivityType;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.repository.ProduceListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ProduceListingRepository listingRepository;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private ListingService listingService;

    private final User farmer = User.builder()
            .id(1L)
            .name("Alice Farmer")
            .email("alice@example.com")
            .role(Role.FARMER)
            .build();

    @Test
    void farmerCanCreateAValidListing() {
        ListingCreateRequest request = new ListingCreateRequest(
                "Tomatoes", "Vegetables", 500, new BigDecimal("800"),
                "Kigali", LocalDate.now().plusDays(7), "Fresh farm tomatoes");

        when(listingRepository.save(any(ProduceListing.class))).thenAnswer(invocation -> {
            ProduceListing saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        ListingResponse response = listingService.create(request, farmer);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Tomatoes");
        assertThat(response.category()).isEqualTo("Vegetables");
        assertThat(response.quantity()).isEqualTo(500);
        assertThat(response.pricePerUnit()).isEqualByComparingTo(new BigDecimal("800"));
        assertThat(response.location()).isEqualTo("Kigali");
        assertThat(response.farmerId()).isEqualTo(1L);
        assertThat(response.farmerName()).isEqualTo("Alice Farmer");
        assertThat(response.status()).isEqualTo(ListingStatus.ACTIVE);

        verify(activityService).record(eq(ActivityType.LISTING_CREATED), eq(1L), eq(10L), contains("Tomatoes"));
    }
}
