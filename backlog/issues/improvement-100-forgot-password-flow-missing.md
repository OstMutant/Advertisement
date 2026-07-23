# improvement-100: No password-recovery flow — login dialog has no "Forgot password?"

**Type:** improvement — product gap (account recovery). Found via UX review over the 2026-07-19
e2e screenshot set.
**Module:** `marketplace-app` (login dialog, new recovery UI), `user-spring-boot-starter`
(recovery tokens, password reset), new infrastructure: outbound email
**Priority:** medium — irrelevant for the current dev/test audience, blocking for any real public
audience (a locked-out user is a lost user with no self-service path)
**When:** Deferred — trigger: project nearing public launch (same gate as improvement-052);
requires an email-infrastructure decision first, which nothing else in the project needs yet

## Problem

The Sign In dialog offers Email + Password + Log In/Cancel — no "Forgot password?" affordance,
and no recovery mechanism exists anywhere in the stack (no mail sender dependency, no reset-token
table, no reset endpoint). A user who forgets their password has no path back except asking an
admin to... nothing — admins can't reset passwords either (`UserProfileUpdate` deliberately
excludes `passwordHash`, ADR-029).

## Suggested fix (design decision first)

1. **Email infrastructure decision** — the prerequisite and the real cost: SMTP provider vs.
   transactional-mail API, dev-profile stub (log-only sender), prod secrets via env vars
   (consistent with the committed-no-secrets `.env` rule).
2. Standard token flow: `password_reset_token` table (user starter changelog; single-use,
   hashed token, short TTL), "Forgot password?" link in the login dialog → email-entry dialog
   (same response whether or not the email exists — no account enumeration), email link →
   reset form → `UserService.resetPassword(token, newPassword)`.
3. Rate-limit token requests per IP+email with the existing Caffeine pattern (ADR-026); audit
   the reset with the existing `captureUpdate` machinery (settings-style snapshot, no secrets in
   snapshots).
4. Playwright coverage with the dev log-only sender (assert the logged link works end-to-end).

## Related

- `backlog/issues/improvement-052-first-admin-registration-toctou-race.md` — same
  "before public launch" trigger; natural companions in a pre-launch hardening pass with
  improvement-088.
- `user-spring-boot-starter/CLAUDE.md` — `DelegatingPasswordEncoder` note: reset writes go
  through the same encoder, no migration concerns.
