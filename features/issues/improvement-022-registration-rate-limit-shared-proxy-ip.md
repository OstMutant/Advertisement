# improvement-022: Registration rate limiter keys on raw remoteAddr — one shared bucket behind a proxy

**Type:** improvement — security/reliability, found by external code audit (round 7), verified
**Module:** marketplace-app, user-spring-boot-starter
**Priority:** high — real deployment target (Render) makes this directly exploitable, not
theoretical
**When:** before public launch — same gate as the rest of improvement-020's security baseline

## Problem

`UserService.register(dto, clientIp)` rate-limits registration failures keyed **only** on
`clientIp`:

```java
AtomicInteger attempts = registerAttempts.get(clientIp, _ -> new AtomicInteger(0));
if (attempts.get() >= MAX_REGISTER_ATTEMPTS) {
    throw new IllegalStateException("Too many failed registration attempts, try again later");
}
```

`clientIp` is populated in `SignUpDialog.handleRegistration()` via
`request.getRemoteAddr()`. The project's README documents the deployed target: `README.md:6-7`
links a live Render deployment, and `README.md:95-96` confirms Render builds directly from the
`Dockerfile` (no custom infra layer). `server.forward-headers-strategy` is not set anywhere in
the codebase (verified via grep across `*.yml`/`*.java`) — Spring Boot's default is to **not**
trust `X-Forwarded-For`/`Forwarded` headers.

Render, like essentially all PaaS providers, terminates the connection at its own edge/load
balancer and forwards to the app instance over its internal network. Without
`forward-headers-strategy` configured, `HttpServletRequest.getRemoteAddr()` returns Render's
internal proxy address — **the same value for every user hitting the app** — not the real
client IP.

**Consequence:** the registration limiter's key effectively collapses to one shared bucket for
the entire platform. Five failed registration attempts *from anyone* within the 15-minute
window (even organic ones — e.g. two real users racing to register the same not-yet-taken
email, hitting the `DuplicateKeyException` path added in improvement-020) lock out
**registration for all users**, not just the actor causing the failures.

This is distinct from the sibling login limiter (`AuthService.login()`), which keys on
`remoteAddr + "|" + email` — even with a fully collapsed `remoteAddr`, the email component
still scopes lockout to one target account. Only the registration path is a true shared-bucket
risk, because a brand-new registration has no existing account to scope the key by.

## Suggested fix

1. Configure `server.forward-headers-strategy: framework` (or `native`, depending on how Render
   terminates TLS — `framework` is the safer default for typical PaaS setups where Spring
   handles the translation itself) in `application-prod.yml`, so
   `HttpServletRequest.getRemoteAddr()` (and `request.getRemoteAddr()` in `SignUpDialog`)
   reflects the real client IP once Render forwards it via `X-Forwarded-For`.
2. Verify Render actually sends `X-Forwarded-For` (most PaaS do by default) — if not, the fix
   requires reading the header explicitly rather than relying on the servlet container's
   translation.
3. Defense in depth regardless of (1)/(2): consider adding a coarser, higher-ceiling *global*
   registration-failure limit (e.g. 50/hour across all clients) as a backstop against exactly
   this kind of key-collapse scenario, so a single misconfigured deployment doesn't turn into a
   full platform lockout even if per-IP scoping silently breaks again in the future.

## When

Before public launch — same urgency tier as the rest of the improvement-020 security baseline
this builds on.
