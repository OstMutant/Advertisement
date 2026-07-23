# improvement-063: no "ready" signal for async-initialized custom components — potential Playwright flakiness

**Type:** improvement — test tooling/process. Raised via direct review discussion (2026-07-16), not
a currently-observed failure — a preventive measure, not a bug fix.
**Module:** `playwright/` (test conventions), `marketplace-app` (`QuillEditor`, `AttachmentGallery`
and any other custom Vaadin web components with async JS initialization).
**Priority:** low — no flaky failure from this specific cause has actually been observed/reported in
this suite yet; filed to track the risk and the fix shape before one shows up, not to chase a
current symptom.
**When:** independent, no blockers.

## Problem

`QuillEditor` (`ui/views/components/fields/QuillEditor.java`, `@JsModule("./quill-editor.js")`) and
similar custom Vaadin components (`AttachmentGallery`) initialize their underlying JS/web-component
behavior asynchronously after the element lands in the DOM (`connectedCallback()` in
`quill-editor.js`, confirmed directly — no existing "ready" signal beyond a `value-changed` custom
event fired after user interaction, not on init completion). A Playwright interaction
(`page.locator(...).click()`) that fires as soon as the element exists in the DOM, but before its
JS has finished attaching listeners/rendering internal state, can race and either silently no-op or
hit a stale/partial DOM structure.

This is a preventive issue, not a reported bug: no specific flaky Playwright failure has been
traced to this cause in this suite so far. Filed because the risk is real and structural (confirmed
by reading the actual async init code), and the fix is cheaper to design once, up front, than to
retrofit after a hard-to-reproduce flaky failure eventually surfaces.

## Suggested fix

Introduce an explicit "ready" signal on async-initializing custom components — e.g. a
`ready`/`initialized` boolean attribute (or a dedicated CSS class) the component's own JS sets once
its internal setup (Quill instance construction, listener attachment, etc.) actually completes, not
just once the custom element is connected to the DOM. Playwright specs interacting with these
components would then wait on that attribute/class (`page.locator('quill-editor[ready]')` or
equivalent) before the first interaction, instead of relying on bare element presence.

Scope this to components that actually have non-trivial async JS init (`QuillEditor`,
`AttachmentGallery` at minimum — audit for others sharing the same `@JsModule` + `connectedCallback`
shape before considering this done) rather than adding it project-wide to every Vaadin component,
most of which have no such async gap.

## Related

- `marketplace-app/src/main/frontend/quill-editor.js` — the async `connectedCallback()` this issue
  is about, confirmed to have no existing readiness signal.
- `playwright/CLAUDE.md` — existing Vaadin/Shadow-DOM Playwright conventions this would extend.
- `.claude/rules.md` "No waitForTimeout in Playwright" (project memory) — this issue's fix is the
  correct alternative to a blind `waitForTimeout()` workaround for this specific class of race.
