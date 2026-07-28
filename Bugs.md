# SwipeAuctions — Bug Report

**Status: findings #1, #2, and #3 below have been fixed and verified** (see "Fix" notes under each).

**Scope:** End-to-end pass over the working tree's uncommitted changes (10 files — admin "User
Details" / bidding-activity feature, admin-only current-winner visibility on auction cards, and the
bid-box redesign on the auction detail page), on top of `079e6b1` (main).

**Method:** Backend restarted fresh (`mvn spring-boot:run`, env vars loaded from `.env`), frontend
already running on Vite. Read the full diff, then exercised it live in Chrome as a regular bidder
(`bidder@swipeauctions.test`) and as admin (`admin@swipeauctions.test`): browsing, placing a real
bid, and opening the new admin User Details / bidding-activity views from both the Users and
Auctions tabs. Also ran `tsc -p tsconfig.app.json --noEmit`, `vitest run`, and the full Maven test
suite (`mvn test`, with `.env` sourced into the shell — without it, `BidServiceConcurrencyTest`
fails at context-startup with `'url' must start with "jdbc"`, a local env-setup issue, not a
product bug).

- Frontend typecheck: clean.
- Frontend tests: 17/17 passed.
- Backend tests: 13/13 passed (5 test classes) once DB env vars were present.

No crashes, console errors, or failed network requests were observed during manual testing. The
findings below are logic/design issues found by reading the diff and confirming against the
running app and codebase.

---

## 1. Test gap: `AuctionControllerListTest` silently exercises the admin path, not the anonymous one

**File:** `src/test/java/com/swipeauctions/auction/controller/AuctionControllerListTest.java`

The diff adds `AuctionController.currentViewer()`, which decides whether a browse request is
anonymous, a regular user, or an admin:

```java
private Viewer currentViewer() {
    try {
        User u = loggedInUserUtil.getCurrentUser();
        return new Viewer(u.getId(), u.getRole() == Role.ADMIN);
    } catch (RuntimeException e) {
        try {
            loggedInUserUtil.getCurrentAdmin();
            return new Viewer(null, true);
        } catch (RuntimeException e2) {
            return new Viewer(null, false);
        }
    }
}
```

This is correct in production: the real `LoggedInUserUtil.getCurrentAdmin()` always throws
(`UnauthorizedException` if there's no authenticated principal, `ResourceNotFoundException` if
there's a principal but no matching `Admin` row), so a genuinely anonymous request correctly falls
through to `Viewer(null, false)`.

But `AuctionControllerListTest` mocks `LoggedInUserUtil` and only stubs `getCurrentUser()` to
throw — `getCurrentAdmin()` is never stubbed. Mockito's default for an unstubbed mock method
returning an object is `null`, not a thrown exception. So inside the test, `currentViewer()` calls
`loggedInUserUtil.getCurrentAdmin()`, gets `null` back (no exception), and returns
`Viewer(null, true)` — every "anonymous" call in this test is actually resolved as **an admin
viewer**, not an anonymous one.

Today's two tests (`noEventIdFilter_returnsEveryAuction`, `eventIdFilter_keepsOnlyAuctionsInThatEvent`)
don't assert on `currentWinnerId`/`currentWinnerEmail`, so this doesn't produce a wrong result right
now — but it means the test file provides **zero real coverage of the anonymous-browsing path**,
which is exactly the path this diff's privacy guarantee depends on ("the current highest bidder's
identity is private and must never leak to regular bidders browsing the same catalogue" — comment
in `AuctionController.AuctionResponse`). A future regression that leaks `currentWinnerId`/
`currentWinnerEmail` to non-admins would pass this suite undetected.

**Fix (applied):** `setUp()` now stubs `getCurrentAdmin()` to throw `UnauthorizedException` for the
default (anonymous) case, and a new test —
`currentWinnerIdentity_hiddenFromAnonymousViewer_visibleToAdminViewer` — asserts
`currentWinnerId`/`currentWinnerEmail` are `null` for an anonymous viewer and populated for an admin
one. All 3 tests in the file pass (`mvn test -Dtest=AuctionControllerListTest`).

---

## 2. UX regression: admin Users tab can no longer open a user's details without spawning a new tab

**Files:** `frontend/src/pages/AdminDashboardPage.tsx`, `frontend/src/util.ts`

Before this change, the Users tab had an inline **Wallet** button per row
(`<button onClick={() => setWalletUser(u)}>Wallet</button>`) that opened the `WalletModal` in
place. That button was removed and replaced by making the email/mobile cells clickable via
`openUserDetails()`:

```ts
export function openUserDetails(userId: string) {
  window.open(`/admin?tab=users&userId=${userId}`, '_blank', 'noopener,noreferrer')
}
```

This always opens a **new browser tab**, even when clicked from inside the Users tab itself, where
the admin is already looking at the exact list `WalletModal` used to open over. The `WalletModal`
component and its `walletUser` state are still in the code, but the only way left to populate
`walletUser` is the `focusUserId` URL-param effect — i.e. only via a freshly opened tab.

Verified live: clicking any user's email in the Users tab correctly spawns
`/admin?tab=users&userId=<id>` in a new tab and shows the full profile/wallet/bidding-activity
modal there — the new feature itself works. But an admin who wants to check, say, five users in a
row while triaging the KYC queue or Users list now accumulates five new browser tabs to do it,
where previously it was five in-place modals with no navigation at all.

The stated intent (comment in `util.ts`): "so jumping to that person's profile never loses the page
the admin was already on." That goal is real (e.g. from the Auctions tab, or from the current-bid
link on a catalogue card, you don't want to lose your filtered list) — but it doesn't apply to the
Users tab's own rows, where the previous in-place modal already didn't lose any state.

**Fix (applied):** the Users tab's own email/mobile buttons now call `setWalletUser(u)` directly
(using the row's already-fetched `AdminUser`, so no extra network round trip) instead of
`openUserDetails(u.id)`. `openUserDetails()` and its new-tab behavior are untouched everywhere else
(Auctions tab's current-bid link, `AuctionCard.tsx`'s admin-only "Current max bid" link). Verified
live: clicking a user's email on the Users tab now opens the User Details modal in place, with zero
new tabs, while the Auctions tab's winner link still opens a new tab as designed.

---

## 3. Minor: bid-amount input lost its inline minimum-bid placeholder

**File:** `frontend/src/pages/AuctionDetailPage.tsx`

```diff
- min={minNext} placeholder={`${minNext}`} />
+ min={minNext} placeholder="" />
```

The empty bid-amount box on the auction detail page used to show the suggested minimum bid as a
greyed-out placeholder inside the field itself; it's now always blank. The minimum is still stated
in the `<label>` directly above ("Your bid (min ₹X)"), so no information is actually lost, and this
is very likely deliberate cleanup alongside removing the "Current bid" line above it — flagging only
because it's a slightly duplicated user-facing regression versus the previous state (one fewer
visual hint at exactly the moment the bidder is about to type a number) and it wasn't mentioned
in the surrounding comments the way the other removals were.

**Fix (applied):** restored `placeholder={`${minNext}`}`.

---

## Things that looked like bugs during testing but weren't

For completeness, these were investigated and ruled out — recorded so they aren't re-litigated:

- **"Outbid" shown on a card even though the bid amount looked high, and the current highest bid is
  no longer shown anywhere for a regular bidder.** Both are intentional consequences of the bid
  acceptance rule change ("[[project_bid_acceptance_rule]]" — bids only need to beat the bidder's
  own last bid, not the global leader). Confirmed by placing a real bid as `bidder@swipeauctions.test`
  and cross-checking via the admin bidding-activity view that `bidder2@swipeauctions.test` genuinely
  held a higher bid on the same item.
- **Admin tab briefly appeared stuck / eventually bounced to `/admin-login` mid-session.** Caused by
  the admin JWT's short default expiry (`ADMIN_JWT_EXPIRATION`, 5 minutes) combined with the
  session-expired handling in `api.ts`'s response interceptor — not a hang or infinite loop. Logging
  back in immediately restored normal behavior.
- **A user's credit limit showing as "₹297.5 Cr" for a ~₹6L deposit.** Matches
  `WalletService.creditLimitFor` exactly (every complete ₹5,000 of balance grants ₹2.5 crore of
  credit; this is the pre-existing "[[project_credit_limit_feature]]" formula, unrelated to this
  diff).
- **First click on the "Auctions" admin tab via an accessibility-tree ref didn't switch tabs.**
  Reproduced only through the automated-testing tool's ref-based click; a normal coordinate click
  worked immediately. Automation artifact, not an app bug.

## Operational note (not a code bug, but worth knowing)

During this session the backend eventually went fully unresponsive — every request hung ~30s then
failed — after an extended stretch of the dev server running alongside several `mvn test` runs
against the same database. The backend log showed:

```
FATAL: (EMAXCONNSESSION) max clients reached in session mode - max clients are limited to pool_size: 15
```

The shared Supabase instance's pooler (session mode) caps concurrent connections at 15. The
long-running dev server's own Hikari pool plus each separate `mvn test` invocation's own Hikari pool
(each opens its own connections against the same database) can exhaust that cap; once exhausted,
every query — including the periodic `AuctionScheduler` tick and any admin/user request — blocks for
the full 30s connection-acquire timeout before failing. The app doesn't crash outright; it just goes
silently slow/unresponsive until connections free up (Supabase's pooler reclaims dead ones after a
delay) or the process is restarted. Not something introduced by this diff, and not fixed here — just
worth knowing if the backend seems to "hang" during heavy local testing: check the log for
`EMAXCONNSESSION` before assuming an app-level deadlock, and avoid running `mvn test` concurrently
with a live `mvn spring-boot:run` against the same shared database.
