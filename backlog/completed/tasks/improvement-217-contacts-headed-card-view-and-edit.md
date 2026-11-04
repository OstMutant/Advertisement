# improvement-217: Contacts as a headed card in view and edit modes, like feedback

**Type:** improvement — UI consistency
**Module:** `marketplace-app` (`ContactRevealPanel`, `AdvertisementViewOverlayModeHandler`, `ProviderProfileCatalogViewModeHandler`, `ProviderProfileViewModeHandler`, `AdvertisementFormOverlayModeHandler`, `ProviderProfileFormOverlayModeHandler`; `marketplace-app/src/main/frontend/themes/my-app/forms.css` only if no existing class fits)
**Priority:** medium — Top; phase 1 done, phase 2 open, phase 3 deferred
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

## Execution plan

1. `ContactRevealPanel.configure()`: first child is a `Div.overlay__view-card-header` (`VaadinIcon.PHONE` + `Span(CONTACT_SECTION_LABEL)`), added only when a channel exists; `setVisible(getComponentCount() > 1)`.
2. `I18nKey.CONTACT_SECTION_LABEL("contact.section.label")`; en `Contacts`, uk `Контакти` (unicode-escaped).
3. `ProviderProfileFormOverlayModeHandler`: phone/telegram/viber fields move from `fieldsCard` into a new `contactsCard` (`overlay__form-fields-card`, header `overlay__form-card-header`), content = `Div(tabBar, fieldsCard, contactsCard)`.
4. `AdvertisementFormOverlayModeHandler.buildContent()`: `contactPreview` moves into its own card right after `fieldsCard`, before the gallery.
5. CSS only if spacing needs it; check `.overlay__form-fields-card + .attachment-gallery` in `advertisement-overlay.css`.
6. Check Playwright selectors (`04-provider-profile-flow`, `06-seed-filter-sort-pagination`), then visual check with `--ux`.
7. Update this file's Operational notes.

Status: steps 1-4 done 2026-10-27 (Haiku agent; `CONTACT_SECTION_LABEL` moved to the end of the contact-reveal key group afterwards). Step 5 done 2026-10-27: no CSS change needed (the contacts card carries `overlay__form-fields-card`, so the gallery sibling rule still matches). Step 6 selector check done 2026-10-27: the Playwright specs use test ids and `.contact-reveal-*` / `.advertisement-contact-preview*` classes, none assume the first card; the build and the visual run are still open. Step 7 open.

Follow-up 2026-10-27: the Account overlay's Provider Profile view card (`ProviderProfileViewModeHandler.buildContactViewsBlock`) gets the same headed Contacts header (Haiku agent). `04-provider-profile-flow` "user edits provider profile" gains steps for the owner (view and edit), another signed-in user (catalog card, reveal buttons only) and an administrator (Users grid, view and edit), each in its own session, with screenshots.

## Phases

Reopened 2026-10-27 after an in-depth research (plan below, topic folder removed).

- **Phase 1 -- headed Contacts card in view and edit: done 2026-10-27.** Includes the Account overlay Provider Profile view header and the role-based Playwright steps (owner, another user, administrator).
- **Phase 2 -- contacts only from the provider profile:** steps 2-6 below.
- **Phase 3 -- author link to the provider profile: deferred, not doing for now.** Moved to entry 6 of improvement-223 (ideas collection) when this task closed; steps 8-13 stay below as the reference plan.

### Decisions (2026-10-27)

- Keep the Contacts card on the advertisement view (filled from the author's provider profile).
- `contact_info` stays generic over the owning entity (recommended; the dead own-contact lookup is removed); `contact_view` stays generic.
- Label the owner's monthly counters as views on the profile page (step 6); do not count ad reveals.
- View modes (catalog, account, advertisement) show no empty-state line and no hint when there are no contacts; the provider profile edit form keeps its Contacts card with the empty fields, since the fields are how contacts are added (the research's optional steps for the view-mode empty states are dropped).
- Reveal stays open to visitors who are not signed in.
- The author-email exposure is left as is for now and not added to the deferred findings list.

## Plan for people (from the research)


### Goal

Answer four questions about contacts and authorship on advertisements, then plan the rest of
improvement-217:

- **Phase 1 is done** (2026-10-27): contacts are a headed card in the view and edit screens.
- **Phase 2**: an advertisement's contacts come only from its author's provider profile, and the
  code and documentation say so.
- **Phase 3**: the author's name on ad cards and in the ad view becomes an accented link that opens
  the author's provider profile. An administrator gets Edit there as usual.

### The problem this solves

- The code still has a way for an ad to have its own contacts. Nothing writes such contacts. That
  option was dropped in improvement-199 (2026-09-25), but a decision record, two Javadoc comments,
  the database remarks and an API description still describe it. A reader is told something that
  is not true.
- A buyer who sees an ad cannot open the author's profile from it. On cards and in the ad view the
  author is plain text.
- Empty contacts look different on each screen, with no rule behind the differences.

### Answers to the four questions

**1. Keep a Contacts card on the ad, or show contacts only on the profile?**
Proposal: **keep the card on the ad**, filled from the author's profile, and add the author link.
Classified-ad sites (OLX, Craigslist, Avito) and eBay's "Contact seller" put the contact action on
the listing itself. One counter-example: eBay UK moved business sellers' contact details to the
seller profile, a click or two away, because of a legal disclosure rule. Keeping the card saves the
buyer a click and keeps the per-ad reveal count meaningful. This matches what you already lean
toward.
- Privacy and scraping: the app already follows the usual practice. Values only reach the browser
  after a click, through the server. Reveals are limited to 30 per 15 minutes per address (and per
  user when signed in).
- The OWASP bot-management guidance layers limits and step-up checks. It does **not** recommend a
  sign-in wall.
- OLX Poland put phone numbers behind sign-in on some listings in 2021, to stop people collecting
  numbers for telemarketing. Whether reveal should require sign-in is a decision for you (below).

**2. The author link.**
- The link text is the author's name, in the accent color.
- When the author has no provider profile, the name stays plain text. Moderation already works this
  way.
- The link reuses the path Moderation already uses: it switches to the Providers tab, opens the
  profile, and on close returns to the tab you came from.
- Batching: each page of ads already loads all authors' names in one query. Proposal: in the same
  step, also load each author's profile id in **one extra query per page**. One new lookup method
  is added to the provider-profile module. The REST API's ad JSON then also carries the profile id,
  which helps API clients at no extra cost.

**3. The contacts table.**
Proposal: **keep it generic** (owner type + owner id). Remove the dead "ad's own contacts" lookup and
fix the text that describes it.
- The usual best-practice argument against generic owner columns is that the database cannot
  check the link (GitLab's guidelines, Karwin's *SQL Antipatterns*). That argument does not help
  here. This project deliberately has no links between modules' tables, so a profile-only column
  would not be checked either.
- Narrowing the table would make the contacts module name another module's concept. It would also
  break the pattern shared by the audit, contact, feedback and attachment tables.
- The reveal log (`contact_view`) stays generic, as already decided.

**4. Empty contacts.**
Design guidance (Nielsen Norman Group, IBM Carbon, GOV.UK) says that a person who can fill an empty
section should see that it is empty, with a direct way to fill it. A visitor who cannot fill it does
not need to see it.
- **Catalog**: the card is hidden. This fits; keep it.
- **Edit form**: the empty fields are shown. This fits, because the fields are the way to fill it.
- **Account view**: the owner sees a card with only its caption. This is a near-empty card with no
  message, so the guidance points to a short "No contacts yet" line.
- **Ad create/edit form**: when the profile has no contacts, the form shows nothing. The author is
  not told where to add contacts.

Both of the last two are left for you to decide.

### The means needed

- No new library, no new setting, no database change. If you choose to narrow the table, that is a
  change to the existing first contacts script.
- Code that already exists and is reused:
  - Moderation's tab switch to the Providers tab and its "open profile, return on close" behavior.
  - The provider catalog screen, which already gives an administrator Edit.
  - The per-page author lookup.
  - The headed card styles.
  - The Playwright fixtures for visitor, user and administrator.
- Decision records change only through `/record-decision`, as the project requires.

### What the result will look like

- **Ad card and ad view**: "Author: Ivan" appears as an accented link when Ivan has a provider
  profile. Clicking it, or pressing Enter on it, opens Ivan's profile with its contacts and feedback.
  Closing the profile returns to the ad.
- **Ads whose author has no profile**: the name is plain text and nothing else changes.
- **The ad view**: the Contacts card stays, showing the author's profile contacts, as today.
- **The code and documentation** say one thing: contacts belong to the provider profile, and an ad
  only shows its author's profile contacts.
- **Tests** cover these cases with Playwright steps for each role: a visitor, the author, another
  user, an administrator, and an author without a profile.

### Cost

| Phase | Effort | Executor work (estimate) |
|---|---|---|
| 2: contacts only from the profile | about 1-2 hours of supervised work | 2 Haiku dispatches (about 200-250k Haiku tokens), 1 Sonnet step for the decision-record notes (about 80-150k) |
| 3: author link | about half a day to a day, most of it the UI wiring and the e2e run | 3 Haiku dispatches (about 300k Haiku tokens), 4 Sonnet steps (about 600-900k Sonnet tokens) |
| Verification (both) | about 18 min per full run (unit, integration and full e2e together, as measured in phase 1) | no tokens for the scripts themselves |

The token figures are estimates. The one Haiku dispatch in phase 1 used about 109k tokens for four
edits, and they are scaled from that. Your own conversation's tokens are not included.

### Risks

- **Opening a profile from inside an open ad.** The profile screen sets the browser address to the
  plain list address when it closes, while the ad is still open underneath. Phase 3 must put the ad
  address back. Moderation never met this because it has no address of its own.
- **A link inside a clickable card.** Clicking or pressing Enter on the author link must not also
  open the ad. The card already uses the same "stop the click here" technique for its photo.
- **Owner's monthly counters miss ad reveals.** The counters in the account view only count reveals
  made on the profile page. Reveals made on the owner's ads are stored but shown nowhere. Keeping
  the ad card makes this gap larger. See decision 3.
- **Database remarks.** Changing remarks in the first contacts script needs a database reset to show
  up in an existing database. The project allows the reset, because the data may be wiped.
- **REST JSON gains a field** (`ownerProviderProfileId`). This adds to the API and breaks no client.

### Where the project's practice differs from best practice

1. **The link element.**
   - Best practice (MDN, the Vaadin docs): navigation should be a real link with an address. That
     gives keyboard and screen-reader support, "open in new tab" and copy-link for free.
   - The project's precedent (Moderation's profile link, clickable cards): a text element with a
     server click handler and no address.
   - The plan follows the project. It adds keyboard focus and Enter, the same as cards. Switching to
     real links is decision 4.
   - Cost of switching: an address per link, and stopping the browser's own navigation on a plain
     click. Moderation would need the same change for consistency.
2. **Generic owner columns.**
   - GitLab's guidelines and Karwin say to avoid them.
   - The project uses them on purpose, in five tables, without database links between modules.
   - The plan keeps the project's way. Narrowing the table is decision 2.
3. **Author email shown to everyone** (found next to this task, not part of it).
   - The author's email is in the hover text of every ad card and in the public REST ad JSON.
   - Meanwhile phone numbers are protected behind a limited reveal. Data-minimisation practice
     (GDPR Article 5(1)(c)) and the anti-scraping practice above both argue against this.
   - Not in this plan. It is proposed for the deferred findings list (decision 6).


## Execution plan -- phases 2 and 3

Each step is presented and approved before it runs; `git add` after every change; no commits unless the user asks.

### Step 2 -- `resolveContact` without the dead ADVERTISEMENT own-row lookup, and its unit tests

**Done 2026-10-27** (Haiku agent, diff checked; `build-and-test --unit --sandbox` green, 851 tests, `ContactAccessServiceTest` 14).

- **Depends on:** none (task reopened).
- **Files:**
  - `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ContactAccessService.java`
  - `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/ContactAccessServiceTest.java`
- **Executor:** haiku.
- **Input:** both files as they are now.
- **Change**, in `ContactAccessService.java`:

  Old:
  ```java
  /** Wraps {@link ContactPort} for marketplace-app, adding the ADVERTISEMENT-to-owner's-PROVIDER_PROFILE contact fallback via {@link AdvertisementOwnerProfileLookupService}. */
  ```
  New:
  ```java
  /** Wraps {@link ContactPort} for marketplace-app; an advertisement shows its owner's PROVIDER_PROFILE contact, looked up via {@link AdvertisementOwnerProfileLookupService}. */
  ```

  Old:
  ```java
      public Optional<ContactInfoDto> resolveContact(@NonNull EntityType entityType, @NonNull Long entityId) {
          return contactPortFactory.findIfAvailable().flatMap(port -> {
              Optional<ContactInfoDto> own = port.find(entityType, entityId);
              if (own.isPresent() || entityType != EntityType.ADVERTISEMENT) {
                  return own;
              }
              return advertisementOwnerProfileLookupService.findOwnerProfileId(entityId)
                      .flatMap(profileId -> port.find(EntityType.PROVIDER_PROFILE, profileId));
          });
      }
  ```
  New:
  ```java
      /** A provider profile's own contact, or an advertisement's owner's provider-profile contact; any other entity type is rejected. */
      public Optional<ContactInfoDto> resolveContact(@NonNull EntityType entityType, @NonNull Long entityId) {
          return contactPortFactory.findIfAvailable().flatMap(port -> switch (entityType) {
              case PROVIDER_PROFILE -> port.find(EntityType.PROVIDER_PROFILE, entityId);
              case ADVERTISEMENT -> advertisementOwnerProfileLookupService.findOwnerProfileId(entityId)
                      .flatMap(profileId -> port.find(EntityType.PROVIDER_PROFILE, profileId));
              default -> throw new IllegalArgumentException("No contact for entity type " + entityType);
          });
      }
  ```

  In `ContactAccessServiceTest.java`:
  1. Replace the class Javadoc line
     `/** Unit tests for {@link ContactAccessService}, focused on the ADVERTISEMENT-to-PROVIDER_PROFILE fallback. */`
     with
     `/** Unit tests for {@link ContactAccessService}, focused on resolving an advertisement to its owner's PROVIDER_PROFILE contact. */`
  2. Delete the constant `AD_OWN_CONTACT` (both lines, starting
     `private static final ContactInfoDto AD_OWN_CONTACT = new ContactInfoDto(`).
  3. Delete the whole test method
     `resolveContact_advertisementHasOwnContact_returnsItWithoutFallback` (its `@Test` line too).
  4. Replace the test `resolveContact_advertisementNoOwnContact_fallsBackToOwnerProfile` with:
     ```java
         @Test
         void resolveContact_advertisement_returnsOwnerProfileContactWithoutReadingAnAdvertisementRow() {
             when(advertisementOwnerProfileLookupService.findOwnerProfileId(10L)).thenReturn(Optional.of(99L));
             when(contactPort.find(EntityType.PROVIDER_PROFILE, 99L)).thenReturn(Optional.of(PROFILE_CONTACT));

             assertThat(service.resolveContact(EntityType.ADVERTISEMENT, 10L)).contains(PROFILE_CONTACT);
             verify(contactPort, never()).find(EntityType.ADVERTISEMENT, 10L);
         }
     ```
  5. Replace the test `resolveContact_advertisementNoOwnContactAndNoOwnerProfile_returnsEmpty` with:
     ```java
         @Test
         void resolveContact_advertisementOwnerHasNoProfile_returnsEmpty() {
             when(advertisementOwnerProfileLookupService.findOwnerProfileId(10L)).thenReturn(Optional.empty());

             assertThat(service.resolveContact(EntityType.ADVERTISEMENT, 10L)).isEmpty();
         }
     ```
  6. Directly after the test `resolveContact_providerProfile_neverUsesFallback`, add:
     ```java
         @Test
         void resolveContact_otherEntityType_throws() {
             assertThatThrownBy(() -> service.resolveContact(EntityType.USER, 1L))
                     .isInstanceOf(IllegalArgumentException.class);
         }
     ```
  7. Rename `resolveContact_providerProfile_neverUsesFallback` to
     `resolveContact_providerProfile_neverLooksUpAnOwner`.
- **Verification:**
  - `git grep -n "AD_OWN_CONTACT\|own.isPresent" marketplace-orchestrator` returns nothing.
  - `EntityType.USER` exists (`git grep -n "USER," platform-commons/src/main/java/org/ost/platform/core/model/EntityType.java`).
  - `bash scripts/build-and-test.sh --unit --sandbox`, run through the activity monitor, passes.
- **Sources:**
  - https://docs.gitlab.com/development/database/polymorphic_associations/ (reason the table stays generic is project-specific, see notes)
  - https://milanjovanovic.tech/blog/modular-monolith-data-isolation

### Step 3 -- text that still describes ad-owned contacts

**Done 2026-10-27** (Haiku agent, 14 exact replacements in 9 files, diff checked). One leftover found outside the spec: the `FeedbackDto` Javadoc says "PROVIDER_PROFILE or ADVERTISEMENT"; that is the feedback domain, where an advertisement may really own feedback, so it is left as is.

- **Depends on:** 2.
- **Files:** the ones listed in the replacements below.
- **Executor:** haiku.
- **Input:** each file as it is now.
- **Change:** exact replacements. Each old text occurs once in its file.
  1. `/app/platform-commons/src/main/java/org/ost/platform/contact/spi/ContactPort.java`.
     Old: ` * Owns contact_info (phone/telegram/viber attached to a PROVIDER_PROFILE or ADVERTISEMENT) and`
     New: ` * Owns contact_info (phone/telegram/viber attached to an owning entity; only PROVIDER_PROFILE owns contacts) and`
  2. `/app/platform-commons/src/main/java/org/ost/platform/contact/dto/ContactInfoDto.java`.
     Old: `/** The phone/telegram/viber contact data attached to one owning entity (PROVIDER_PROFILE or ADVERTISEMENT). */`
     New: `/** The phone/telegram/viber contact data attached to one owning entity (a PROVIDER_PROFILE). */`
  3. `/app/contact-spring-boot-starter/src/main/java/org/ost/contact/entity/ContactInfo.java`.
     Old: `/** What to show -- at most one row per owning entity (PROVIDER_PROFILE or ADVERTISEMENT), no FK, resolved only via ContactPort. */`
     New: `/** What to show -- at most one row per owning entity (a PROVIDER_PROFILE), no FK, resolved only via ContactPort. */`
  4. `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ContactRevealService.java`.
     Old: `    /** The channels that are set for the entity (an advertisement without its own contact falls back to its owner's profile), without their values. */`
     New: `    /** The channels that are set for the entity (an advertisement shows its owner's provider-profile contact), without their values. */`
  5. `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AdvertisementOwnerProfileLookupService.java`.
     Old: `/** Resolves an advertisement's owner's provider profile id -- {@link ContactAccessService}'s collaborator for the ADVERTISEMENT-to-PROVIDER_PROFILE contact fallback. */`
     New: `/** Resolves an advertisement's owner's provider profile id -- {@link ContactAccessService}'s collaborator for showing an advertisement its owner's contact. */`
  6. `/app/marketplace-rest-api/src/main/java/org/ost/restapi/api/ContactApiController.java`.
     Old: `description = "Public. Only which channels exist, never their values; an advertisement without its own contact uses its owner's provider-profile contact. Empty when none is set.")`
     New: `description = "Public. Only which channels exist, never their values; an advertisement shows its owner's provider-profile contact. Empty when none is set.")`
  7. `/app/integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ContactScenarioTest.java`.
     Old: `and advertisement fallback to the owner's profile contact. */`
     New: `and an advertisement showing its owner's profile contact. */`
  8. `/app/contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/01-contact-schema.xml`.
     Change only the `contact_info` table and its first two columns. `contact_view` stays as it is.
     - Table `remarks`. Old: `What to show -- at most one row per owning entity (PROVIDER_PROFILE or ADVERTISEMENT), holding phone/telegram/viber. No DB-level FK to provider_profile/advertisement, same entity_type+entity_id convention audit_log already uses -- resolved only via ContactPort, never a SQL join. An ADVERTISEMENT row is an optional per-listing override of the owner's PROVIDER_PROFILE row; fallback resolution lives in marketplace-orchestrator.`
       New: `What to show -- at most one row per owning entity, holding phone/telegram/viber. Only PROVIDER_PROFILE owns contacts; an ADVERTISEMENT shows its owner's PROVIDER_PROFILE row, resolved in marketplace-orchestrator. No DB-level FK, same entity_type+entity_id convention audit_log already uses -- resolved only via ContactPort, never a SQL join.`
     - Column `entity_type` (the first, in `contact_info`). Old remarks: `Owning entity type (EntityType: PROVIDER_PROFILE or ADVERTISEMENT). No FK -- resolved via ContactPort, not a join`
       New: `Owning entity type (EntityType; only PROVIDER_PROFILE owns contacts). No FK -- resolved via ContactPort, not a join`
     - Column `entity_id` (the first, in `contact_info`). Old remarks: `ID of the owning entity. References provider_profile(id) or advertisement(id) depending on entity_type, no FK`
       New: `ID of the owning entity, provider_profile(id) for PROVIDER_PROFILE, no FK`
  9. `/app/.claude/rules/marketplace-orchestrator.md`.
     Old: `ADVERTISEMENT-to-PROVIDER_PROFILE contact fallback, same "extract a services.* collaborator"`
     New: `ADVERTISEMENT-to-owner's-PROVIDER_PROFILE contact lookup, same "extract a services.* collaborator"`
     Old (two lines): `  plus \`resolveContact()\`'s fallback (an advertisement's own \`contact_info\` row if present, else` followed by `  its owner's provider-profile row, via \`AdvertisementOwnerProfileLookupService\`). Not split into`
     New (two lines): `  plus \`resolveContact()\` (a provider profile's own \`contact_info\` row; for an advertisement,` followed by `  its owner's provider-profile row, via \`AdvertisementOwnerProfileLookupService\`). Not split into`
     The backslashes above only escape the backticks in this plan; the file has plain backticks.
- **Verification:**
  - `git grep -n "without its own contact\|per-listing override\|PROVIDER_PROFILE or ADVERTISEMENT" -- ':!backlog' ':!docs/architecture' ':!*DECISIONS.md'`
    returns only:
    - the `contact_view` `entity_type` remarks line in `01-contact-schema.xml`;
    - the feedback changelog line.
  - The XML still parses: `xmllint --noout` on the changelog, if available; otherwise the
    integration build in the final verification.
- **Sources:** https://docs.gitlab.com/development/database/polymorphic_associations/ (kept generic, documented honestly)

### Step 4 -- annotate the two decision records

**Done 2026-10-27.** Approach settled with the user: one new contact ADR (ADR-002: only a provider profile owns `contact_info`), and the stale sentences of ADR-001 and ADR-010 corrected in place (not superseded, since both are otherwise still valid), both stamped `Verified: 2026-10-27`. `Applied` of ADR-001 and ADR-010 is raised by 1 when the task closes.

- **Depends on:** 3.
- **Files:**
  - `/app/contact-spring-boot-starter/DECISIONS.md` (ADR-001)
  - `/app/marketplace-orchestrator/DECISIONS.md` (ADR-010)
  - `/app/.claude/nav/adr-index.md` (regenerated)
- **Executor:** sonnet.
- **Input:** both ADRs; `.claude/commands/record-decision.md`; `.claude/rules.md` ("When a decision
  contradicts or supersedes an existing entry, update or annotate the existing entry").
- **Change:**
  - Through `/record-decision`, not a hand edit:
    - annotate contact ADR-001: an advertisement never owns a `contact_info` row; it shows its
      owner's provider-profile contact. The table stays generic over `(entity_type, entity_id)`;
      `contact_view` stays per entity shown.
    - annotate orchestrator ADR-010 the same way: `resolveContact` has no "ad's own row" branch and
      rejects other entity types.
  - Add `**Verified:** <today>` to both, because the claims were re-checked.
  - Run `bash .claude/nav/scripts/generate-adr-index.sh`.
  - Add `ADR-001 (contact-spring-boot-starter), ADR-010 (marketplace-orchestrator)` to the task's
    `adrs_referenced`.
- **Verification:**
  - `git diff --stat` shows both `DECISIONS.md` files and `adr-index.md`.
  - The index row text for both ADRs is unchanged or updated.
- **Sources:** none beyond the project rules.

### Step 5 -- phase 2 verification

**Done 2026-10-27**: one `run-all-tests --sandbox --playwright "e2e --full --ux"` (unit and integration plus the full e2e on a reset database): Maven build success, 851 tests, 0 failures; e2e 62 passed, 3 skipped, 0 failed.

- **Depends on:** 2, 3, 4.
- **Files:** none.
- **Executor:** main thread (script run).
- **Change:**
  - Run `bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox`
    in the background, with Monitor on the tree file.
  - Then `bash scripts/playwright.sh 04-provider-profile-flow --ux`. Its step "advertisement view --
    ContactRevealPanel click-through resolves the owner's provider profile contact via fallback" must
    still pass, because the behavior is unchanged.
- **Verification:**
  - Both runs pass.
  - Record counts in the task's Operational notes.


### Step 6 -- label the owner's monthly counters as profile-page views

**Done 2026-10-27** (Haiku agent, diff checked; the uk value is raw UTF-8 like its neighbours). Not yet run: covered by step 5.

- **Depends on:** 2.
- **Files:**
  - `/app/marketplace-app/src/main/resources/i18n/messages_en.properties`
  - `/app/marketplace-app/src/main/resources/i18n/messages_uk.properties`
  - `/app/playwright/e2e/04-provider-profile-flow.spec.js`
- **Executor:** haiku.
- **Change:**
  - Key `providerProfile.view.contactViews.label`: en `Contact views on the profile page (this month)`, uk `Перегляди контактів на сторінці профілю (цього місяця)` (unicode-escaped like the neighbouring lines).
  - In the existing step "contact views counters" of the first test, assert the caption `.provider-profile-contact-views-label` contains `profile page`.
- **Verification:** `04-provider-profile-flow --ux` passes; the screenshot of the account view shows the new caption.

### Phase 3 -- author link to the provider profile (deferred: not doing for now)

### Step 8 -- batched "profile id per actor" lookup in the provider-profile domain

- **Depends on:** 1.
- **Files:**
  - `/app/platform-commons/src/main/java/org/ost/platform/providerprofile/spi/ProviderProfilePort.java`
  - `/app/provider-profile-spring-boot-starter/src/main/java/org/ost/provider/spi/ProviderProfilePortImpl.java`
  - `/app/provider-profile-spring-boot-starter/src/main/java/org/ost/provider/services/ProviderProfileService.java`
  - `/app/provider-profile-spring-boot-starter/src/main/java/org/ost/provider/repository/ProviderProfileRepository.java`
  - `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ProviderProfileReadService.java`
  - `/app/integration-tests/src/test/java/org/ost/integrationtests/level1/providerprofile/ProviderProfileRepositoryTest.java`
- **Executor:** haiku.
- **Input:** the files as they are now. Each new method goes directly after that file's
  `findOwnerIds` method (in `ProviderProfileReadService`, after `findByActorId`).
- **Change:**
  - `ProviderProfilePort` (add `import java.util.Map;`):
    ```java
        /** Provider profile id per actor id, for the subset of {@code actorIds} that own a profile. */
        Map<Long, Long> findIdsByActorIds(@NonNull Set<Long> actorIds);
    ```
  - `ProviderProfilePortImpl` (add `import java.util.Map;`):
    ```java
        @Override
        public Map<Long, Long> findIdsByActorIds(@NonNull Set<Long> actorIds) {
            return service.findIdsByActorIds(actorIds);
        }
    ```
  - `ProviderProfileService` (add `import java.util.Map;`):
    ```java
        public Map<Long, Long> findIdsByActorIds(@NonNull Set<Long> actorIds) {
            return repository.findIdsByActorIds(actorIds);
        }
    ```
  - `ProviderProfileRepository` (add `import java.util.Map;` and `import java.util.stream.Collectors;`):
    ```java
        public Map<Long, Long> findIdsByActorIds(@NonNull Set<Long> actorIds) {
            return jdbcClient.sql("SELECT actor_id, id FROM provider_profile WHERE actor_id = ANY(:ids)")
                    .paramSource(new MapSqlParameterSource("ids", actorIds.toArray(new Long[0])))
                    .query((rs, _) -> Map.entry(rs.getLong("actor_id"), rs.getLong("id")))
                    .list().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    ```
  - `ProviderProfileReadService` (add `import java.util.Map;` and `import java.util.Set;`):
    ```java
        public Map<Long, Long> findIdsByActorIds(@NonNull Set<Long> actorIds) {
            return providerProfilePortFactory.findIfAvailable().map(p -> p.findIdsByActorIds(actorIds)).orElse(Map.of());
        }
    ```
  - `ProviderProfileRepositoryTest`, after `findOwnerIds_returnsActorsWithProfile` (add
    `import java.util.Map;` if missing):
    ```java
        @Test
        void findIdsByActorIds_returnsProfileIdPerOwningActor() {
            ProviderProfile saved = save(actorId, ProviderKind.MASTER);
            Long otherActor = newActor();

            Map<Long, Long> ids = providerProfileRepository.findIdsByActorIds(Set.of(actorId, otherActor));

            assertThat(ids).containsExactly(Map.entry(actorId, saved.getId()));
        }
    ```
- **Verification:**
  - `git grep -n "findIdsByActorIds"` lists the six files.
  - The integration build in step 13 passes.
  - If another class implements `ProviderProfilePort` (`git grep -n "implements ProviderProfilePort"`),
    stop and report; do not invent an implementation.
- **Sources:** https://www.graphql-js.org/docs/n1-dataloader/

### Step 9 -- `ownerProviderProfileId` on advertisements, filled per page

- **Depends on:** 8.
- **Files:**
  - `/app/platform-commons/src/main/java/org/ost/platform/advertisement/dto/AdvertisementInfoDto.java`
  - `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AdvertisementDisplayEnrichmentService.java`
  - a new unit test `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/AdvertisementDisplayEnrichmentServiceTest.java`,
    shaped like `ProviderProfileDisplayEnrichmentServiceTest`
- **Executor:** sonnet.
- **Input:** the files above; `.claude/rules/marketplace-orchestrator.md` (at most two domain `*Port`s
  per class via `ComponentFactory`; `ProviderProfileReadService` is a service collaborator, not a
  port).
- **Behavior and constraints:**
  - Add `Long ownerProviderProfileId;` to `AdvertisementInfoDto`, after `createdByUserEmail`. It is
    display-only, null when the author has no profile or the provider-profile starter is absent.
  - `AdvertisementDisplayEnrichmentService` gets `ProviderProfileReadService` as a constructor
    collaborator.
    - In the "Actor" block, `enrichWithActorInfo` makes one extra call,
      `findIdsByActorIds(<the same author id set>)`, and sets `ownerProviderProfileId` from the map.
    - `enrichWithActor` does the same for one ad, with a one-element set.
    - Extend `applyActorData` rather than adding a separate public method, so every existing caller
      (`AdvertisementsView`, the REST list and get, `enrichSingle`) gets the field with no call-site
      change.
    - Update the class Javadoc to mention the author's provider profile id.
  - Do not add the field to `AdvertisementSnapshotDto` or any audit diff.
  - Unit tests:
    - a page with two authors, one with a profile, gets the id on that author's ads and null on the
      other's;
    - the profile id lookup is called once per page.
- **Verification:**
  - Unit build passes.
  - `git grep -n "ownerProviderProfileId"` shows the DTO, the service and the test.
- **Sources:**
  - https://www.graphql-js.org/docs/n1-dataloader/
  - https://www.dummies.com/article/technology/notable-websites/ebay/for-seniors-how-to-examine-the-seller-and-item-info-on-ebay-189630

### Step 10 -- `EntityMetaPanel` renders the author as a link when given a click action

- **Depends on:** 1.
- **Files:**
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/EntityMetaPanel.java`
  - `/app/marketplace-app/src/main/frontend/themes/my-app/styles.css`
- **Executor:** sonnet.
- **Input:**
  - the files;
  - `ModerationView.buildProfileLink` (the project's link precedent);
  - `AdvertisementCardView` lines that use `STOP_PROPAGATION` and the card's own `keydown` handler.
- **Behavior and constraints:**
  - `Parameters` gains an optional `Runnable onAuthorClick`. Null keeps today's plain author span.
    Add it to the author-carrying factory methods as a new last argument, or as a builder field;
    keep the provider-profile factories (no author) unchanged.
  - With `onAuthorClick` set, the author span also gets:
    - class `entity-meta-author--link`;
    - `role="link"` and `tabindex="0"`;
    - a `click` DOM listener that runs the action, with `event.stopPropagation()` event data;
    - a `keydown` listener filtered to `event.key === 'Enter'`, also with `event.stopPropagation()`,
      so the surrounding clickable card does not open too.
  - The `title` attribute with the email stays as today; its exposure is a separate decision (D6).
  - CSS: a `.entity-meta-author--link` rule next to `.entity-meta-author` in `styles.css`:
    `color: var(--app-accent-primary); text-decoration: underline; cursor: pointer;`, plus a
    `:focus-visible` outline matching the theme's existing focus style (search `styles.css` for
    `focus-visible`).
  - One-line comments only; no task numbers.
- **Verification:**
  - Unit build passes.
  - Existing Playwright selectors on `.entity-meta-author` still match, because the class is kept.
- **Sources:**
  - https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/a
  - https://vaadin.com/docs/latest/building-apps/views/navigate/flow (D4 alternative)

### Step 11 -- wire the author link on ad cards and in the ad view to the provider profile overlay

- **Depends on:** 9, 10.
- **Files:**
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/MainView.java`
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/AdvertisementsView.java`
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/AdvertisementCardView.java`
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/AdvertisementOverlay.java`
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementViewOverlayModeHandler.java`
- **Executor:** sonnet.
- **Input:**
  - `MainView`'s existing `moderationView.setOnOpenProviderProfile(...)` block;
  - `ProvidersView.openProfileOverlay(Long, Runnable)`;
  - `ProviderProfileCatalogOverlay.closeToList()`, which pushes `LIST_PATH`;
  - `AdvertisementOverlay.openForView`, which pushes `AD_PATH_PREFIX + id`;
  - `BrowserHistoryUtil`.
- **Behavior and constraints:**
  - Mirror the moderation wiring; do not invent a new navigation mechanism.
    - `AdvertisementsView` and `AdvertisementOverlay` each get a
      `setOnOpenProviderProfile(Consumer<Long>)` setter.
    - `MainView` sets both with the same lambda shape as moderation: remember the selected tab,
      switch to the Providers tab, call `providersView.openProfileOverlay(id, onClose)`.
    - Do this only when the Providers tab exists (`providersTab != null`). Without it, no callback
      is set and authors stay plain text.
  - `AdvertisementCardView` and `AdvertisementViewOverlayModeHandler` receive the callback through
    their `Parameters` (nullable `Consumer<Long> onOpenProviderProfile`). They pass
    `() -> callback.accept(ad.getOwnerProviderProfileId())` as `onAuthorClick` only when both the
    callback and `ad.getOwnerProviderProfileId()` are non-null.
  - `onClose`, when the link was used inside an open ad view:
    - re-select the previous tab;
    - restore the browser URL to that ad's `/ads/<id>`, because the profile overlay's close pushed
      the list path while the ad overlay is still open.
    - From a card (no ad overlay open) the list path is correct as is.
    - The executor chooses the smallest way to do this with `BrowserHistoryUtil` and states it in
      the step report.
  - An administrator gets Edit in the profile overlay through the existing
    `ProviderProfileCatalogViewModeHandler` rule (`canEditUserAccount`). Change nothing there.
  - No `ProviderProfileReadService` call per card; the id comes from step 9's enrichment.
- **Verification:**
  - Unit build passes.
  - A manual or Playwright check (step 12):
    - card link opens the profile, and closing returns to the ads list;
    - ad view link opens the profile, closing returns to the ad view with URL `/ads/<id>`;
    - clicking the link does not open the ad.
- **Sources:**
  - https://www.dummies.com/article/technology/notable-websites/ebay/for-seniors-how-to-examine-the-seller-and-item-info-on-ebay-189630
  - https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/a

### Step 12 -- Playwright: author link by role

- **Depends on:** 11.
- **Files:** `/app/playwright/e2e/05-marketplace-advertisement-flow.spec.js`. Update the header
  comment's test list.
- **Executor:** sonnet.
- **Input:**
  - `playwright/e2e/_fixtures.js` (`page` is anonymous; `pageAs`, `createUser`, `adminAccount`,
    `adminKey`, `request`, `unique`);
  - the existing `04-provider-profile-flow` steps that create a profile with contacts;
  - `.claude/rules/playwright.md`.
- **Behavior:** one new `test(...)` with `test.step`s, each role in its own session, with a
  screenshot per step:
  1. Setup through the REST API / fixtures:
     - user A with a provider profile with a phone and one ad;
     - user B without a profile, with one ad.
  2. Visitor (anonymous `page`):
     - A's card shows `.entity-meta-author--link`, and clicking it opens the provider overlay
       showing A's profile and the Contacts card;
     - no `.overlay__view-edit` is visible;
     - closing returns to the Advertisements tab.
  3. Visitor: B's card shows `.entity-meta-author` without `--link`, and clicking the name opens
     B's ad, not a profile.
  4. Visitor, in A's ad view:
     - the author link opens A's profile;
     - closing returns to the ad view and the URL matches `/ads/<id>`;
     - the ad's Contacts card is still there.
  5. Keyboard: focus A's card author link and press Enter; the profile opens and the ad does not.
  6. Administrator (`adminAccount`): the same link from A's ad view shows a visible
     `.overlay__view-edit` in the profile overlay.
  7. Another signed-in user (`createUser`): the link opens the profile without Edit.
- **Verification:** `bash scripts/playwright.sh 05-marketplace-advertisement-flow --ux` passes;
  screenshots attached.
- **Sources:** https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/a

### Step 13 -- documentation, decision record and whole verification

- **Depends on:** 2-12 (whichever ran).
- **Files:**
  - Javadoc already updated in steps 8-10;
  - possibly `/app/marketplace-app/DECISIONS.md` through `/record-decision`;
  - `/app/.claude/nav/adr-index.md`;
  - the task file.
- **Executor:** sonnet for the record-decision gate; the main thread for the runs.
- **Change:**
  - Run `/record-decision marketplace-app — Ad author links to the provider profile through the
    moderation tab-switch path, profile id batched in ad enrichment`. Let its worthiness gate decide
    whether an ADR is written. If written, regenerate the ADR index.
  - Run `/check-architecture-map` and, if it reports drift (new port method, new DTO field),
    `/sync-docs`, as root `CLAUDE.md` says.
  - Fill the task's Operational notes as the work happens.
- **Verification of the whole:**
  1. `bash scripts/activity-monitor.sh -- bash scripts/run-all-tests.sh --sandbox --playwright "e2e --full --ux"`
     (Definition of Done): unit, integration and full e2e green; counts recorded.
  2. `git grep -n "own.isPresent\|without its own contact\|per-listing override"` returns nothing
     outside `backlog/` and the generated architecture files.
  3. `git grep -n "findIdsByActorIds\|ownerProviderProfileId\|entity-meta-author--link"` shows the
     port chain, enrichment, DTO, panel, CSS and spec.
  4. Visual check of the Playwright screenshots:
    - the author link uses the accent color on the card and in the view;
    - the Contacts card is unchanged on the ad.
  5. The task file lists phases 2 and 3 as done with dates. Closing the task (move,
     `BACKLOG.md` row, archive line, `Applied:` counts) waits for the user's explicit approval.


## Related

- improvement-200 (item 6) and improvement-203 (item 2): the same card treatment done for the view overlays
- improvement-213 phase 5: REST contacts

## Operational notes
- token_cost_review: 86709
- token_cost_research: 232096
- token_cost_verification: n/a
- review_signal_ratio: 2/5
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: UI-visible change needs visual/behavioral verification
- flows_chosen: scripts/run-all-tests.sh with e2e --full --ux
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 5
- haiku_decomposition_rework_needed: 0/5
- adrs_referenced: ADR-002 (contact-spring-boot-starter), ADR-001 (contact-spring-boot-starter), ADR-010 (marketplace-orchestrator)

### Agent calls
- apply the four specified code and i18n edits | subagent_type=general-purpose (model haiku) | tokens=109196 | tool_uses=16 | duration_s=83 | mode=background | batch=solo
- account view Contacts header | subagent_type=general-purpose (model haiku) | tokens=79306 | tool_uses=4 | duration_s=27 | mode=background | batch=solo
- phase 2 step 2, resolveContact cleanup | subagent_type=general-purpose (model haiku) | tokens=99073 | tool_uses=13 | duration_s=70 | mode=background | batch=solo
- phase 2 step 3, text replacements | subagent_type=general-purpose (model haiku) | tokens=81666 | tool_uses=6 | duration_s=55 | mode=background | batch=solo
- phase 2 step 6, counters label | subagent_type=general-purpose (model haiku) | tokens=81207 | tool_uses=9 | duration_s=39 | mode=background | batch=solo
- in-depth research on contacts and author link | subagent_type=deep-researcher | tokens=232096 | tool_uses=86 | duration_s=756 | mode=background | batch=solo
- code review of the working diff | subagent_type=code-review skill (forked) | tokens=86709 | tool_uses=4 | duration_s=52 | mode=background | batch=solo

### Script/command runs
- scripts/deploy-and-run.sh --reset-only-db | duration_s=176 | mode=background | result=pass
- scripts/playwright.sh 04-provider-profile-flow --ux | duration_s=199 | mode=background | result=pass
- scripts/run-all-tests.sh --sandbox --playwright "e2e --full --ux" | duration_s=1093 | mode=background | result=fail
- scripts/run-all-tests.sh --sandbox --playwright "e2e --full --ux" | duration_s=1083 | mode=background | result=pass
- scripts/deploy-and-run.sh --reset-only-db | duration_s=180 | mode=background | result=pass
- scripts/playwright.sh 04-provider-profile-flow --ux (role steps) | duration_s=225 | mode=background | result=fail (a missing overlay close in the new step), then rerun of that test | duration_s=54 | mode=background | result=pass
- scripts/run-all-tests.sh --sandbox --playwright "e2e --full --ux" | duration_s=1086 | mode=background | result=pass
- scripts/build-and-test.sh --unit --sandbox | duration_s=468 | mode=background | result=pass
- scripts/run-all-tests.sh --sandbox --playwright "e2e --full --ux" | duration_s=1136 | mode=background | result=pass

### Review angle yield
- correctness and simplification (single /code-review run, default effort) | survived=2 | total_candidates=5 | tokens=86709
