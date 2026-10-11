package com.farmlink.service;

import com.farmlink.dto.AppEvent;
import com.farmlink.dto.PurchaseRequestCreateRequest;
import com.farmlink.dto.PurchaseRequestResponse;
import com.farmlink.entity.ActivityType;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.RequestStatus;
import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.exception.ApiException;
import com.farmlink.repository.ProduceListingRepository;
import com.farmlink.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestServiceTest {

    @Mock
    private PurchaseRequestRepository requestRepository;

    @Mock
    private ProduceListingRepository listingRepository;

    @Mock
    private ActivityService activityService;

    @Mock
    private AppEventPublisher eventPublisher;

    @InjectMocks
    private PurchaseRequestService purchaseRequestService;

    private User farmerA;
    private User farmerB;
    private User buyer;
    private User otherBuyer;
    private User admin;
    private ProduceListing listing;

    @BeforeEach
    void setUp() {
        farmerA = User.builder().id(1L).name("Alice").email("alice@example.com").role(Role.FARMER).build();
        farmerB = User.builder().id(2L).name("Bob").email("bob@example.com").role(Role.FARMER).build();
        buyer = User.builder().id(3L).name("Carol").email("carol@example.com").role(Role.BUYER).build();
        otherBuyer = User.builder().id(4L).name("Dan").email("dan@example.com").role(Role.BUYER).build();
        admin = User.builder().id(9L).name("Eve").email("eve@example.com").role(Role.ADMIN).build();

        listing = ProduceListing.builder()
                .id(10L)
                .farmer(farmerA)
                .name("Tomatoes")
                .category("Vegetables")
                .quantity(100)
                .pricePerUnit(new BigDecimal("800"))
                .location("Kigali")
                .availableFrom(LocalDate.now().plusDays(3))
                .description("Fresh tomatoes")
                .status(ListingStatus.ACTIVE)
                .build();
    }

    private PurchaseRequest pendingRequest() {
        return PurchaseRequest.builder()
                .id(50L)
                .listing(listing)
                .buyer(buyer)
                .quantity(40)
                .message("Please keep 40 kg for me")
                .status(RequestStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void buyerCannotRequestMoreProduceThanIsAvailable() {
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        PurchaseRequestCreateRequest request = new PurchaseRequestCreateRequest(10L, 150, "I need 150 kg");

        ApiException exception =
                assertThrows(ApiException.class, () -> purchaseRequestService.create(request, buyer));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(requestRepository, never()).save(any(PurchaseRequest.class));
        verify(eventPublisher, never()).publish(any(AppEvent.class));
    }

    @Test
    void onlyTheListingOwnerCanAcceptOrRejectARequest() {
        PurchaseRequest request = pendingRequest();
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        ApiException acceptException =
                assertThrows(ApiException.class, () -> purchaseRequestService.accept(request.getId(), farmerB));
        ApiException rejectException =
                assertThrows(ApiException.class, () -> purchaseRequestService.reject(request.getId(), farmerB));

        assertThat(acceptException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejectException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        verify(requestRepository, never()).save(any(PurchaseRequest.class));
    }

    @Test
    void aProcessedRequestCannotBeProcessedAgain() {
        PurchaseRequest request = pendingRequest();
        request.setStatus(RequestStatus.ACCEPTED);
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        ApiException exception =
                assertThrows(ApiException.class, () -> purchaseRequestService.accept(request.getId(), farmerA));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(requestRepository, never()).save(any(PurchaseRequest.class));
    }

    private PurchaseRequest requestWithStatus(RequestStatus status) {
        PurchaseRequest request = pendingRequest();
        request.setStatus(status);
        return request;
    }

    @Test
    void bothParticipantsCanSeeEachOthersContactDetailsOnceAccepted() {
        PurchaseRequest request = requestWithStatus(RequestStatus.ACCEPTED);
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        var forBuyer = purchaseRequestService.contact(50L, buyer);

        assertThat(forBuyer.viewerId()).isEqualTo(buyer.getId());
        assertThat(forBuyer.farmer().email()).isEqualTo("alice@example.com");
        assertThat(forBuyer.buyer().email()).isEqualTo("carol@example.com");
        assertThat(forBuyer.instruction()).contains("collection or delivery");

        var forFarmer = purchaseRequestService.contact(50L, farmerA);

        assertThat(forFarmer.viewerId()).isEqualTo(farmerA.getId());
        assertThat(forFarmer.farmer().name()).isEqualTo("Alice");
        assertThat(forFarmer.buyer().name()).isEqualTo("Carol");
    }

    @Test
    void contactDetailsAreHiddenWhileTheRequestIsPendingOrRejected() {
        for (RequestStatus status : new RequestStatus[]{RequestStatus.PENDING, RequestStatus.REJECTED}) {
            PurchaseRequest request = requestWithStatus(status);
            when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

            for (User actor : new User[]{buyer, farmerA}) {
                ApiException exception =
                        assertThrows(ApiException.class, () -> purchaseRequestService.contact(50L, actor));
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.getMessage()).contains("accepted");
            }
        }
    }

    @Test
    void nonParticipantsAreForbiddenFromContactDetails() {
        PurchaseRequest request = requestWithStatus(RequestStatus.ACCEPTED);
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        for (User actor : new User[]{farmerB, otherBuyer, admin}) {
            ApiException exception =
                    assertThrows(ApiException.class, () -> purchaseRequestService.contact(50L, actor));
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void contactDetailsAreGracefulWhenNoPhoneNumberWasAdded() {
        PurchaseRequest request = requestWithStatus(RequestStatus.ACCEPTED);
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        var contact = purchaseRequestService.contact(50L, buyer);

        assertThat(contact.farmer().phone()).isNull();
        assertThat(contact.buyer().phone()).isNull();
        assertThat(contact.farmer().email()).isNotBlank();
    }

    @Test
    void ownerCanAcceptAPendingRequestAndTheQuantityIsDeducted() {
        PurchaseRequest request = pendingRequest();
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));

        PurchaseRequestResponse response = purchaseRequestService.accept(50L, farmerA);

        assertThat(response.status()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(listing.getQuantity()).isEqualTo(60);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);

        verify(requestRepository).save(request);
        verify(listingRepository).save(listing);
        verify(activityService).record(
                org.mockito.ArgumentMatchers.eq(ActivityType.PURCHASE_REQUEST_ACCEPTED),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(50L),
                org.mockito.ArgumentMatchers.contains("Tomatoes"));

        ArgumentCaptor<AppEvent> event = ArgumentCaptor.forClass(AppEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ActivityType.PURCHASE_REQUEST_ACCEPTED);
        assertThat(event.getValue().targetUserId()).isEqualTo(buyer.getId());
    }
}
