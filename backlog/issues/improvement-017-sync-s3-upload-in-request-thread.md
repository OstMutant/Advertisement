# improvement-017: Synchronous S3 upload blocks the Vaadin request thread

**Type:** improvement — scalability, found by external code audit (round 5), verified
**Module:** attachment-spring-boot-starter
**Priority:** medium — per-user freeze today, systemic risk largely mitigated by virtual threads
**When:** Split: step 1 (size caps) Wave 1 alongside thumbnails; step 2 (async pipeline) deferred, bundled with the thumbnail refactor
**Status:** ◐ PARTIALLY RESOLVED (2026-07-04) — step 1 done: `AttachmentUploadButton.MAX_FILE_SIZE`
lowered `500 MB → 50 MB` (`AttachmentUploadButton.java:9`), sized for realistic ad photos/short
demo videos rather than raw camera footage; bounds worst-case per-user freeze from
15-25+ min down to ~2-3 min on a slow connection. Virtual threads already on (Week 0). No
reverse proxy exists in this stack yet, so the original "Nginx `client_max_body_size`" half of
step 1 doesn't apply until one is introduced — revisit then. Whether Vaadin's `UploadHandler`
bypasses Spring's `multipart.max-file-size` entirely was not empirically confirmed — flag if
relevant when touching this area again. **Step 2 (real async pipeline) remains open, deferred
to the thumbnail refactor per the trigger below.**

## Problem

`AttachmentService.upload()` calls `storageService.upload(...)` synchronously
(`AttachmentService.java:61`, temp path at `:132`). In Vaadin the call runs on the request
thread while the Vaadin session lock is held, so an S3 latency spike (2-3 s) freezes **that
user's** UI for the duration (not the whole app — the session lock is per user, contrary to
the external reviewer's phrasing).

Two distinct risks:
1. **Per-user UX:** the uploading user's UI is unresponsive during the transfer — inherent
   to synchronous upload, partially acceptable (upload progress is expected UX), painful on
   slow S3 or big files.
2. **Systemic:** with platform threads, many concurrent uploads exhaust the servlet thread
   pool. **Largely mitigated by `spring.threads.virtual.enabled=true`** (process-improvements
   Part 3 #13, Week-0 item) — blocked virtual threads don't starve the pool. The session
   lock contention per user remains either way.

## Suggested fix (escalation order)

1. **Now (cheap):** enforce upload size limits at two levels — Vaadin `Upload` component
   `setMaxFileSize(...)` and proxy/Nginx `client_max_body_size` — bounding worst-case block
   time. Confirm virtual threads are enabled (Week-0).
2. **Later (real async):** upload to a temp location from the UI, run the S3 transfer via
   `CompletableFuture` / AWS SDK async client outside the session lock, update the gallery
   through `ui.access(...)` on completion. Do this together with thumbnail generation
   (process-improvements Part 3 #16) — same pipeline touch, one refactor instead of two.

## Trigger for step 2

User complaints about upload freezes, or media volume growth after community launch
(Phase 1-2 of the product roadmap).
