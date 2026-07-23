# improvement-011: UI components hard-inject starter ports, breaking graceful degradation

**Status:** ✅ RESOLVED (2026-07-13) — Option A implemented: `AttachmentGalleryService`,
`AttachmentGallery`, `AuditActivityPanel` now inject `ComponentFactory<AttachmentPort>` /
`ComponentFactory<AuditPort>` instead of the raw port. The consolidated "Option C" variant (gating
the three component classes themselves with `@ConditionalOnBean`) was tried first, deployed, and
**empirically broke the app** (Playwright e2e went from 48/48 to 5 failed/35 skipped) due to a
Spring Boot bean-registration-ordering issue: `@ConditionalOnBean` on a `@ComponentScan`-discovered
class evaluates before the target starter's `@AutoConfiguration` class registers its bean, so the
condition sees the port as absent even when it is genuinely present, and the component silently
never registers. Reverted that approach entirely — see `marketplace-app/DECISIONS.md` ADR-033 for
the full root-cause writeup and the corrected fix (plain `ComponentFactory<Port>` wrapping, with
the availability gate moved to the port's own factory at every call site). Full e2e suite verified
48/48 green.

**Type:** improvement — architectural, found during coupling/SOLID review
**Module:** marketplace-app
**Priority:** medium — app startup crash if attachment starter is removed from classpath
**When:** Wave 2 — the Option A/C decision must precede creation of any new starter

## Problem

The architecture rule (root CLAUDE.md, marketplace-app CLAUDE.md) requires UI components to
degrade gracefully via `ObjectProvider.ifAvailable()` when an optional starter is absent. At
the starter level this holds (`AdvertisementService` uses `ComponentFactory<...>` guards
everywhere), but three marketplace-app UI classes hard-inject starter ports directly:

| Class | Injection | Scope | Failure mode without the starter |
|-------|-----------|-------|----------------------------------|
| `AttachmentGalleryService` | `private final AttachmentPort attachmentPort` | **singleton** `@SpringComponent` | **context fails at startup** — eager singleton with unsatisfiable dependency |
| `AttachmentGallery` | `private final AttachmentPort attachmentPort` | prototype | exception on first `build()` |
| `AuditActivityPanel` | `private final AuditPort auditPort` | prototype | exception on first `build()` |

The call-site guards (`galleryServiceFactory.ifAvailable(...)`) do NOT protect against this:
the bean *definitions* live in marketplace-app and always exist, so
`ObjectProvider.getIfAvailable()` attempts instantiation and throws
`UnsatisfiedDependencyException` instead of returning null. For the singleton
`AttachmentGalleryService` even that point is never reached — the application context fails
during startup.

Net effect: attachment and audit starters are effectively mandatory today, contradicting both
the stated architecture rule and the `<optional>true</optional>` declarations in
advertisement-spring-boot-starter's pom.xml.

## Suggested fix

Pick one direction and apply it consistently:

**Option A — make degradation real (matches the stated rule):**
Replace direct port fields with `ComponentFactory<AttachmentPort>` / `ComponentFactory<AuditPort>`
in the three classes, resolving via `findIfAvailable()` inside methods. Additionally gate the
component beans themselves with `@ConditionalOnBean(AttachmentPort.class)` /
`@ConditionalOnBean(AuditPort.class)` so `getIfAvailable()` on the component returns null
cleanly when the starter is absent.

**Option B — declare reality (starters are mandatory):**
Remove `<optional>true</optional>` from advertisement-spring-boot-starter's pom.xml, drop the
graceful-degradation clause for attachment/audit from CLAUDE.md, and keep direct injections.
Simpler, but gives up modular deployability.

## Decisive argument (added 2026-07-04): Option B does not survive the product roadmap

Upcoming starters (`payment-`, `telegram-`, `ai-spring-boot-starter` — see private roadmap)
have **environment-dependent activation**: acquiring credentials, bot tokens, AI API keys.
Dev/test environments must boot without them. Optionality therefore stops being theoretical
the moment Phase 4 starts — making all starters mandatory would have to be reverted within
months. **Choose Option A (or the consolidated variant below), record it in
`marketplace-app/DECISIONS.md` as the pattern for all upcoming starters.**

Consolidated variant of Option A ("Option C"): put `@ConditionalOnBean(XxxPort.class)` on the
`UiComponentFactory` **bean declarations** in `MarketplaceUiConfiguration` (one place per
subsystem, not per component class); for read-mostly ports (Audit, Taxon) optionally add a
`@ConditionalOnMissingBean` no-op fallback that returns empty results and logs WARN once at
startup. Avoid `@Lazy` proxies — they defer the crash to first use instead of preventing it.

Option A preserves the architecture's stated goal; Option B is honest about current usage but
conflicts with the roadmap. Decision should be recorded in `marketplace-app/DECISIONS.md`
either way.

## Related

- `docs/architecture/06-coupling-analysis.md` — "Optional Dependencies Without Guards" section
  (the original starter-level concern is resolved; this issue is the residual risk relocated
  to the UI layer).
- `backlog/completed/issues/improvement-001-attachment-ui-boundary-violation.md` — the
  refactor that replaced internal service injections with `AttachmentPort` (correct move, but
  it introduced the direct port injections this issue covers).
