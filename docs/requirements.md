# FarmLink — Software Requirements Documentation

**Project:** FarmLink — Local Produce Marketplace & Order Coordination Platform
**Companion documents:** the root [`README.md`](../README.md) covers setup, running and
testing commands; this document covers *what the system must do and how that is verified*.
It does not repeat setup instructions.

All statements below are based on the actual implementation in this repository
(backend services/controllers under `backend/src/main/java/com/farmlink/`, tests under
`backend/src/test/`, frontend under `frontend/src/`). Anything not implemented is explicitly
labelled **Future work** or **Excluded**.

---

## 1. Problem Statement

Smallholder farmers frequently lack a direct channel to business buyers (restaurants,
shops, institutions). Produce availability spreads informally, prices are opaque, and
orders are coordinated by phone or word of mouth, leaving no record of what was requested,
promised, or refused.

FarmLink addresses this with a web platform where farmers publish structured produce
listings (item, category, quantity in kg, price per kg, location, availability date) and
business buyers discover produce and submit **purchase requests** for a specific quantity.
The farmer accepts or rejecting each request; accepted quantities are deducted from the
listing, and both sides are kept informed through in-app notifications. Every meaningful
action is recorded in an audit trail, so the platform replaces informal phone coordination
with a transparent, queryable ordering process.

## 2. Target Users

Roles are assigned at first login and enforced server-side (Spring Security) on every
protected endpoint.

| Role | Description | Actual enforced capabilities |
|---|---|---|
| **FARMER** | A smallholder farmer selling produce | Create listings; view/edit/deactivate own listings (403 on others' listings); view incoming purchase requests; accept or reject pending requests on listings they own |
| **BUYER** | A business buyer (restaurant, shop, institution) | Browse the marketplace (also possible without login); submit purchase requests against active listings; track the status of own requests |
| **ADMIN** | Platform administrator | View platform statistics, all users, all listings and the activity trail; deactivate/reactivate any listing; view all purchase requests. Admin cannot be self-selected — it is assigned only through the server-side `ADMIN_EMAILS` allowlist |

A first-time Google login creates a user **without a role** (`role = null`); the onboarding
screen asks the user to choose FARMER or BUYER exactly once (`POST /api/me/role`). The role
is immutable afterwards, so demonstrating both sides of the marketplace requires two Google
accounts.

## 3. Project Objectives

1. **Make produce discoverable** — a public marketplace with text search plus category and
   location filters fed by real listing data (facets).
2. **Structure buying interest** — buyers submit quantity-specific purchase requests that
   the server validates against live stock.
3. **Give farmers operational control** — full management of own listings and a one-click
   accept/reject workflow for incoming requests that keeps stock accurate.
4. **Keep both sides informed and auditable** — asynchronous in-app notifications
   (RabbitMQ) and a persistent activity trail (MongoDB) surfaced in the admin dashboard.
5. **Enforce access control** — role and ownership rules on the server for every protected
   operation, returning 401/403 rather than relying on the UI.

## 4. Scope

### 4.1 Implemented functionality (covered by the code and automated tests)

* Google OAuth2 login, session-based security, first-login onboarding (role chosen once).
* Public marketplace: browse active listings, name/category/location filtering, facets.
* Farmer listing lifecycle: create (starts `ACTIVE`), edit, deactivate/reactivate.
* Purchase-request lifecycle: create (`PENDING`) → `ACCEPTED` (with stock deduction and
  automatic deactivation of the listing at zero stock) or `REJECTED`.
* Post-acceptance contact sharing: once a request is `ACCEPTED`, the two participants (the
  buyer and the listing owner) can see each other's name, Google e-mail and optional phone
  number, with an instruction to coordinate collection or delivery. Enforced server-side;
  pending/rejected requests and non-participants (including admins) are refused.
* Optional phone number: a user may store or clear their own phone number
  (`PUT /api/me/phone`, ≤ 30 characters); the Google e-mail captured at login remains the
  primary contact channel.
* In-app notifications delivered asynchronously via RabbitMQ: new request → farmer;
  acceptance/rejection → buyer. The navbar bell polls every 15 s.
* Activity/audit events in MongoDB for all listing and request actions, shown in the admin
  dashboard.
* Admin dashboard: six platform counters, user list, listing moderation, recent activity.
* Consistent JSON error responses and server-side validation on all writes.

### 4.2 Deliberate exclusions (out of scope, not implemented)

Online payments, delivery/shipping tracking, in-app chat, maps, reviews/ratings, e-mail or
push notifications (delivery is in-app only), listing photos, and AI-based features. These
are intentionally excluded to keep the project focused on listings and request coordination.

### 4.3 Incomplete, untested or limited areas (honest gaps)

* **Frontend has no automated tests** — the React app is verified by a production build in
  CI and by manual use only; all automated behaviour tests target the backend API.
* **Notifications have no read/unread state or deletion** — the bell lists all
  notifications, newest first.
* **No pagination** on marketplace or dashboard list endpoints (complete lists are
  returned).
* **No performance measurements exist** — performance targets in §6 are proposed, not
  tested.
* The RabbitMQ consumer is at-least-once: a redelivered message would create a duplicate
  notification (no idempotency key). Not observable in normal use, noted for completeness.
* **Accepted-request contact sharing has no expiry or revocation** — once a request is
  accepted, the two participants keep access to each other's contact details; there is no
  un-accept action and no in-app messaging fallback (the feature only reveals existing
  contact information). The phone number is optional, so e-mail is the only guaranteed
  channel.

## 5. Functional Requirements

Priority uses MoSCoW (Must / Should / Could). Status reflects the repository as built.

| ID | Requirement | Priority | Status |
|---|---|---|---|
| FR-01 | The system shall authenticate users via Google OAuth2/OIDC and maintain a server-side session; unauthenticated API calls shall receive a JSON 401 | Must | Implemented |
| FR-02 | A first-time user shall have no role and shall be able to choose FARMER or BUYER exactly once (`POST /api/me/role`); a second attempt returns 409 and the role is immutable | Must | Implemented |
| FR-03 | Only accounts listed in the server-side `ADMIN_EMAILS` allowlist shall become ADMIN (checked on every login); ADMIN shall never be self-selected (rejected with 400) | Must | Implemented |
| FR-04 | The marketplace shall publicly list only `ACTIVE` listings without login | Must | Implemented |
| FR-05 | Users shall be able to filter listings by name/category/location; filter options shall come from the distinct values of active listings (`/api/listings/facets`) | Must | Implemented |
| FR-06 | A farmer (FARMER role) shall be able to create a listing with name, category, quantity, price per unit, location and availability date; invalid input shall return 400 with a field message | Must | Implemented |
| FR-07 | A farmer shall be able to edit and deactivate/reactivate **own** listings; other users shall receive 403 (admins may update any listing) | Must | Implemented |
| FR-08 | A buyer shall be able to submit a purchase request (quantity + optional message ≤ 500 chars) against an `ACTIVE` listing; the quantity must be > 0 and ≤ available stock | Must | Implemented |
| FR-09 | Only the listing owner shall accept or reject a request, only while it is `PENDING`; reprocessing returns 409; other users receive 403 | Must | Implemented |
| FR-10 | Accepting a request shall deduct the requested quantity from the listing and set the listing to `INACTIVE` when stock reaches zero | Must | Implemented |
| FR-11 | Buyers shall see their own requests with status; farmers shall see incoming requests on own listings; admins shall see all requests | Must | Implemented |
| FR-12 | The system shall notify users in-app via RabbitMQ: the farmer when a request is created, the buyer when it is accepted or rejected | Must | Implemented |
| FR-13 | The system shall record an activity event (MongoDB) for listing creation/update and request creation/acceptance/rejection, viewable by admins (20 most recent) | Should | Implemented |
| FR-14 | Admins shall see platform counters (users, farmers, buyers, active listings, requests, pending requests) and all users with roles | Must | Implemented |
| FR-15 | Admins shall be able to deactivate/reactivate any listing (moderation) | Should | Implemented |
| FR-16 | All error responses shall use one JSON shape (`timestamp`, `status`, `error`, `message`, `path`) and never leak stack traces | Should | Implemented |
| FR-17 | Wrong-role access to protected endpoints shall return 403 and never execute the operation (role rules in `SecurityConfig` + service-layer ownership checks) | Must | Implemented |
| FR-18 | E-mail/SMS/push notifications | — | **Excluded** (§4.2) |
| FR-19 | Online payments and delivery tracking | — | **Excluded** (§4.2) |
| FR-20 | Listing photo upload | — | Future work |
| FR-21 | Pagination / sorting options on list endpoints | — | Future work |
| FR-22 | Notification read-state and deletion | — | Future work |
| FR-23 | Once a purchase request is `ACCEPTED`, the two participants (buyer and listing owner) shall be able to retrieve each other's contact details — name, Google e-mail and optional phone — plus a short coordination instruction (`GET /api/requests/{id}/contact`). `PENDING` and `REJECTED` requests shall share nothing; any other caller (including admins, other farmers and users without a role) shall receive 403; unauthenticated callers shall receive 401 | Must | Implemented |
| FR-24 | A user shall be able to store or clear their own optional phone number (`PUT /api/me/phone`, ≤ 30 characters, blank clears). The number shall never appear in public listings, request lists, notification payloads or the admin user list — only on `/api/me` (own number) and the accepted-request contact endpoint | Must | Implemented |

## 6. Quality Attributes

Each attribute states a **measurable acceptance criterion**, a **verification method**, and
whether the criterion is a **proposed target** or **actually covered by automated tests**.
No performance measurements have been taken; none are claimed.

### 6.1 Usability

* **Acceptance criterion:** a new user completes onboarding in a single choice screen after
  login; every data view shows an explicit loading, empty, or error state.
* **Verification method:** manual walkthrough of the demo flow (login → onboarding →
  dashboard); frontend code inspection confirms `Loading`, `EmptyState`, `ErrorState` and
  skeleton states are used on all data views (`frontend/src/components/`).
* **Status:** criterion is a **design target**; no usability study has been performed.

### 6.2 Performance

* **Acceptance criterion (proposed):** marketplace search responds in under 500 ms on
  localhost with up to 10,000 listings; the notification poll (every 15 s, per
  `frontend/src/App.jsx`) must not produce user-visible lag.
* **Verification method (proposed):** browser DevTools timing or `curl -w` timing against
  `/api/listings`, repeated per filter combination.
* **Status:** **proposed target only — not measured.** The repository contains no
  performance tests and records no timing results. The schema supports the target through
  indexes on `status`, `farmer_id`, `listing_id`, `buyer_id` and `user_id` (see
  [`data-model.md`](data-model.md#4-technology-oriented-schema-postgresql)).

### 6.3 Reliability

* **Acceptance criterion:** the automated suite — 23 unit tests and 17 integration tests
  against real PostgreSQL, MongoDB and RabbitMQ — passes on every push/PR in CI.
* **Verification method:** GitHub Actions runs (`.github/workflows/ci.yml`); integration
  tests are flag-gated (`-Dfarmlink.integration=true`) and skip cleanly without
  infrastructure.
* **Status: actually covered by automated tests.** Critical flows with failure potential
  (over-requesting, double processing, processing another farmer's request, missing
  notification recipient) have dedicated tests; the RabbitMQ queue is durable and the
  consumer drops events for deleted users instead of failing.

### 6.4 Security

* **Acceptance criteria (each enforced by a named test):**
  * unauthenticated calls to protected `/api/**` endpoints return JSON 401;
  * wrong-role calls return 403 and do not change state;
  * a user can neither self-select ADMIN nor change an existing role;
  * contact details of an accepted request are returned only to its two participants —
    not to admins, not to other users, not while the request is pending or rejected, and
    never through any public or unrelated endpoint;
  * no secrets are stored in the repository (all credentials via environment variables;
    `.env` is git-ignored).
* **Verification method:** integration tests `marketplaceIsPublicButProtectedEndpointsNeedLogin`,
  `buyerIsForbiddenFromFarmerAndAdminEndpoints`, `buyersCannotProcessRequests`,
  `newUserMustChooseARoleBeforeUsingRoleSpecificEndpoints`,
  `contactDetailsAreOnlySharedWithParticipantsOfAnAcceptedRequest`,
  `contactDetailsStayHiddenForRejectedRequests`,
  `contactDetailsNeverAppearInPublicOrUnrelatedResponses`; unit tests
  `adminCanNeverBeChosenByTheUser`, `roleCannotBeChangedAfterItWasChosen`,
  `nonParticipantsAreForbiddenFromContactDetails`,
  `contactDetailsAreHiddenWhileTheRequestIsPendingOrRejected`.
* **Status: actually covered by automated tests** (secret hygiene is verified by repository
  inspection). Known gaps — CSRF disabled, no rate limiting, no deprovisioning UI — are
  documented in `README.md` §13 and accepted for a coursework demo system.

## 7. User Stories and Acceptance Criteria

### US-1 — As a farmer, I want to publish a produce listing, so that business buyers can discover and order my produce.

* **Given** I have logged in with Google and chosen the FARMER role, **when** I submit a
  valid listing (name, category, quantity, price, location, availability date) on my
  dashboard, **then** the listing is stored with status `ACTIVE`, appears on the public
  marketplace without login, and a `LISTING_CREATED` activity event is recorded.
  *(Test: `farmerCanCreateAValidListingAndItIsLoggedInMongoDb`.)*
* **Given** the same state, **when** I submit a listing with quantity `0` or a blank name,
  **then** the server responds 400 with a field-level message and no listing is created.
  *(DTO validation, `ListingCreateRequest`.)*
* **Given** I am a BUYER, **when** I call `POST /api/listings`, **then** the server
  responds 403. *(Test: `buyerIsForbiddenFromFarmerAndAdminEndpoints`.)*

### US-2 — As a business buyer, I want to request a specific quantity of produce, so that the farmer knows exactly what I need.

* **Given** an active listing with 100 kg available, **when** I (BUYER) submit a request
  for 150 kg, **then** the server responds 400 stating the requested quantity exceeds the
  available quantity, and no request is created.
  *(Tests: `buyerCannotRequestMoreProduceThanIsAvailable`, unit and integration.)*
* **Given** the same listing, **when** I submit a request for 50 kg with an optional
  message, **then** the request is stored as `PENDING`, returns 201, and the farmer
  receives an in-app notification delivered through RabbitMQ.
  *(Test: `ownerProcessesRequestsAndBuyerIsNotified`.)*
* **Given** I am not logged in, **when** I call `POST /api/requests`, **then** the server
  responds 401.

### US-3 — As a farmer, I want to accept or reject incoming requests, so that stock stays accurate and buyers get a clear answer.

* **Given** a `PENDING` request for 50 kg on my listing with 100 kg available, **when** I
  accept it, **then** the request becomes `ACCEPTED`, the listing quantity becomes 50 kg,
  and the buyer receives an in-app notification.
  *(Test: `ownerCanAcceptAPendingRequestAndTheQuantityIsDeducted` +
  `ownerProcessesRequestsAndBuyerIsNotified`.)*
* **Given** the accepted request, **when** I try to accept it again, **then** the server
  responds 409 "Request has already been processed".
  *(Test: `aProcessedRequestCannotBeProcessedAgain`.)*
* **Given** the listing quantity equals the accepted quantity, **when** the request is
  accepted, **then** the listing automatically becomes `INACTIVE`
  (`PurchaseRequestService.accept`).
* **Given** a request on another farmer's listing, **when** I try to accept it, **then**
  the server responds 403. *(Test: `onlyTheListingOwnerCanProcessARequest`.)*

### US-4 — As a buyer (or farmer), I want to see the other party's contact details once a request is accepted, so that we can arrange collection or delivery directly.

* **Given** my purchase request is `PENDING` or `REJECTED`, **when** I call
  `GET /api/requests/{id}/contact`, **then** the server responds 403 and shares nothing.
  *(Tests: `contactDetailsAreOnlySharedWithParticipantsOfAnAcceptedRequest`,
  `contactDetailsStayHiddenForRejectedRequests`,
  `contactDetailsAreHiddenWhileTheRequestIsPendingOrRejected`.)*
* **Given** the farmer accepts my request, **when** I call the same endpoint, **then** I
  receive the farmer's name, Google e-mail and optional phone (null when none was added)
  plus a short instruction to coordinate collection or delivery — and the farmer receives
  my contact details the same way.
  *(Tests: `acceptedRequestSharesEachSidesContactIncludingOptionalPhone`,
  `bothParticipantsCanSeeEachOthersContactDetailsOnceAccepted`,
  `contactDetailsAreGracefulWhenNoPhoneNumberWasAdded`.)*
* **Given** I am any other user — another farmer, a different buyer, or an admin —
  **when** I call the endpoint for someone else's accepted request, **then** the server
  responds 403; without login it responds 401.
  *(Tests: `contactDetailsAreOnlySharedWithParticipantsOfAnAcceptedRequest`,
  `nonParticipantsAreForbiddenFromContactDetails`.)*
* **Given** the request is accepted, **when** any user lists the marketplace, their
  requests, notifications or the admin user list, **then** no e-mail or phone contact
  fields are present in those responses.
  *(Test: `contactDetailsNeverAppearInPublicOrUnrelatedResponses`.)*

## 8. Requirements Verification Matrix

Mapping of requirements to implementation areas and automated tests where one exists.

| Requirement | Implementation area | Automated verification |
|---|---|---|
| FR-01 | `SecurityConfig`, `GoogleOAuth2UserService`, `api.js` (`/oauth2/authorization/google`) | `marketplaceIsPublicButProtectedEndpointsNeedLogin` |
| FR-02 | `UserService.selectRole`, `UserController`, `Onboarding.jsx` | `newAccountHasNoRoleUntilTheOnboardingScreenIsCompleted`, `onboardingSavesTheChosen{Farmer,Buyer}Role`, `roleSelectionPersistsTheNewAuthorityInTheSession`, `roleCannotBeChangedAfterItWasChosen` |
| FR-03 | `UserService.upsertGoogleUser` / `initialRole` (`ADMIN_EMAILS`, `FARMER_EMAILS`) | `adminAllowlistGetsAdminOnFirstLogin`, `adminAllowlistIsAuthoritativeOnEveryLogin`, `adminCanNeverBeChosenByTheUser`, `farmerAllowlistGetsFarmerOnFirstLogin` |
| FR-04 | `ListingService.search`, `ListingController` (public GETs) | `marketplaceIsPublicButProtectedEndpointsNeedLogin` |
| FR-05 | `ProduceListingRepository.search` / facet queries, `Marketplace.jsx` | `marketplaceSupportsLocationFilterAndFacets` |
| FR-06 | `ListingCreateRequest` (Bean Validation), `ListingService.create` | `farmerCanCreateAValidListing`, `farmerCanCreateAValidListingAndItIsLoggedInMongoDb` |
| FR-07 | `ListingService.update` (owner-or-admin), `FarmerDashboard.jsx`, `AdminDashboard.jsx` | `farmerCanEditAndDeactivateOwnListingButNotOthers` |
| FR-08 | `PurchaseRequestCreateRequest`, `PurchaseRequestService.create` | `buyerCannotRequestMoreProduceThanIsAvailable` (unit + integration) |
| FR-09 | `PurchaseRequestService.ensureOwner` / `ensurePending` | `onlyTheListingOwnerCanAcceptOrRejectARequest`, `onlyTheListingOwnerCanProcessARequest`, `buyersCannotProcessRequests`, `aProcessedRequestCannotBeProcessedAgain` |
| FR-10 | `PurchaseRequestService.accept` (stock deduction, zero → `INACTIVE`) | `ownerCanAcceptAPendingRequestAndTheQuantityIsDeducted` |
| FR-11 | `PurchaseRequestService.list` (role-scoped), `BuyerDashboard.jsx`, `FarmerDashboard.jsx` | `buyerIsForbiddenFromFarmerAndAdminEndpoints` (scoping enforced by ownership tests) |
| FR-12 | `AppEventPublisher`, `RabbitConfig`, `NotificationService.onAppEvent`, `Navbar.jsx` | `ownerProcessesRequestsAndBuyerIsNotified` |
| FR-13 | `ActivityService`, `ActivityEvent` (MongoDB), `AdminService.recentActivity` | `farmerCanCreateAValidListingAndItIsLoggedInMongoDb` (asserts the Mongo record) |
| FR-14 | `AdminService.stats` / `users`, `AdminController`, `AdminDashboard.jsx` | `adminAccountCanUseTheAdminEndpoints` |
| FR-15 | `AdminDashboard.jsx` → `PUT /api/listings/{id}` | Covered by FR-07 ownership rules (no dedicated admin-moderation test) |
| FR-16 | `GlobalExceptionHandler` / `ApiError` | Exercised implicitly by every 4xx integration assertion |
| FR-17 | `SecurityConfig` matchers + service-layer checks | See FR-01…FR-09 tests above |
| FR-23 | `PurchaseRequestService.contact` (participant + `ACCEPTED` checks), `RequestController` (`GET /api/requests/{id}/contact`), `RequestContactResponse`, `RequestContact.jsx` | `contactDetailsAreOnlySharedWithParticipantsOfAnAcceptedRequest`, `contactDetailsStayHiddenForRejectedRequests`, `acceptedRequestSharesEachSidesContactIncludingOptionalPhone`, `bothParticipantsCanSeeEachOthersContactDetailsOnceAccepted`, `contactDetailsAreHiddenWhileTheRequestIsPendingOrRejected`, `nonParticipantsAreForbiddenFromContactDetails`, `contactDetailsAreGracefulWhenNoPhoneNumberWasAdded` |
| FR-24 | `User.phone`, `UserService.updatePhone`, `UserController` (`PUT /api/me/phone`), `MeResponse` (separate from the admin `UserResponse`), `Profile.jsx` | `buyersCanAddAnOptionalPhoneThatIsValidatedAndClearable`, `contactDetailsNeverAppearInPublicOrUnrelatedResponses`, `phoneIsSavedTrimmedForTheCurrentUser`, `blankPhoneClearsTheStoredNumber`, `unknownPrincipalCannotUpdateAPhoneNumber` |

*Test counts:* 23 unit tests (`ListingServiceTest`, `PurchaseRequestServiceTest`,
`UserServiceTest`) and 17 integration tests (`FarmLinkIntegrationTest`, HTTP-level, real
PostgreSQL/MongoDB/RabbitMQ). Commands: see `README.md` §11.
