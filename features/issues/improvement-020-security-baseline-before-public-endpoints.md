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

Today this is a lax-but-workable posture: the app is a single-route server-side Vaadin UI,
there is no REST surface, and view access is enforced manually (`AccessEvaluator`) with tabs
hidden per role. The external reviewer's "CRITICAL" rating overstates the current risk.

It becomes a real hole exactly at product Phase 1: the roadmap adds servlet/REST endpoints —
the OG bot controller and sitemap (F-01), later the Telegram webhook (F-07), actuator
(process-improvements P3 #17), payment webhooks (F-08). Under `anyRequest().permitAll()`
every one of them is born public by default, and nobody will remember to secure each one.

Related known gap in the same area: no rate limiting on login/signup (brute force / signup
spam), previously flagged in the strategic memo hidden-debt list.

## Suggested fix

1. Flip to deny-by-default with an explicit permit list:
   ```java
   .requestMatchers(vaadinInternalRequestMatcher).permitAll()
   .requestMatchers("/health", "/ads/*", "/sitemap.xml").permitAll()  // explicit, documented
   .anyRequest().denyAll()
   ```
   Every new public endpoint then requires a conscious, reviewable permit entry (payment/
   telegram webhooks get their own matchers with signature verification as the real guard).
2. Rate limiting on the auth endpoints: simple in-memory bucket (Caffeine) per IP for
   login/signup attempts — no new infrastructure; upgrade path to bucket4j if ever needed.
3. Verify CSRF stance for each future non-Vaadin endpoint explicitly (webhooks are
   signature-verified and CSRF-exempt; browser-facing endpoints are not).

## When

Hard gate: land together with (or immediately before) the first F-01 controller/sitemap
work. Rate limiting: before public launch (Phase 1 end).
