---
paths: ["html-sanitizer-lib/**"]
---

## html-sanitizer-lib

Plain Java library, no Spring Boot autoconfiguration, no domain knowledge — mirrors `query-lib`'s
shape. Provides `HtmlSanitizer.sanitize(html, maxVisibleTextLength)`
(`org.ost.sanitizer.HtmlSanitizer`), used by `advertisement-spring-boot-starter`'s
`AdvertisementService` and `provider-profile-spring-boot-starter`'s `ProviderProfileService` to
sanitize rich-text input and enforce a visible-text-length cap, replacing what used to be two
near-identical private implementations.

---

## Admission criterion

A utility belongs in this module only when (a) genuinely needed by ≥2 starters, not "might be
useful later", and (b) it doesn't fit `platform-commons` specifically because of an
external-dependency concern `platform-commons`'s own governance rule would reject — not merely
"avoids duplication" as a standalone reason. This module starts with exactly one class; do not
pre-populate it speculatively.
