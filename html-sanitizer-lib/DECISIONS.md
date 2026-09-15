# html-sanitizer-lib — Decisions

---

## ADR-001: New shared library module for HTML sanitization — narrow admission criterion

**Status:** Accepted

**Context:** `AdvertisementService` and `ProviderProfileService` each hand-rolled an identical HTML
sanitizer (OWASP `PolicyFactory` construction + Jsoup-based visible-text-length check), found as a
DRY violation during `/code-review`. A `platform-commons`-based `HtmlSanitizerUtil` was considered
and rejected first: `platform-commons`'s own governance list explicitly disallows "feature helpers
or generic utils," and unlike `YoutubeUtil` (zero external dependencies), this utility would pull
`owasp-java-html-sanitizer` + `jsoup` onto every module in the reactor transitively, not just the
two that need it.

**Decision:** New module `html-sanitizer-lib`, mirroring `query-lib`'s shape exactly (plain Java
library, no Spring Boot autoconfiguration, no `platform-commons` dependency). One class,
`org.ost.sanitizer.HtmlSanitizer.sanitize(String html, int maxVisibleTextLength)`. Both
`advertisement-spring-boot-starter` and `provider-profile-spring-boot-starter` depend on it
directly and call it from their own `buildEntity()` — the sanitize call stays inside each starter,
unconditionally on every write, so `AdvertisementPort.save()`/`ProviderProfilePort.save()` stay
safe by construction regardless of caller. A starter depending on a plain shared library module
(not another starter, not `marketplace-app`) is not a Module Import Rules violation — the same
precedent `query-lib` already establishes.

**Admission criterion, deliberately narrow — this module is not a general-purpose utils dumping
ground:** a class belongs here only when (a) genuinely needed by ≥2 starters, not "might be useful
later," and (b) it doesn't fit `platform-commons` specifically because of an external-dependency
concern `platform-commons`'s own governance rule would reject — not merely "avoids duplication" as
a standalone reason. Checked against every other starter before creating this module (direct grep,
not assumed): no other genuine duplicate-implementation candidate existed at the time
(`TaxonService.validateTranslations()`/`AttachmentVideoUtil.validateEmbedUrl()` matched a generic
"validate*" grep but are unrelated, domain-specific logic) — this module starts with exactly one
class, not pre-populated speculatively.

**Rejected alternatives:** a Hook-based or orchestrator-side sanitize call — rejected because it
makes sanitization depend on caller discipline instead of being a domain-owned guarantee (same
reasoning as `platform-commons/DECISIONS.md` ADR-027's `kind == SUPPORT` check).
