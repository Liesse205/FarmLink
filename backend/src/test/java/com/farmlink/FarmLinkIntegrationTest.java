package com.farmlink;

import com.farmlink.entity.ActivityEvent;
import com.farmlink.entity.ActivityType;
import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.RequestStatus;
import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.repository.ActivityRepository;
import com.farmlink.repository.NotificationRepository;
import com.farmlink.repository.ProduceListingRepository;
import com.farmlink.repository.PurchaseRequestRepository;
import com.farmlink.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "farmlink.integration", matches = "true")
class FarmLinkIntegrationTest {

    private static final String FARMER_EMAIL = "it-farmer@example.com";
    private static final String FARMER2_EMAIL = "it-farmer2@example.com";
    private static final String BUYER_EMAIL = "it-buyer@example.com";
    private static final String ADMIN_EMAIL = "it-admin@example.com";
    private static final String PENDING_EMAIL = "it-pending@example.com";

    private static final String LISTING_JSON = """
            {"name":"Tomatoes","category":"Vegetables","quantity":%d,"pricePerUnit":800,
             "location":"Kigali","availableFrom":"2026-10-10","description":"Fresh farm tomatoes"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProduceListingRepository listingRepository;
    @Autowired
    private PurchaseRequestRepository requestRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private User farmer;
    private User farmer2;
    private User buyer;
    private User pending;

    @BeforeEach
    void seed() {
        cleanup();
        farmer = userRepository.save(User.builder().name("IT Farmer").email(FARMER_EMAIL).role(Role.FARMER).build());
        farmer2 = userRepository.save(User.builder().name("IT Farmer 2").email(FARMER2_EMAIL).role(Role.FARMER).build());
        buyer = userRepository.save(User.builder().name("IT Buyer").email(BUYER_EMAIL).role(Role.BUYER).build());
        userRepository.save(User.builder().name("IT Admin").email(ADMIN_EMAIL).role(Role.ADMIN).build());

        pending = userRepository.save(User.builder().name("IT Pending").email(PENDING_EMAIL).role(null).build());
    }

    @AfterEach
    void cleanUpAfterTest() {
        cleanup();
    }

    @Test
    void marketplaceIsPublicButProtectedEndpointsNeedLogin() throws Exception {
        mockMvc.perform(get("/api/listings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/listings?search=tom&category=Vegetables")).andExpect(status().isOk());

        mockMvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                .content(LISTING_JSON.formatted(500))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\":1,\"quantity\":5}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = BUYER_EMAIL, authorities = "ROLE_BUYER")
    void buyerIsForbiddenFromFarmerAndAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/listings/mine")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                .content(LISTING_JSON.formatted(500))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/activity")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = FARMER_EMAIL, authorities = "ROLE_FARMER")
    void farmerCanCreateAValidListingAndItIsLoggedInMongoDb() throws Exception {
        mockMvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                .content(LISTING_JSON.formatted(500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.farmerId").value(farmer.getId()))
                .andExpect(jsonPath("$.name").value("Tomatoes"));

        mockMvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                .content(LISTING_JSON.formatted(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("quantity")));

        mockMvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"category\":\"Vegetables\",\"quantity\":10,\"pricePerUnit\":10,"
                        + "\"location\":\"Kigali\",\"availableFrom\":\"2026-10-10\"}"))
                .andExpect(status().isBadRequest());

        List<ActivityEvent> created = activityEventsOfType(ActivityType.LISTING_CREATED);
        assertThat(created).isNotEmpty();
        assertThat(created.get(0).getUserId()).isEqualTo(farmer.getId());
        assertThat(created.get(0).getDescription()).contains("Tomatoes");
    }

    @Test
    @WithMockUser(username = BUYER_EMAIL, authorities = "ROLE_BUYER")
    void buyerCannotRequestMoreProduceThanIsAvailable() throws Exception {
        ProduceListing listing = seedListing(100);

        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\":" + listing.getId() + ",\"quantity\":150,\"message\":\"too much\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("available quantity")));

        MvcResult created = mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":" + listing.getId() + ",\"quantity\":40,"
                                + "\"message\":\"please keep 40 kg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.listingName").value("Tomatoes"))
                .andReturn();
        assertThat(createdRequestId(created)).isNotNull();

        assertThat(activityEventsOfType(ActivityType.PURCHASE_REQUEST_CREATED)).isNotEmpty();
        assertThat(waitForNotifications(farmer.getId(), 1))
                .as("farmer notification delivered via RabbitMQ after a request is created")
                .isTrue();
    }

    @Test
    @WithMockUser(username = BUYER_EMAIL, authorities = "ROLE_BUYER")
    void buyersCannotProcessRequests() throws Exception {
        ProduceListing listing = seedListing(100);
        Long requestId = createRequestAsBuyer(listing, 40);

        mockMvc.perform(put("/api/requests/" + requestId + "/accept"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/requests/" + requestId + "/reject"))
                .andExpect(status().isForbidden());

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @WithMockUser(username = FARMER2_EMAIL, authorities = "ROLE_FARMER")
    void onlyTheListingOwnerCanProcessARequest() throws Exception {
        ProduceListing listing = seedListing(100);
        Long requestId = seedRequest(listing, 40);

        mockMvc.perform(put("/api/requests/" + requestId + "/accept"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/requests/" + requestId + "/reject"))
                .andExpect(status().isForbidden());

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @WithMockUser(username = FARMER_EMAIL, authorities = "ROLE_FARMER")
    void ownerProcessesRequestsAndBuyerIsNotified() throws Exception {
        ProduceListing listing = seedListing(100);
        Long acceptedId = seedRequest(listing, 40);
        Long rejectedId = seedRequest(listing, 10);

        mockMvc.perform(put("/api/requests/" + acceptedId + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(listingRepository.findById(listing.getId()).orElseThrow().getQuantity()).isEqualTo(60);

        mockMvc.perform(put("/api/requests/" + acceptedId + "/accept"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/requests/" + acceptedId + "/reject"))
                .andExpect(status().isConflict());
        assertThat(requestRepository.findById(acceptedId).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.ACCEPTED);

        mockMvc.perform(put("/api/requests/" + rejectedId + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(activityEventsOfType(ActivityType.PURCHASE_REQUEST_ACCEPTED)).isNotEmpty();
        assertThat(activityEventsOfType(ActivityType.PURCHASE_REQUEST_REJECTED)).isNotEmpty();

        assertThat(waitForNotifications(buyer.getId(), 2))
                .as("buyer notifications delivered via RabbitMQ")
                .isTrue();
    }

    @Test
    void contactDetailsAreOnlySharedWithParticipantsOfAnAcceptedRequest() throws Exception {
        ProduceListing listing = seedListing(100);
        Long requestId = seedRequest(listing, 40);

        // hidden from everyone while the request is pending
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(BUYER_EMAIL).roles("BUYER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/" + requestId + "/contact"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/requests/" + requestId + "/accept")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk());

        // both participants now see each other, with the coordination instruction
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(BUYER_EMAIL).roles("BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerId").value(buyer.getId()))
                .andExpect(jsonPath("$.farmer.email").value(FARMER_EMAIL))
                .andExpect(jsonPath("$.farmer.name").value("IT Farmer"))
                .andExpect(jsonPath("$.buyer.email").value(BUYER_EMAIL))
                .andExpect(jsonPath("$.instruction").value(containsString("collection or delivery")));
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyer.email").value(BUYER_EMAIL))
                .andExpect(jsonPath("$.buyer.name").value("IT Buyer"))
                .andExpect(jsonPath("$.farmer.phone").value(nullValue()));

        // third parties stay locked out — including admins and unauthenticated callers
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(FARMER2_EMAIL).roles("FARMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/" + requestId + "/contact"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contactDetailsStayHiddenForRejectedRequests() throws Exception {
        ProduceListing listing = seedListing(100);
        Long rejectedId = seedRequest(listing, 20);

        mockMvc.perform(put("/api/requests/" + rejectedId + "/reject")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + rejectedId + "/contact")
                        .with(user(BUYER_EMAIL).roles("BUYER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/" + rejectedId + "/contact")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void contactDetailsNeverAppearInPublicOrUnrelatedResponses() throws Exception {
        ProduceListing listing = seedListing(100);
        Long acceptedId = seedRequest(listing, 30);
        mockMvc.perform(put("/api/requests/" + acceptedId + "/accept")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk());

        // public listing detail carries no e-mail or phone data
        String listingBody = mockMvc.perform(get("/api/listings/" + listing.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listingBody).doesNotContain(FARMER_EMAIL).doesNotContain("phone");

        // a buyer's own request list carries no contact fields
        String requestsBody = mockMvc.perform(get("/api/requests")
                        .with(user(BUYER_EMAIL).roles("BUYER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(requestsBody).doesNotContain("\"email\"").doesNotContain("phone");

        // the admin user list has no phone numbers
        String usersBody = mockMvc.perform(get("/api/admin/users")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(usersBody).doesNotContain("phone");
    }

    @Test
    void buyersCanAddAnOptionalPhoneThatIsValidatedAndClearable() throws Exception {
        mockMvc.perform(put("/api/me/phone").with(user(BUYER_EMAIL).roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\" +250 788 123 456 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+250 788 123 456"));

        mockMvc.perform(get("/api/me").with(user(BUYER_EMAIL).roles("BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+250 788 123 456"));

        // over-long numbers are rejected with a field-level 400
        mockMvc.perform(put("/api/me/phone").with(user(BUYER_EMAIL).roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"1234567890123456789012345678901\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("phone")));

        // a blank value clears the number again
        mockMvc.perform(put("/api/me/phone").with(user(BUYER_EMAIL).roles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(nullValue()));

        mockMvc.perform(put("/api/me/phone"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = BUYER_EMAIL, authorities = "ROLE_BUYER")
    void acceptedRequestSharesEachSidesContactIncludingOptionalPhone() throws Exception {
        mockMvc.perform(put("/api/me/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+250788123456\"}"))
                .andExpect(status().isOk());

        ProduceListing listing = seedListing(100);
        Long requestId = createRequestAsBuyer(listing, 25);

        // still pending → nothing is shared
        mockMvc.perform(get("/api/requests/" + requestId + "/contact"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/requests/" + requestId + "/accept")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk());

        // the buyer sees the farmer (no phone on file → null, handled gracefully)
        mockMvc.perform(get("/api/requests/" + requestId + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmer.email").value(FARMER_EMAIL))
                .andExpect(jsonPath("$.farmer.phone").value(nullValue()))
                .andExpect(jsonPath("$.buyer.phone").value("+250788123456"))
                .andExpect(jsonPath("$.instruction").isNotEmpty());

        // the farmer sees the buyer's phone too
        mockMvc.perform(get("/api/requests/" + requestId + "/contact")
                        .with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyer.email").value(BUYER_EMAIL))
                .andExpect(jsonPath("$.buyer.phone").value("+250788123456"));
    }

    private ProduceListing seedListing(int quantity) {
        return listingRepository.save(ProduceListing.builder()
                .farmer(farmer)
                .name("Tomatoes")
                .category("Vegetables")
                .quantity(quantity)
                .pricePerUnit(new BigDecimal("800"))
                .location("Kigali")
                .availableFrom(LocalDate.of(2026, 10, 10))
                .description("Fresh farm tomatoes")
                .status(ListingStatus.ACTIVE)
                .build());
    }

    private Long seedRequest(ProduceListing listing, int quantity) {
        return requestRepository.save(PurchaseRequest.builder()
                .listing(listing)
                .buyer(buyer)
                .quantity(quantity)
                .message("seeded request")
                .status(RequestStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .build()).getId();
    }

    private Long createRequestAsBuyer(ProduceListing listing, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":" + listing.getId() + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return createdRequestId(result);
    }

    private Long createdRequestId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private List<ActivityEvent> activityEventsOfType(ActivityType type) {
        return activityRepository.findAll().stream()
                .filter(event -> event.getType() == type)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .toList();
    }

    private boolean waitForNotifications(Long userId, int expected) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size() >= expected) {
                return true;
            }
            Thread.sleep(250);
        }
        return false;
    }

    @Test
    void newUserMustChooseARoleBeforeUsingRoleSpecificEndpoints() throws Exception {

        mockMvc.perform(get("/api/me").with(user(PENDING_EMAIL).roles("PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(PENDING_EMAIL))
                .andExpect(jsonPath("$.role").value(nullValue()));

        mockMvc.perform(get("/api/listings/mine").with(user(PENDING_EMAIL).roles("PENDING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/listings").with(user(PENDING_EMAIL).roles("PENDING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LISTING_JSON.formatted(500)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/requests").with(user(PENDING_EMAIL).roles("PENDING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":1,\"quantity\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats").with(user(PENDING_EMAIL).roles("PENDING")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/me/role").with(user(PENDING_EMAIL).roles("PENDING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmail(PENDING_EMAIL).orElseThrow().getRole()).isNull();

        mockMvc.perform(post("/api/me/role").with(user(PENDING_EMAIL).roles("PENDING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FARMER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FARMER"));
        assertThat(userRepository.findByEmail(PENDING_EMAIL).orElseThrow().getRole()).isEqualTo(Role.FARMER);

        mockMvc.perform(get("/api/me").with(user(PENDING_EMAIL).roles("FARMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FARMER"));
        mockMvc.perform(post("/api/me/role").with(user(PENDING_EMAIL).roles("FARMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"BUYER\"}"))
                .andExpect(status().isConflict());
        assertThat(userRepository.findByEmail(PENDING_EMAIL).orElseThrow().getRole()).isEqualTo(Role.FARMER);
    }

    @Test
    void roleSelectionPersistsTheNewAuthorityInTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/me")
                        .with(user(PENDING_EMAIL).roles("PENDING"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(nullValue()));

        mockMvc.perform(post("/api/me/role")
                        .with(user(PENDING_EMAIL).roles("PENDING"))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FARMER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FARMER"));

        mockMvc.perform(get("/api/listings/mine").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void adminAccountCanUseTheAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/stats").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.farmers").isNumber())
                .andExpect(jsonPath("$.buyers").isNumber())
                .andExpect(jsonPath("$.activeListings").isNumber())
                .andExpect(jsonPath("$.purchaseRequests").isNumber())
                .andExpect(jsonPath("$.pendingRequests").isNumber());
        mockMvc.perform(get("/api/admin/users").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/activity").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/listings").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void farmerCanEditAndDeactivateOwnListingButNotOthers() throws Exception {
        ProduceListing listing = listingRepository.save(ProduceListing.builder()
                .farmer(farmer)
                .name("IT Deact Tomatoes")
                .category("Vegetables")
                .quantity(100)
                .pricePerUnit(new BigDecimal("800"))
                .location("Kigali")
                .availableFrom(LocalDate.of(2026, 10, 10))
                .description("Fresh farm tomatoes")
                .status(ListingStatus.ACTIVE)
                .build());

        String updateJson = """
                {"name":"IT Deact Tomatoes","category":"Vegetables","quantity":80,"pricePerUnit":900,
                 "location":"Kigali","availableFrom":"2026-10-10","description":"Updated","status":"%s"}""";

        mockMvc.perform(put("/api/listings/" + listing.getId())
                        .with(user(FARMER2_EMAIL).roles("FARMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson.formatted("INACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/listings/" + listing.getId())
                        .with(user(FARMER_EMAIL).roles("FARMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson.formatted("INACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.quantity").value(80))
                .andExpect(jsonPath("$.pricePerUnit").value(900));

        mockMvc.perform(get("/api/listings?search=IT Deact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/listings/mine").with(user(FARMER_EMAIL).roles("FARMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + listing.getId() + ")].status").value("INACTIVE"));

        mockMvc.perform(put("/api/listings/" + listing.getId())
                        .with(user(FARMER_EMAIL).roles("FARMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson.formatted("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/listings?search=IT Deact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + listing.getId() + ")]").exists());
    }

    @Test
    void marketplaceSupportsLocationFilterAndFacets() throws Exception {
        ProduceListing listing = seedListing(100);

        mockMvc.perform(get("/api/listings?location=kiga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(listing.getId().intValue())));

        mockMvc.perform(get("/api/listings?category=Vegetables&location=Kigali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(listing.getId().intValue())));

        mockMvc.perform(get("/api/listings/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasItem("Vegetables")))
                .andExpect(jsonPath("$.locations", hasItem("Kigali")));
    }

    private void cleanup() {
        List<String> emails = List.of(FARMER_EMAIL, FARMER2_EMAIL, BUYER_EMAIL, ADMIN_EMAIL, PENDING_EMAIL);
        List<User> users = emails.stream()
                .flatMap(email -> userRepository.findByEmail(email).stream())
                .toList();
        List<Long> userIds = users.stream().map(User::getId).toList();

        for (User user : users) {

            requestRepository.deleteAll(requestRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId()));
            requestRepository.deleteAll(
                    requestRepository.findByListingFarmerIdOrderByCreatedAtDesc(user.getId()));
            for (ProduceListing listing : listingRepository.findByFarmerIdOrderByAvailableFromAsc(user.getId())) {
                listingRepository.delete(listing);
            }
            notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
            userRepository.delete(user);
        }
        activityRepository.deleteAll(activityRepository.findAll().stream()
                .filter(event -> event.getUserId() != null && userIds.contains(event.getUserId()))
                .toList());
    }
}
