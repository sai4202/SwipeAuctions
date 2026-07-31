# SwipeAuctions — End-to-End Security & Money-Flow Findings

**Status: RESOLVED.** All findings below were fixed in commit `f563ac3` (2026-07-30). Payments have
since moved from Stripe to Razorpay (commit `a69b6c2`), so file-level details below describing
Stripe are historical — the same root causes and fixes applied to the Razorpay implementation that
replaced it. Kept as a historical record of what was found and why; not an open task list.

A follow-up audit on 2026-07-31 re-verified every fix in this document against current code (still
correct) and additionally: confirmed no admin-privilege-escalation path exists, added a per-IP login
throttle (`common/security/LoginRateLimiterService`) on top of the existing per-account lockout, and
fixed the device-list UI to show device name + last-active time instead of IP address.

**Scope:** Full end-to-end review requested by the user ("front end, back end, security issues,
login loopholes, money tightness, and everything"). Covered via two parallel read-only deep-dive
investigations — (1) authentication/authorization/session security, (2) money-flow correctness
(wallet, bidding, settlement, disputes, Stripe) — plus manual functional smoke testing of the live
app (login, session limits, admin-authorization boundary, bid placement).

**Method:** Backend + frontend running locally (`mvn spring-boot:run` with `.env` sourced, Vite dev
server). Two `general-purpose` agents (isolated git worktrees, read-only, no code changes) grepped
and read the full backend for their respective domains, cross-checked against existing tests
(`BidServiceConcurrencyTest`, `WalletServiceTest`) where relevant. In parallel, manual testing was
done live in Chrome as `bidder@swipeauctions.test`, plus direct `curl` checks of admin endpoints
with a non-admin token and with no token at all.

Findings below are ordered by severity. Each includes: file/location, **why it's happening** (root
cause), **plan** (what needs to change), and **fix** (the concrete change).

---

## Critical — exploitable today, real money at stake

### 1. `POST /api/wallet/topup` lets any logged-in user mint free wallet balance

**File:** `src/main/java/com/swipeauctions/wallet/controller/WalletController.java:61-66`, backed by
`WalletService.topUp` (`wallet/service/WalletService.java:113-122`)

```java
/** Dev-only instant credit — bypasses Stripe entirely. Left in place for local/demo testing. */
@PostMapping("/topup")
public WalletResponse topUp(@Valid @RequestBody TopUpRequest req) { ... }
```

**Why it's happening:** Built as a dev/demo convenience so wallet-dependent features could be
tested without wiring up Stripe — the code comment says so explicitly. It adds the client-supplied
`amount` straight to `availableBalance`: no Stripe call, no verification, no idempotency key.
Unlike `DevDataSeeder` (which correctly has `@Profile("dev")`), nobody added the equivalent guard
here. It is a live route in every environment, including production.

**Impact:** Since ₹5,000 of `availableBalance` → ₹2.5 crore of bidding credit
(`WalletService.creditLimitFor`), one `curl -X POST /api/wallet/topup -d '{"amount":5000}'` with any
user's token grants ₹2.5cr of real bidding power backed by nothing. That fabricated balance is what
`AuctionService.completeSettlement`/`captureRemainder` actually debits at settlement — a direct path
to winning real auctions and "paying" with money that was never deposited.

**Plan:** Production already has a solid, verified Stripe top-up path (webhook signature
verification confirmed correct), so this endpoint isn't needed outside local dev at all.

**Fix:** Add `@Profile("dev")` to this controller (same pattern already used by `DevDataSeeder`), so
the route doesn't exist outside the dev profile.

---

### 2. Registration fee & subscription "payment" are unpaid stubs — same exposure

**Files:**
- `src/main/java/com/swipeauctions/auth/serviceImpl/UserAuthServiceImpl.java:706-727`
  (`POST /api/auth/pay-registration-fee`)
- `src/main/java/com/swipeauctions/settings/service/SubscriptionService.java:25-31`
  (`POST /api/subscriptions/subscribe`)

**Why it's happening:** Same root cause as #1 — both are explicitly-commented Phase-2 stubs
("dev-instant payment") that flip `registrationFeePaid=true` / grant any requested `SubscriptionTier`
with zero wallet debit or Stripe involvement, and neither is profile-gated.

**Impact:** Anyone can unlock bidding (bypassing the registration-fee wall) or any subscription tier
(Gold/Diamond-gated listings) for free with one POST, in production.

**Plan:** Short-term: gate both to dev profile, same as #1, so production can't exploit the free
stub. Long-term/complete fix is a separate feature-build — there is currently no real payment
collection flow for either of these two gates, unlike wallet top-up/withdraw which already has one.

**Fix (immediate):** `@Profile("dev")` on both endpoints (or the service methods, if the controllers
serve other non-dev-only routes too — verify before applying).
**Fix (complete, larger task):** build real Stripe Payment Intents + webhook confirmation for both,
mirroring the pattern already proven correct for wallet withdrawals.

---

### 3. Wallet holds can be double-released or double-captured under concurrency

**File:** `src/main/java/com/swipeauctions/wallet/service/WalletService.java` —
`releaseHold`/`captureHold` (lines 154-179), `releaseSaleProceeds`/`refundSaleProceeds`
(lines 275-310)

**Why it's happening:** All four methods check "is this hold still ACTIVE?" via a **non-locking**
read *before* taking the wallet row's pessimistic lock. The wallet balance arithmetic itself is
safely locked (`WalletRepository.findByUserIdForUpdate` is used correctly) — but the *decision* to
run that arithmetic isn't, because neither `BidEligibilityHoldRepository` nor
`SaleProceedsHoldRepository` has any locking finder for the hold row itself.

**Concrete exploit path:** An admin double-clicking "Resolve dispute" (`DisputeService.resolve`,
`dispute/service/DisputeService.java:72-97`), or the auto-release scheduler
(`AuctionService.releaseDueProceeds`) racing an admin's manual release
(`AdminController.releaseHold`) on the same hold — both read ACTIVE, both pass, both apply the
credit. The second application drives `heldBalance` negative and mints an extra, unearned credit
into `availableBalance`, which is then withdrawable via the (correctly-verified) Stripe payout.

**Plan:** The hold row itself needs its own lock, acquired before the status check, not just the
wallet row.

**Fix:** Add `@Lock(PESSIMISTIC_WRITE)` finder methods to both `BidEligibilityHoldRepository` and
`SaleProceedsHoldRepository` (mirroring the one `WalletRepository` already has). Reorder
`releaseHold`, `captureHold`, `releaseSaleProceeds`, `refundSaleProceeds` to lock the hold row
first, re-verify ACTIVE status *after* that lock, then proceed as today. Also add a lock (on the
`Dispute` row or the hold) inside `DisputeService.resolve` itself so the admin-double-click case is
closed at that layer too, not just at the wallet layer.

---

## High

### 4. Admin editing an auction's base price can race a bidder's EMD hold

**File:** `src/main/java/com/swipeauctions/auction/service/AuctionService.java:179-212`
(`adminUpdate`)

**Why it's happening:** `adminUpdate` fetches the auction via the non-locking `get(auctionId)`, not
`getForUpdate` (which `forceClose`/`closeAuctionById` correctly use). Its guard against changing
`basePrice` while a hold exists is a check-then-act with nothing serializing it against
`WalletService.placeHold`, which never touches or version-bumps the `Auction` row. A hold can commit
in the window between admin's existence check and admin's final save.

**Impact:** Traced through `settlementRemainder`/`closeAuction`: if the base price is raised after a
hold was sized to the old price, the platform silently absorbs the shortfall at settlement; if
lowered, the buyer is silently overcharged. Narrow timing window, but real.

**Plan:** Serialize the two operations on the same row lock.

**Fix:** Use `getForUpdate(auctionId)` in `adminUpdate` (matching `forceClose`); have `placeHold`
also acquire a lock on (or at least read-through) the `Auction` row inside its transaction so the
two genuinely compete for the same lock instead of racing past each other.

---

### 5. A plain double-click on "Register to bid" can permanently strand a bidder's own EMD

**File:** `src/main/java/com/swipeauctions/wallet/service/WalletService.java:126-150`
(`placeHold`)

**Why it's happening:** The "already registered for this auction" check runs *before* the wallet
lock; the balance-debit mutation runs unconditionally after the lock, and only the post-lock lookup
decides whether to reuse an existing hold row. Two near-simultaneous `POST /api/auctions/{id}/register`
calls (an ordinary double-click, no adversary needed) both pass the pre-lock check, both debit the
wallet, but only one `BidEligibilityHold` row survives.

**Impact:** `heldBalance` ends up double-debited while only one hold is ever tracked/released —
1× EMD is permanently stranded, invisible and unrecoverable by the bidder. Harms the bidder, not an
attacker-gain vector, but it's a real stuck-funds bug.

**Plan:** Add a hard backstop at the database level, since app-level locking alone has already
proven fragile for this exact shape of bug (see #3).

**Fix:** Add a unique constraint on `bid_eligibility_holds(auction_id, bidder_id)` via a new Flyway
migration, and move the "already registered" check inside the locked section so a legitimate
double-click gets a clean, expected error instead of a stray constraint-violation exception.

---

## Medium

### 6. Login endpoint leaks whether an email/mobile is registered

**Files:** `src/main/java/com/swipeauctions/auth/helper/UserAuthHelperService.java:44-58`,
`src/main/java/com/swipeauctions/auth/serviceImpl/AdminAuthServiceImpl.java:102-103`

**Why it's happening:** A nonexistent account throws `ResourceNotFoundException("User/Admin not
found")` → HTTP 404, while a wrong password on an existing account throws a distinct 401 "Invalid
credentials, N attempt(s) remaining." This is inconsistent with the deliberately-generic messaging
this same codebase already uses correctly on `forgotPassword` and OTP-verify endpoints (confirmed by
comments there stating anti-enumeration was a conscious design decision).

**Plan/Fix:** Make the login failure response identical (status code + message) regardless of
whether the account exists or the password was wrong — same treatment already applied elsewhere in
this codebase, just not here.

---

### 7. Image upload accepts SVG based only on the client-supplied `Content-Type` header

**File:** `src/main/java/com/swipeauctions/storage/serviceImpl/LocalDiskStorageProvider.java:36-40`

**Why it's happening:** The upload handler accepts anything where the browser-supplied
`Content-Type` starts with `image/` or `video/` — an attacker-controlled header, never cross-checked
against actual file content (no magic-byte sniffing).

**Impact:** An SVG containing an embedded `<script>` can be uploaded with `Content-Type:
image/svg+xml`, gets stored, and is served back from `/uploads/**`. Viewing it through the normal
in-app `<img>` gallery is safe (browsers don't execute scripts in an `<img src="...svg">`), but
anyone who opens the raw upload URL directly gets script execution against the backend's origin.
Blast radius is reduced because the JWT lives in the *frontend's* separate origin (`localStorage`),
not the backend's — but it's still a real stored-content risk (phishing/defacement, and an
increasing risk if anything else ever becomes same-origin with `/uploads`).

**Plan/Fix:** Validate actual file bytes server-side (magic-byte check) rather than trusting the
header, and/or drop SVG from the accepted `image/*` set, and/or serve `/uploads` from a separate
cookieless subdomain with a locked-down CSP / `Content-Disposition: attachment`.

---

## Low / Informational (not urgent — listed for completeness, no immediate action needed)

- **`AdminController.releaseHold` has no lifecycle guard** — lets an admin force-release any
  bidder's EMD hold with no precondition tying it to a genuinely stuck hold. Self-healing at
  settlement (confirmed), just an audit/precondition gap. `admin/controller/AdminController.java:124-131`
- **`AuctionController.register` doesn't check `registrationFeePaid`**, unlike `BidService.placeBid`
  which does — a user who hasn't "paid" the fee can still lock real wallet funds into an EMD hold via
  `/register`, just can't subsequently bid. Enforcement inconsistency, compounds with #2 while that
  fee is free. `auction/controller/AuctionController.java:144-180`
- **No IP-based rate limiting anywhere** — only account-scoped lockout (5 attempts → 1 minute,
  verified live and working correctly). An attacker can grind slowly against one account forever, or
  fan out across many accounts with zero friction.
- **Secondary enumeration signal in `forgotPassword`** for accounts that exist but aren't verified
  yet — a distinct `BadRequestException("Account is not active")` instead of the generic message
  used for the "doesn't exist" case. `auth/serviceImpl/UserAuthServiceImpl.java:484-499`
- **Listing-image upload writes the file to disk before the ownership check** —
  `storageProvider.store(...)` runs before `catalogService.addImage(...)` throws on a non-owned
  listing. Storage-quota-pollution nuisance only; the resulting filename/URL is never disclosed to
  the attacker on the failing path. `catalog/controller/CatalogController.java:71-78`
- **WebSocket connections don't re-check session liveness** — a revoked token (logout,
  password-change, session-limit kick) keeps receiving personal push notifications over the existing
  WS connection until natural JWT expiry (up to 24h). No state-changing action is exposed over WS
  (push-only), so impact is limited to continued notification delivery, not acting as the user.
  `bidding/config/StompAuthChannelInterceptor.java:38-47`
- **JWT stored in `localStorage`, not an httpOnly cookie** — deliberate, documented tradeoff for a
  stateless cross-origin bearer-token API. Flagged because it raises the stakes of #7 (any future XSS
  becomes full session takeover, not just page defacement). `frontend/src/api.ts`, `auth.tsx`
- **Dealers may get 403 on `/api/sessions/**`** — role-name mismatch (`ROLE_DEALER` granted, but the
  security rule checks `hasRole("USER")`). Fails closed (no hole), but likely means dealers can't
  self-manage/logout their own devices. `common/config/SecurityConfig.java:152-153`
- **WebSocket endpoint's CORS allows `*` origin patterns** — low risk given auth is a bearer token in
  the CONNECT frame, not a cookie, so a cross-origin page can't ride a victim's session without
  already having their JWT (i.e. needs a separate XSS first). `bidding/config/WebSocketConfig.java:31`
- **`getOrCreateWallet` isn't atomic** — a genuine race on a brand-new user's very first wallet
  operation could hit the DB's unique constraint on `wallets.user_id` and produce an ugly 500 instead
  of a graceful retry. No duplicate-balance risk (the constraint backstops it), just a rough edge.

---

## Confirmed solid — reviewed and no action needed

- JWT signing/validation: no algorithm-confusion / `alg:none` vulnerability, no fallback secret if
  `JWT_SECRET` is unset (app fails to start instead), authorities are re-derived fresh from the DB on
  every request rather than trusted from token claims.
- Session revocation is enforced server-side on **every** request (not just at next login) — logout,
  password change, and the 2-device limit all take effect immediately.
- Admin-route gating is one global rule (`/api/admin/**` → `hasRole("ADMIN")`), so no individual
  admin controller method can be accidentally left ungated.
- No IDOR found across wallet, KYC, disputes, settlement, or session endpoints — every one resolves
  the resource from the authenticated principal, not a client-supplied ID.
- KYC-before-bid and registration-fee-before-bid gates are enforced inside `BidService.placeBid`
  itself, not just the controller or frontend — a direct API call can't skip them (though see #2 for
  why the fee itself is currently free to "pay").
- CORS allowlist is explicit (no wildcard); CSRF-disabled is the correct choice for this stateless
  bearer-token API (no cookie for a forged request to ride on).
- Stripe webhook handling verifies the signature before trusting any payload, and is idempotent
  against replay.
- No path-traversal vector in file storage (server-generated UUID filenames, normalized/validated
  target directory).
- Bid placement correctly locks the auction row before validating/inserting — the credit-limit race
  fix from an earlier session is still holding, confirmed by existing passing tests
  (`BidServiceConcurrencyTest`).
- Settlement is idempotent and unified between the scheduler's automatic close and admin's
  force-close (both funnel through the same locked `closeAuction`).
- No `double`/`float` used for money anywhere in the backend — `BigDecimal` throughout with correct
  scale and explicit rounding where division occurs.

---

## Functional smoke test (manual, live in Chrome + curl)

- **Login lockout:** verified live — 5 wrong-password attempts locked the account with "Account
  locked due to multiple failed login attempts. Please try again after 1 minute," a short,
  self-expiring window (not a permanent DoS).
- **2-device session limit:** verified live — a 3rd login attempt correctly surfaced a "Device limit
  reached" screen listing active sessions with the option to log one out and continue; this screen
  only appears *after* the correct password was already validated, so it's not exploitable
  pre-authentication.
- **Admin-authorization boundary:** verified via direct `curl` — `GET /api/admin/users` and
  `GET /api/admin/stats` both correctly returned 401 with a valid bidder (non-admin) token, and 401
  with no token at all.
- **Bid placement:** placed a real bid as `bidder@swipeauctions.test` through the full UI flow
  (Terms & Conditions acceptance gate → confirm-bid dialog → toast confirmation). Wallet available
  balance, held amount, and credit limit all updated correctly in real time; no console errors.
- **Not yet covered:** seller flow, dealer flow, admin dashboard actions (force-close, dispute
  resolution, KYC approval), the wallet withdraw/Stripe-Connect flow, and the vehicle-listing
  creation flow. Flagged as open — worth a follow-up pass once the above findings are triaged.
