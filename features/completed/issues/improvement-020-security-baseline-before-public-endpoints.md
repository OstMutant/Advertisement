# improvement-020: Security baseline before first public endpoints — deny-by-default + rate limiting

**Type:** improvement — security hardening, found by external code audit (round 6), verified
**Module:** marketplace-app
**Priority:** medium-high — acceptable today, mandatory BEFORE F-01 adds the first REST controllers
**When:** Wave 1 — hard gate: lands with (or before) the first public REST endpoints

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
`AuthService` already has `HttpServletRequest` injected (`request.getRemoteAddr()` available
for IP-keyed limiting) — `UserService.register()` does not, and per the starter's "no
transport-layer dependency" boundary (`user-spring-boot-starter/CLAUDE.md`), it shouldn't
gain one just for this; the registration rate-limit check should live in marketplace-app
(e.g. wrapping the call from `SignUpDialog`'s handler) rather than inside the starter.

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
2. Rate limiting for login/signup — **application-level, not a URL filter**: a Caffeine cache
   (key: IP + email, short TTL, small max-attempts) checked at the top of
   `AuthService.login()` (IP already available there) and in a marketplace-app-side guard
   around the `SignUpDialog` → `UserService.register()` call (keeps user-spring-boot-starter
   transport-agnostic). Upgrade path to bucket4j if ever needed.
3. Verify CSRF stance for each future non-Vaadin endpoint explicitly (webhooks are
   signature-verified and CSRF-exempt; browser-facing endpoints are not).

## When

Hard gate: land together with (or immediately before) the first F-01 controller/sitemap
work. Rate limiting: before public launch (Phase 1 end).
