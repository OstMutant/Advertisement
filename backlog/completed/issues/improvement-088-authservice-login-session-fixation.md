# improvement-088: AuthService.login() — no session id rotation after authentication (session fixation)

**Type:** bug — security (OWASP session fixation). Found via pattern-focused code review
(2026-07-19).
**Module:** `marketplace-app` (`services/auth/AuthService.java`)
**Priority:** high — standard web-security expectation; small, well-understood fix
**When:** independent, no blockers

## Problem

`AuthService.login()` authenticates manually — `authenticationManager.authenticate(...)` +
`securityContextRepository.saveContext(...)` — inside a Vaadin request, not through Spring
Security's form-login filter chain. Spring Security's built-in session-fixation protection
(session id rotation on authentication) lives in that filter chain, so it never runs here: the
HTTP session id is **the same before and after login**.

Attack shape: an attacker who can plant or learn a victim's `JSESSIONID` before login (shared
computer, link with a fixated cookie, XSS on a sibling subdomain) still owns that same session
after the victim authenticates.

Note the asymmetry: `logout()` already invalidates the session, but `login()` never rotates it.

## Suggested fix

Rotate the session id immediately after successful authentication, before `saveContext(...)`.
Options, either is sufficient:

1. `request.changeSessionId()` (Servlet API; keeps session attributes, changes only the id) —
   matches what Spring Security's own `ChangeSessionIdAuthenticationStrategy` does.
2. Vaadin-idiomatic: `VaadinService.reinitializeSession(VaadinService.getCurrentRequest())` —
   also migrates Vaadin's own session-scoped state safely; prefer this if plain
   `changeSessionId()` confuses Vaadin's UI/session bookkeeping.

Verify with the existing Playwright auth suite (spec 02) that login/logout still works, and add
an assertion that the `JSESSIONID` cookie value changes across a successful login.

## Related

- `marketplace-app/DECISIONS.md` ADR-026 (login rate limiting) — same method, unrelated concern.
- `scripts/ci/` e2e stage — spec 02 covers the affected flows end-to-end.
