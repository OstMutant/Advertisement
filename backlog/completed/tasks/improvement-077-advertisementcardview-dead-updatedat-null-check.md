# improvement-077: AdvertisementCardView — remove dead null-check on updatedAt

**Type:** improvement — dead code removal, found via manual UI review.
**Module:** `marketplace-app` (`ui/views/main/tabs/advertisements/AdvertisementCardView.java`).
**Priority:** low — trivial, safe, no behavior change.
**When:** anytime — no blockers, no dependencies on other items in this batch.

## Problem

`AdvertisementCardView.createMetaPanel()` computes:
```java
boolean neverEdited = ad.getUpdatedAt() == null || ad.getUpdatedAt().equals(ad.getCreatedAt());
```
`updatedAt` is `@LastModifiedDate` on the `Advertisement` entity
(`advertisement-spring-boot-starter/.../entity/Advertisement.java`), populated by Spring Data
JDBC auditing on both insert and update — it is never null on a persisted row, so the left
disjunct (`ad.getUpdatedAt() == null`) is provably dead.

**Correction to the original proposal:** only the null-check half is dead. The
`.equals(ad.getCreatedAt())` half is live, correct logic — on insert, `createdAt`/`updatedAt` are
set to the same instant by JDBC auditing, so this equality is the actual "was this ever edited"
signal. Do not remove the whole `neverEdited` computation, only the `== null ||` part.

## Suggested fix

```java
boolean neverEdited = ad.getUpdatedAt().equals(ad.getCreatedAt());
```
If a defensive null-check is still wanted for robustness against a future entity change, leave a
one-line comment explaining why — don't leave it unexplained either way.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  and improvement-078 through improvement-083 for the rest.
