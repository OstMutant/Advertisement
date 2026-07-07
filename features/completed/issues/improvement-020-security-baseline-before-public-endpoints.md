# improvement-020: Security baseline before first public endpoints — deny-by-default + rate limiting

**Type:** improvement — security hardening, found by external code audit (round 6), verified
**Module:** marketplace-app
**Priority:** medium-high — acceptable today, mandatory BEFORE F-01 adds the first REST controllers
**When:** Wave 1 — hard gate: lands with (or before) the first public REST endpoints
**Status:** ✅ RESOLVED (2026-07-07) — partially diverged from the Suggested fix below after
empirical failure. Deny-by-default (`anyRequest().denyAll()`) was implemented, deployed, and
broke the entire app (0/46 e2e tests — the root Vaadin route itself never rendered, since
`vaadinInternalRequestMatcher` covers only internal AJAX/RPC traffic, not the `GET /` app-shell
bootstrap). Reverted to `anyRequest().permitAll()` — see `marketplace-app/DECISIONS.md` ADR-025
for the full root-cause and the resulting process rule (each future non-Vaadin REST controller
must add its own explicit `requestMatchers(...)` rule ahead of the catch-all). Rate limiting was
implemented as designed below, then corrected: the first version counted every attempt
(including successes), which broke bulk e2e signups from a shared IP; both limiters now count
only real failures — see ADR-026. Full e2e suite 47/47 green after both fixes (47, not 46 —
a dedicated `rateLimitUser` test was added to spec 02).

## Problem

`SecurityConfig.java:26-34` (verified):

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(vaadinInternalRequestMatcher).permitAll()
        .anyRequest().permitAll()          // everything public by default
)
.csrf(csrf -> csrf.ignoringRequestMatchers(vaadinInternalRequestMatcher))
```

Today this is a lax-but-workable posture: the app is almost entirely a single-route
server-side Vaadin UI, view access is enforced manually (`AccessEvaluator`) with tabs hidden
per role, and there is exactly **one** intentional non-Vaadin REST endpoint today —
`HealthController` (`/health`, load balancer probe, deliberately public per
`marketplace-app/CLAUDE.md`). Correction to an earlier draft of this issue: "no REST surface"
was not quite accurate — one exists by design, and it's the right kind of public (a health
probe with no sensitive data). The external reviewer's "CRITICAL" rating overstates the
current risk.

It becomes a real hole exactly at product Phase 1: the roadmap adds servlet/REST endpoints —
the OG bot controller and sitemap (F-01), later the Telegram webhook (F-07), actuator
(process-improvements P3 #17), payment webhooks (F-08). Under `anyRequest().permitAll()`
every one of them is born public by default, and nobody will remember to secure each one.

Related known gap in the same area: no rate limiting on login/signup (brute force / signup
spam), previously flagged in the strategic memo hidden-debt list.

**Verified (2026-07-04): login/signup are NOT HTTP endpoints — a URL-based rate limit
cannot apply to them.** `AuthService.login(email, password)`
(`marketplace-app/services/auth/AuthService.java:26`) and
`UserService.register(SignUpDto)` (`user-spring-boot-starter/services/UserService.java:84`)
are plain Java method calls invoked directly from Vaadin dialogs via Vaadin's internal RPC
channel — already covered by `vaadinInternalRequestMatcher.permitAll()`, with no separate
`/login` or `/signup` URL for Spring Security to match against. Rate limiting must therefore
be an **application-level guard inside these methods**, not a `SecurityFilterChain` rule.

**Forward-compatibility check (2026-07-04): no REST login is on the current product roadmap**
(checked F-01/F-07/F-08/mobile — mobile is explicitly deferred, Telegram verification uses a
one-time-token deep link, not a login endpoint). But the design below is kept
forward-compatible anyway, at no extra cost:
- `AuthService` is a plain singleton `@Service` with `HttpServletRequest`/`Response`
  constructor-injected (Spring's standard request-scoped proxy) — it already works
  identically whether called from a Vaadin dialog or a future REST controller. Putting the
  rate-limit check **inside** `AuthService.login()` itself means any future caller (Vaadin or
  REST) is protected automatically, no rework needed.
- For registration, avoid injecting `HttpServletRequest` into `UserService` (would violate
  the starter's transport-agnostic boundary, `user-spring-boot-starter/CLAUDE.md`). Instead,
  pass the caller's IP as a plain `String` parameter —
  `UserService.register(@Valid SignUpDto dto, @NonNull String clientIp)` — and keep the
  actual Caffeine-based limiter inside `UserService` itself. The starter stays
  framework-agnostic (a `String` is not a transport dependency), while any future entry point
  (Vaadin today, a hypothetical REST signup later) just extracts an IP and passes it in —
  symmetric with the login path, no duplicated safety logic.

## Suggested fix

1. Flip to deny-by-default with an explicit permit list:
   ```java
   .requestMatchers(vaadinInternalRequestMatcher).permitAll()
   .requestMatchers("/health", "/ads/*", "/sitemap.xml").permitAll()  // explicit, documented
   .anyRequest().denyAll()
   ```
   Every new public endpoint then requires a conscious, reviewable permit entry (payment/
   telegram webhooks get their own matchers with signature verification as the real guard).
   `/health` needs an explicit entry to keep working under deny-by-default.
2. Rate limiting — **application-level, not a URL filter**, symmetric for both entry points:
   - `AuthService.login()`: Caffeine cache keyed by IP + email (short TTL, small
     max-attempts), checked before calling `authenticationManager.authenticate(...)`.
   - `UserService.register(dto, clientIp)`: same Caffeine-cache pattern, keyed by the passed
     `clientIp`; marketplace-app's `SignUpDialog` handler extracts
     `request.getRemoteAddr()` and passes it down.
   Upgrade path to bucket4j if ever needed.
3. Verify CSRF stance for each future non-Vaadin endpoint explicitly (webhooks are
   signature-verified and CSRF-exempt; browser-facing endpoints are not).

## When

Hard gate: land together with (or immediately before) the first F-01 controller/sitemap
work. Rate limiting: before public launch (Phase 1 end).
