# improvement-076: AdvertisementCardView — remove redundant stopPropagation listeners

**Type:** improvement — dead code removal, found via manual UI review.
**Module:** `marketplace-app` (`ui/views/main/tabs/advertisements/AdvertisementCardView.java`).
**Priority:** low — trivial, safe, no behavior change.
**When:** anytime — no blockers, no dependencies on other items in this batch.

## Problem

`AdvertisementCardView.createEditButton()` and `createDeleteButton()` each call
`.getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()")`
directly on the button built via `editButtonFactory`/`deleteButtonFactory`. This is redundant:
`BaseActionButton.applyConfig()` (`ui/views/components/buttons/action/BaseActionButton.java:23`)
already registers the identical `stopPropagation` listener for every button extending it —
including `EditActionButton`/`DeleteActionButton`, the only two `*ActionButton` subclasses in the
repo (`QueryActionButton` is a separate family, extends `Button` directly, unrelated). Both
listeners fire on every click since Vaadin allows multiple listeners per DOM event type — the
second registration is inert.

## Suggested fix

Delete the redundant `.getElement().addEventListener(...)` lines in both `createEditButton()` and
`createDeleteButton()`. Verified safe: `EditActionButton`/`DeleteActionButton` are the only
`*ActionButton` subclasses in the codebase and both already go through `BaseActionButton`.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-077
  through improvement-083 for the rest.
