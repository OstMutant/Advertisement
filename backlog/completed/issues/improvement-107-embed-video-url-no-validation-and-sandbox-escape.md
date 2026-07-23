# improvement-107: Embed video URLs get zero scheme/host validation before landing in an iframe src; sandbox uses the escape-prone allow-scripts+allow-same-origin combo

**Type:** bug — security (stored, user-controlled URL → iframe; sandbox misconfiguration). Found
via edge-case review (2026-07-19).
**Module:** `attachment-spring-boot-starter` (`services/AttachmentService.java`),
`marketplace-app` (`ui/.../attachment/AttachmentLightbox.java`, `CardLightboxViewer.java`)
**Priority:** 🔴 high — user-supplied strings reach the DOM as iframe `src` with no validation,
while the description field beside them gets rigorous OWASP sanitization
**When:** Batch C (session security) — see `backlog/BACKLOG.md` "Execution batches"; coordinate
with improvement-081 (same two lightbox classes)

## Problem

`AttachmentService.addVideo()` / `addVideoTemp()`: for any non-YouTube, non-blank URL, the raw
string is stored verbatim as `CT_EMBED` — no scheme check, no host allowlist:

```java
if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
return new TempAttachmentDto(url, embedFilename(url), CT_EMBED, 0L);   // url stored as-is
```

At render time `AttachmentLightbox.resolveEmbedUrl()` returns the raw stored url for `CT_EMBED`
and feeds it straight into an `IFrame` `src`. So a user can persist
`data:text/html,<script>…`, `http://attacker.example`, or any arbitrary URL, and the app frames
it under its own chrome. Two compounding problems:

1. **No validation** that the URL is `http(s)://` or an embeddable video host. The description
   field (equally user-controlled) is sanitized via OWASP `Sanitizers`; the video URL — which
   ends up in the live DOM — is trusted completely. That asymmetry is the core bug.
2. **Sandbox misconfiguration:** both lightboxes set
   `sandbox="allow-scripts allow-same-origin allow-presentation"`. `allow-scripts` +
   `allow-same-origin` together is the well-known combination that lets a framed document remove
   its own sandbox attribute and re-load unsandboxed — flagged by every security scanner. For a
   same-origin-hosted embed URL that is a direct XSS-in-app-origin path.

The `TempAttachmentDto` placeholder even advertises "YouTube, Facebook..." in its input, but the
backend neither validates against those hosts nor translates a Facebook watch URL to an embed URL
(see the `improvement-055` polish note) — so the field accepts anything and frames it.

## Suggested fix

- **Validate on write** (`addVideo`/`addVideoTemp`): require `http(s)://`, parse with `URI`,
  reject non-hierarchical schemes (`javascript:`, `data:`, `file:`), and check the host against an
  allowlist of embeddable providers (YouTube, Vimeo, and whichever else is actually supported).
  Reject with a clear validation message otherwise. A resolver per provider (url → canonical
  embed url) replaces the current "store raw, hope it embeds" approach.
- **Tighten the sandbox:** drop `allow-same-origin` for third-party embeds (YouTube embed does not
  need same-origin to the app). Keep `allow-scripts allow-presentation` (+ `allow-popups` only if
  a provider needs it). Never both `allow-scripts` and `allow-same-origin` on an app-origin frame.
- Do the attribute changes in coordination with improvement-081's extraction so the hardened
  attributes live in one shared helper, not two.

## Verification

Playwright: attempt to add a `javascript:`/`data:` and a disallowed-host video URL → assert
rejection with a validation message and no attachment row created; a valid YouTube URL still
embeds. Unit-test the URL validator directly with a scheme/host matrix.

## Related

- `backlog/issues/improvement-081-lightbox-embedurl-and-iframe-attrs-duplication.md` — the dedup
  of these exact iframe attributes; land the security hardening in the shared helper it creates.
- `advertisement-spring-boot-starter/CLAUDE.md` — the description OWASP-sanitization rule this
  issue extends to the video-URL surface ("Never trust raw HTML/URL from UI").
