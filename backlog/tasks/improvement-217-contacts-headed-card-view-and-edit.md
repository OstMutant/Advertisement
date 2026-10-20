# improvement-217: Contacts as a headed card in view and edit modes, like feedback

**Type:** improvement — UI consistency
**Module:** `marketplace-app` (`ContactRevealPanel`, `AdvertisementViewOverlayModeHandler`, `ProviderProfileCatalogViewModeHandler`, `ProviderProfileViewModeHandler`, `AdvertisementFormOverlayModeHandler`, `ProviderProfileFormOverlayModeHandler`; `marketplace-app/src/main/frontend/themes/my-app/forms.css` only if no existing class fits)
**Priority:** medium — Top, requested so it is not forgotten; cheap to do
**When:** independent

## Current state

In the view overlays the contact-reveal panel is already a card of its own (`overlay__view-card` on `ContactRevealPanel` in `AdvertisementViewOverlayModeHandler` and `ProviderProfileCatalogViewModeHandler`, and the contact-views block in `ProviderProfileViewModeHandler`), but `ContactRevealPanel` renders only the channel buttons: it has no header. The feedback card next to it has one (`feedback-header` with a label, the rating stars and the count).

In the edit forms the contacts are not a card at all: `AdvertisementFormOverlayModeHandler` adds the contact preview block to the same `fieldsCard` as title, description, category, city and kind, and `ProviderProfileFormOverlayModeHandler` puts `phoneField`, `telegramField` and `viberField` into the same `fieldsCard` as kind, about, category and city. Both forms use one card (`overlay__form-fields-card`, header `overlay__form-card-header`).

## Why change

The contacts read as bare buttons or loose fields instead of a section, unlike feedback, and the view and edit modes of the same data look different.

## Expected benefit

The contact section is visually separated and recognisable in both modes, the same way as feedback; a later change to contact fields (a new channel) touches one card.

## Approach

1. View mode: give `ContactRevealPanel` a header like `FeedbackPanel`'s (an icon and a translated "Contacts" label, in the existing `overlay__view-card-header` style) so the section reads as a card of its own.
2. Edit forms: render the contacts as a separate card below the main form card, with the same header and the same look (the card classes the feedback card already uses in the view overlays: `overlay__view-card` inside an `overlay__view-body`-style flex-gap wrapper). The fields and the advertisement's contact preview block become the card's content; field bindings, validation and the unsaved-changes logic stay unchanged.
3. Use the existing classes; add a rule in the theme CSS only if the form layout needs one.
4. Add the section-title i18n keys (en and uk).
5. Verify visually with Playwright (provider-profile and advertisement flows) next to the feedback card in the matching view overlay, and update any selector that assumes the contact fields live inside the first card.

## Related

- improvement-200 (item 6) and improvement-203 (item 2): the same card treatment done for the view overlays
- improvement-213 phase 5: REST contacts

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
