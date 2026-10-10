# FarmLink

Local produce marketplace & order coordination platform: smallholder farmers publish
produce listings, local businesses browse them and submit purchase requests, and farmers
accept or reject those requests. Notifications are delivered asynchronously through RabbitMQ.

**Stack:** Java 21 · Spring Boot 3.2.5 · Spring Data JPA · PostgreSQL · MongoDB · RabbitMQ ·
Spring Security (Google OAuth2) · React (Vite) · Docker Compose

Architecture: one simple layered monolith — `controller → service → repository → entity`,
with MongoDB used only for activity/audit events and RabbitMQ only for meaningful
application events.

```
Farmer → create listing → Buyer → browse → purchase request → Farmer → accept/reject
                                                              ↓
                                              RabbitMQ → notification consumer
```

---

## 1. Start the infrastructure

```bash
docker compose up -d      # PostgreSQL :5432, MongoDB :27017, RabbitMQ :5672/:15672
```

> **Important — port conflict:** if a local PostgreSQL *Windows service* is also listening on
> `0.0.0.0:5432`, it shadows the Docker container and the app fails with
> `password authentication failed for user "farmlink"`. Stop that service first
> (`net stop postgresql-x64-18`, run as administrator) — the credentials themselves are correct.

## 2. Configure secrets (never hard-coded)

```bash
cp .env.example .env      # .env is git-ignored
```

| Variable | Purpose | Default |
|---|---|---|
| `DATABASE_PASSWORD` | PostgreSQL password | `farmlink` (docker-compose) |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `farmlink` (docker-compose) |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client id | `not-configured` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | `not-configured` |
| `FARMER_EMAILS` | Optional: comma separated e-mails that become **FARMER** on first login (everyone else chooses a role during onboarding) | empty |
| `ADMIN_EMAILS` | Comma separated e-mails that become **ADMIN** (checked on every login) — e.g. `munyanturirekalizaliesse@gmail.com` for the dev account | empty |
| `FARMLINK_FRONTEND_URL` | React origin for CORS + post-login redirect | `http://localhost:5173` |

Export them in the shell you use to start the backend, e.g. PowerShell:

```powershell
$env:GOOGLE_CLIENT_ID = "....apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET = "...."
$env:FARMER_EMAILS = "you@gmail.com"
$env:ADMIN_EMAILS = "you@gmail.com"
```

## 3. Google OAuth2 setup (once)

1. [Google Cloud Console](https://console.cloud.google.com/) → **APIs & Services → Credentials → Create credentials → OAuth client ID**
2. Application type: **Web application**
3. Authorised redirect URI (exact):
   `http://localhost:8080/login/oauth2/code/google`
4. Copy the client id/secret into the environment variables above.

## 4. Roles and permissions

| Role | Can |
|---|---|
| `FARMER` | create/edit/deactivate own listings, view incoming requests, accept/reject them |
| `BUYER` | browse active listings, view listing details, create purchase requests, view own requests |
| `ADMIN` | view users, listings, stats and activity; update/disable listings |

**First-time onboarding:** a Google account that does not yet have a FarmLink role is **not**
automatically made a BUYER. The first login shows a simple onboarding screen —
*"Welcome to FarmLink. How will you use FarmLink?"* — and the chosen `FARMER` or `BUYER`
role is saved through `POST /api/me/role`. Existing users go straight to their dashboard.

**Admins are never self-selected:** accounts listed in `ADMIN_EMAILS` are recognised as
`ADMIN` on **every** login (the allowlist is authoritative and lives only on the server),
everyone else can only choose FARMER or BUYER. The role never changes afterwards, so a user
has exactly one role — to demonstrate both sides of the marketplace use two Google accounts.

Role state is consistent everywhere: the database role, the Spring Security authority
(`ROLE_FARMER` / `ROLE_BUYER` / `ROLE_ADMIN`, or `ROLE_PENDING` before onboarding — the
session authority is refreshed when the role is chosen, no re-login needed) and the
frontend state (driven by `/api/me`).

## 5. Run it

```bash
# backend (http://localhost:8080)
cd backend
set -a; source ../.env; set +a    # load secrets from .env (Git Bash / macOS / Linux)
./mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # macOS / Linux

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server proxies `/api`, `/oauth2`, `/login` and
`/logout` to the backend, so no CORS configuration is needed during development.

**Suggested demo script**

1. Log in with a fresh Google account → **onboarding screen**: choose *I'm a Farmer* →
   **Farmer dashboard** → create a listing.
2. Log in with a second Google account → onboarding: choose *I'm a Buyer* → open the
   listing from the marketplace → **Request produce**.
3. Back on the farmer account → **Accept** (or Reject) the request (stat cards + badges
   update on the dashboard).
4. The buyer sees the notification *"Your purchase request … has been accepted."*
   (delivered asynchronously through RabbitMQ, shown in the 🔔 panel).
5. Admin account (listed in `ADMIN_EMAILS`) → **Admin** dashboard: users/farmers/buyers
   counters, listings management and recent activity from MongoDB.

> **Demo data:** the database contains a clearly marked demo account
> (`demo-farmer@example.com`, "Demo Farm Collective") with five active listings so the
> marketplace is populated out of the box. Delete the user and its listings to start clean.

---

## API

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/health` | public | liveness |
| GET | `/api/listings?search=&category=&location=` | public | browse **ACTIVE** listings |
| GET | `/api/listings/facets` | public | distinct categories + locations (filter options) |
| GET | `/api/listings/{id}` | public | listing details |
| GET | `/api/listings/mine` | FARMER | own listings (incl. inactive) |
| POST | `/api/listings` | FARMER | create listing → 201 |
| PUT | `/api/listings/{id}` | FARMER/ADMIN | update listing / status |
| GET | `/api/requests` | authenticated | buyer: own · farmer: incoming · admin: all |
| POST | `/api/requests` | BUYER | submit request (PENDING) → 201 |
| PUT | `/api/requests/{id}/accept` | listing owner | PENDING → ACCEPTED |
| PUT | `/api/requests/{id}/reject` | listing owner | PENDING → REJECTED |
| GET | `/api/notifications` | authenticated | own notifications |
| GET | `/api/me` | authenticated | current user (`role: null` → onboarding pending) |
| POST | `/api/me/role` | authenticated | first-time onboarding: `{"role":"FARMER"\|"BUYER"}` → 200 · 409 if already set · ADMIN rejected with 400 |
| GET | `/api/admin/stats` | ADMIN | users/farmers/buyers/listings/request counters |
| GET | `/api/admin/activity` | ADMIN | 20 most recent activity events |
| GET | `/api/admin/users` | ADMIN | all users |
| GET | `/api/admin/listings` | ADMIN | all listings |

**Business rules enforced on the backend**

* listing: `quantity > 0`, `price > 0`, name/category/location/availableFrom required
* purchase request: `quantity > 0`, `quantity <= available`, listing must be `ACTIVE`, buyer authenticated
* processing: only the listing owner, only `PENDING` requests, accepted quantity must still be available
* onboarding: a role can be chosen **once** (`POST /api/me/role`), only FARMER or BUYER —
  ADMIN is rejected and existing roles are immutable
* authorization: `FARMER ≠ BUYER ≠ ADMIN` (401 without login, 403 for the wrong role);
  `ROLE_PENDING` users may only read `/api/me` and choose their role

**Error responses** are a single consistent shape:

```json
{ "timestamp": "2026-10-04T09:11:48Z", "status": 404, "error": "Not Found",
  "message": "Listing 999 not found", "path": "/api/listings/999" }
```

---

## Data model

PostgreSQL (source of truth, `ddl-auto: update`):

* `User` (id, name, email, role — **role is nullable**: `null` means the first-time
  onboarding screen has not been completed yet) — unique e-mail index
  (`backend/src/main/resources/schema.sql` drops the old `NOT NULL` on pre-existing
  databases automatically at startup; fresh databases are created correctly by Hibernate)
* `ProduceListing` (farmer FK, name, category, quantity, price, location, availableFrom, description, status) — indexes on status and farmer
* `PurchaseRequest` (listing FK, buyer FK, quantity, message, status, createdAt) — indexes on listing, buyer, status
* `Notification` (user FK, message, createdAt) — index on user

MongoDB: `activity_events` — `LISTING_CREATED`, `LISTING_UPDATED`,
`PURCHASE_REQUEST_CREATED`, `PURCHASE_REQUEST_ACCEPTED`, `PURCHASE_REQUEST_REJECTED`
with user id, entity id, description and timestamp.

RabbitMQ: topic exchange `farmlink.events` → queue `farmlink.notifications`, JSON payload
(`AppEvent`). `NotificationService` consumes it and stores the notification for the target
user. Publishing an event for a missing user is logged and skipped.

## Tests

```bash
cd backend
./mvnw.cmd test                             # 16 unit tests — always run
./mvnw.cmd test -Dfarmlink.integration=true # + 12 integration tests (needs docker compose up)
```

The unit tests cover the business rules and the onboarding rules (new account = no role,
ADMIN allowlist, role chosen once, ADMIN never self-selected).

The integration tests cover, over real HTTP: public vs. protected endpoints, 401/403 RBAC,
the first-time role onboarding (`/api/me/role`, pending users blocked, session authority
refresh), admin dashboard access, farmer listing creation/edit/deactivation with the
MongoDB activity record, marketplace search/category/location filters and facets,
over-requesting produce, owner-only accept/reject, reprocessing a processed request,
stock deduction and the RabbitMQ notification reaching the buyer. They create and remove
their own `it-*@example.com` users.

Frontend: `npm run build` (Vite production build).

## Troubleshooting

* **`password authentication failed for user "farmlink"`** → a local PostgreSQL service is
  occupying port 5432 (see step 1).
* **Login redirects to `?login=error`** → `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` missing or
  the redirect URI does not match exactly.
* **401 / 403 from the API** → 401 = not logged in (or unknown user), 403 = wrong role for
  that endpoint; both are expected responses, not bugs.
