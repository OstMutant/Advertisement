# improvement-052: `UserService.register()` first-admin TOCTOU race — accepted risk, deferred to pre-production hardening

**Type:** security hardening, deliberately deferred — not a bug to fix now.
**Module:** `user-spring-boot-starter` (`UserService.register()`).
**Priority:** lowest — narrow trigger window, accepted risk for now; revisit only when the project
is nearing production readiness.
**When:** trigger-based — see "Trigger to revisit" below. Do not implement any of the options
listed here until that trigger fires.

## Problem

Extracted from [improvement-050](../completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
item 1, decided separately since items 3/4/5 of that issue are pure engineering fixes while this
one needs a deliberate product/security-posture decision, not an engineering call.

`/app/user-spring-boot-starter/src/main/java/org/ost/user/services/UserService.java:97-103`

```java
boolean isFirstUser = repository.countByFilter(UserFilterDto.empty()).equals(0L);
User newUser = User.builder()...role(isFirstUser ? Role.ADMIN : Role.USER)...build();
```

`@Transactional` at PostgreSQL's default `READ COMMITTED` isolation does not prevent two
concurrent transactions from both observing `count() == 0` before either commits its `INSERT`.
**Failure scenario:** two people register within the same instant on a freshly-deployed, empty
instance — both could be promoted to `ADMIN`.

**Reachability:** narrow — only matters in the exact window of an instance's very first-ever
registration (once that window passes, `isFirstUser` is permanently `false` for everyone else).
Real but low-frequency, not a standing risk.

## Decision (2026-07-15)

**Accepted as-is for now — documented, not silently ignored.** No code change. Revisit only per
the trigger below.

Three options were considered (full writeup preserved for when this is revisited):

1. **Accept the risk (chosen for now).** Zero cost. The window really is narrow — exploiting it
   requires knowing the exact moment of a fresh deploy and racing the operator's own first
   registration.
2. **DB-level fix (unique partial index / advisory lock).** Structurally closes the race without
   changing onboarding UX, but needs a new bootstrap-flag column + partial unique index + retry-on
   -conflict logic (same shape as the existing `DuplicateKeyException` handling for duplicate
   emails). Medium effort; the race itself is also inherently hard to test deterministically
   (needs two concurrent transactions with controlled synchronization). Not chosen now — the
   complexity isn't justified by a risk this narrow, today.
3. **Remove automatic first-user-admin entirely.** Every registration becomes `Role.USER`; the
   first admin is granted via an explicit out-of-band step (CLI script, manual `UPDATE`, or an
   `ADMIN_BOOTSTRAP_EMAIL`-style config flag). Cheapest code change (delete the `isFirstUser`
   branch), but changes the deploy UX: today `docker-compose up` + sign-up produces a working
   admin with zero extra steps; this option requires an explicit post-deploy action. Arguably a
   *better* security default regardless of the race (implicit "whoever registers first is
   trusted" is a questionable model on its own merits) — worth strong consideration when this is
   revisited, not just as a race-condition fix.

## Trigger to revisit

Revisit when the project is nearing actual production deployment (real users, real exposure to
the public internet) — not before. At that point, re-evaluate options 2 and 3 above with the
then-current onboarding/deployment story in mind; option 3's UX tradeoff may look different once
there's a real "getting started" flow to design around.

## Related

- [improvement-050](../completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md) — item 1's
  original filing; items 2/3/4/5 of that issue are all done and it's closed. This issue (item 1)
  is the only piece deliberately left open, for a different kind of reason — a product/security
  -posture decision, not an engineering gap.
- `user-spring-boot-starter/CLAUDE.md` — "First registered user is auto-promoted to `ADMIN` role
  — enforced in `UserService`" documents the current (unchanged) behavior this issue is about.
