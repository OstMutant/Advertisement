# improvement-235: Featured placement — a flexible mechanism that lifts advertisements and profiles out of the crowd

**Type:** feature — one mechanism that marks provider profiles (advertisements later) as featured and presents them to searchers first; the reason for featuring is pluggable (today a rating rule, later a purchase); research done, design decided, building not started
**Module:** `platform-commons` (a port and the DTOs), a new starter or an existing one that owns the featured state, `marketplace-orchestrator` (composition with ratings and display), `advertisement-spring-boot-starter` and `provider-profile-spring-boot-starter` (what can be featured), `feedback-spring-boot-starter` (`feedback_aggregate` as the first criterion), `marketplace-app` (the featured display in the catalog and the Providers view), `marketplace-rest-api` and `marketplace-mcp` (a featured list in step with the UI)
**Priority:** 🔵 top at the user's request — a larger feature that needs a design decision; the long-term plan is to earn from it, so the structure matters more than the first rule
**When:** the design is settled; building can start: [improvement-236](../completed/tasks/improvement-236-list-order-and-edit-bump-rest-mcp-ui-parity.md) settled the default list order and [improvement-238](../completed/tasks/improvement-238-block-self-review-of-provider-profile.md) added the self-review guard the rating rule relies on

## Current state

Verified against the code on 2026-11-05:

- There is no notion of a featured advertisement or profile anywhere. The catalog is one list filtered by `AdvertisementFilterDto`, sorted by the caller's sort, paged by page number; the Providers catalog is the same shape; the REST list returns `X-Total-Count` and an RFC 8288 `Link` header and the MCP tools wrap it.
- Ratings exist: `feedback_aggregate` keeps `avgRating` and `reviewCount` for each owning entity (type and id, `FeedbackAggregateDto`), recomputed on every feedback write by the feedback starter, and `FeedbackAccessService` in the orchestrator reads it. So a rule like "five stars from at least five reviews" can be computed today.
- The project has a pattern for a feature that belongs to an entity without touching the entity's table: a separate starter tied by entity type and id (the taxon assignment and the feedback aggregate work that way), composed in `marketplace-orchestrator`.

## Why change

The owner wants to lift the best entries out of the mass and show them to searchers first, and to be able to charge for it later. The way to make that last is one mechanism with the reason for featuring kept separate from the featuring itself, so that a new reason (a rating today, a purchase or a moderator's choice later) does not mean rebuilding the display, the lists or the APIs. Done as a one-off sort tweak, every new reason would touch every list and every door (UI, REST, MCP) again.

## Expected benefit

Searchers see the best or the sponsored entries first without the list getting harder to read or less trustworthy; the platform gets a place to hang later reasons for featuring; a new reason for featuring is a small addition, not a redesign.

## Research result (plan for people)

Purpose: the result for the person who decides: clear, short and easy to follow.

### Goal

One mechanism that lifts some provider profiles and advertisements out of the general mass and shows
them to searchers first, for a reason that can change (today a rating rule, later a purchase or a
moderator's pick), without rebuilding the display, the lists or the APIs each time a reason is added,
and shown so that it helps searchers instead of annoying them.

### The problem this solves

Today every catalog is one list in the caller's own order. There is nowhere to put "the best" or, later,
"the paid" entries. Done as a sort tweak, every new reason would touch every list and every door (UI,
REST, MCP) again, and a paid entry would push an earned one down without telling the reader.

### Conflicts to decide first (raised before the plan, not buried in it)

1. **The example rule "5 stars from 5 reviews" works against the goal.** An average of exactly 5.0
   is the band readers trust least (purchase likelihood peaks at 4.0-4.7 and falls toward 5.0, Spiegel
   Research Center), and one 4-star review would drop the master out. Programs that award an earned
   badge use about 4.8 plus a minimum number of reviews (Airbnb Superhost 4.8 and 10 stays; Thumbtack
   Top Pro 4.8 and 5 reviews; Etsy Star Seller 4.8). See decision 1.
2. **A profile owner can review their own profile today.** No code path checks it (the review form,
   the REST call and the orchestrator only check that the profile exists). With a 5-review threshold
   the owner's own review is a fifth of the qualification. See decision 2.
3. **Best practice removes a featured entry from the normal results; the brief says the lists stay
   untouched.** Algolia's guide and eBay show each entry once. Removing it would change page contents
   and the meaning of the total count. See decision 3.
4. **A recorded decision assumed ratings never show on cards.** The decision that kept the feedback
   starter self-contained (see `.claude/nav/adr-index.md`, the feedback/`EntityRef` entry) was written
   when ratings appeared only on a profile's own page. The featured strip shows the rating on a card for
   the first time. That decision already allows it (read through the feedback port); the design record
   should add a note to it, not contradict it.

### The answer to the six questions

#### 1. The shape of the mechanism

A **featured placement** is one row: *which entry* (entity type and id), *why* (a reason: `TOP_RATED`
now; `SPONSORED` or `EDITORIAL` later), *from when* and *until when* (empty "until" = until its writer
removes it). One table covers advertisements and profiles, the same way reviews and contacts already
work: its own small starter, keyed by entity type and id, no column on the profile or advertisement
tables.

A reason is not code inside the display. It is **a writer** that owns its own rows:

| Mechanism | How it works | SQL cost | Consistency | A purchase later | Verdict |
|---|---|---|---|---|---|
| m1 rule on read | Every catalog load asks the feedback starter "who qualifies now" | One extra cross-module query per page load | Badge flickers with every review | Needs a stored grant anyway, so a second mechanism appears | Rejected |
| m2 table rebuilt by a job | A job recomputes the whole featured set | Cheap reads, one job | Stable between runs | A rebuild would wipe purchased rows unless split by reason, which is m3 | Rejected as a special case of m3 |
| **m3 table of grants, one writer per reason** | The rating rule is a nightly writer that keeps its own `TOP_RATED` rows in step; a purchase later inserts `SPONSORED` rows with an end date; a moderator `EDITORIAL` rows | Cheap reads (one small table), one small job | Stable between runs, like Superhost and Top Pro | Adds rows, nothing else changes | **Proposed** |

#### 2. Which entries qualify

- **Proposed first rule:** a provider profile is "Top rated" when its average is at least **4.8** from
  at least **5** visible reviews. Hidden and confirmed-violation reviews are already left out of the
  average, so no extra filter is needed. The thresholds are settings, not code.
- **Checked once a night**, like the existing clean-up jobs, so status changes at most once a day and a
  single review does not make a card appear and vanish. When the rating drops, the entry leaves at the
  next run. When moderation hides a fake review, its effect is gone by the next morning.
- **Ties and too many entries:** the strip shows at most 4, picked at random among the qualifying
  entries that match the searcher's filter, a new pick on each load, so every qualifying entry gets an
  equal share over time (OLX rotates its TOP set the same way).
- **Small market:** with a few hundred entries per city, fewer than 4 may qualify; the strip then shows
  what exists, and disappears when nothing matches.
- **Against gaming:** one review per user and profile and 10 reviews a day per user already exist; the
  missing piece is the self-review guard (decision 2). Later: count only reviews older than the 48-hour
  edit window, and a minimum account age for reviewers.
- **Advertisements** have no reviews of their own (reviews attach only to provider profiles). An
  advertisement can be featured by inheriting its owner's profile status, with an honest label
  ("From a top-rated master"), or only for a purchase or a moderator's pick. See decision 4.
- **Realistic next reasons:** moderator pick (`EDITORIAL`), verified provider, new provider boost
  (time-limited), purchase (`SPONSORED`). A purchase adds only an end date and the word "Реклама".

#### 3. How to show it without annoying searchers

| Display | Paging and total count | Caller's filter and sort | REST and MCP | Trust and UX | Size | Risk |
|---|---|---|---|---|---|---|
| (a) badge on the normal card only | untouched | untouched | one more field | honest for earned reasons, misleading for paid | smallest | gives no exposure |
| (b) rank boost in the sort | page 1 fills with featured | breaks "newest first" and the caller's choice | changes the list | displaced normal results cost readers (Joo et al.) | needs a column or a join across modules | high |
| **(c) separate strip above the list** | **untouched** | **matches the filter, ignores the sort** | **separate endpoint** | **Algolia's documented pattern; labelled apart** | **small** | **low** |
| (d) featured slots inside the list | page sizes and totals stop matching | partly | per-item flag in the list | repeats the same few entries in a small market | medium | medium |
| (e) block inside the first page | page 1 shifts, totals shift | partly | changes the list | page 1 can become all featured | medium | medium |
| (f) a "Top rated" page or filter chip | untouched | untouched | a filter value | no exposure unless clicked | small | low, good as a later "see all" |

**Proposed: (c).** A strip of at most 4 cards above the list, only on the first page, no automatic
rotation (moving content is ignored and looks like an ad, NN/g; it would also need a pause control,
WCAG 2.2.2), the same card the list uses with a small label, and a "Why these?" link that states the
rule in one sentence (the EU ranking-transparency rule asks for the main parameters in plain words; the
badge study found unexplained badges distrusted). The entry also stays in its normal place in the list
(decision 3).

Desktop, Providers tab, filter "Plumbing, Kyiv":

```
 Providers                                         [Filters v]  [Sort: newest v]
 +-- Top rated . Plumbing, Kyiv -------------------------------- (i) Why these? --+
 | +----------------+ +----------------+ +----------------+ +----------------+  |
 | | * Top rated    | | * Top rated    | | * Top rated    | | * Top rated    |  |
 | | Ivan P. MASTER | | Dmytro K.      | | Aqua Shop SHOP | | Oleh S. MASTER |  |
 | | 4.9 * (12)     | | 4.8 * (7)      | | 5.0 * (5)      | | 4.8 * (21)     |  |
 | | Plumbing, Kyiv | | Plumbing, Kyiv | | Plumbing, Kyiv | | Plumbing, Kyiv |  |
 | +----------------+ +----------------+ +----------------+ +----------------+  |
 +-------------------------------------------------------------------------------+
 All providers . 128
 [card] [card] [card] [card]
 [card] [card] [card] [card]
                              < 1  2  3 ... 11 >
```

"Why these?" opens one line: *"Providers with an average of at least 4.8 from at least 5 reviews.
Updated daily. Shown in random order."*

Mobile: the same strip as one row that scrolls sideways with the next card half visible (NN/g), no dots.

```
 +-- Top rated ---------------- (i) --+
 | +--------------+ +-------         |
 | | * Top rated  | | * Top           |
 | | Ivan P.      | | Dmytr           |
 | | 4.9 * (12)   | | 4.8 *           |
 | +--------------+ +-------         |
 +------------------------------------+
```

States: nothing matches the filter, so no strip at all (no empty box); one match, so one card with the
same label; page 2 and later, so no strip. The heading is a real heading and each card a link, so a
screen reader announces "Top rated, list of 3".

Alternatives, for comparison:

```
 (a) badge only                    (d) interleaved                  (b) rank boost
 [card][card *Top][card]           [card][card][card]               [*card][*card][*card]
 [card][card][card]                [* Top rated card]               [*card][card][card]
                                   [card][card][card]               ("newest first" is no longer true)
```

Later, a paid reason gets **its own** strip labelled "Реклама" / "Advertising", never mixed into the
"Top rated" one, so the label always tells the true reason.

#### 4. The other doors

- **REST:** a separate `GET /api/provider-profiles/featured` with the same filter parameters as the
  list, returning a plain array of at most 4 items, each with its reason and rating. The list endpoint,
  its total count and its paging links do not change. Public, like the list.
- **MCP:** one more tool, `listFeaturedProviderProfiles`, wrapping that endpoint, so an agent sees the
  same strip a person sees.
- **Sitemap, link previews, search engines:** no effect. The sitemap reads the plain list, a link
  preview reads one entry, and the strip shows the same content to everyone (not cloaking).

#### 5. Fit with the architecture

- A new `featured-spring-boot-starter` owns one table, `featured_placement`, with a unique key on
  (entity type, entity id, reason). It knows nothing about profiles, advertisements or reviews.
- `platform-commons` gets the port (`FeaturedPlacementPort`: "list active placements for a type",
  "replace the rows of one reason"), one DTO and the reason enum.
- The feedback port gets one query ("which entities have at least this average from at least this many
  reviews"); the provider profile port gets one ("which of these ids match this filter").
- The orchestrator holds two small services (each within the two-port limit): the nightly rating rule,
  and the read that joins featured rows, the filter, the rating and the display data.
- No caching in the first version (two small queries per first-page load). Rows written by the rating
  rule are not audited, because they can be recomputed from the reviews, which are audited; a moderator
  or purchase writer will audit its rows.
- **Where a purchase plugs in:** the payment module calls the same port with reason `SPONSORED` and an
  end date. Added then: one enum value, one label in the two language files, one method to write a
  single grant, and the separate "Реклама" strip. Unchanged: the table, the read service, the REST
  endpoint shape, the MCP tool and the catalog lists.

#### 6. Smallest first version and sequence

First version: the rating rule on **provider profiles** and the strip in the **Providers** tab, with
REST, MCP, tests and documentation. Left for later: advertisements (decision 4), moderator picks,
purchases, a "see all top rated" filter, counting only reviews older than 48 hours.

### The means needed

- One new starter (table, repository, service, port implementation), one port, one DTO and one enum in
  `platform-commons`, one query each on the feedback and provider-profile ports.
- Two orchestrator services and one nightly job with its settings.
- One Vaadin strip component, styles and the language keys; one REST endpoint; one MCP tool; the
  Postman collections.
- Integration tests (repository, orchestrator, REST), one Playwright scenario, the design record and the
  documentation updates.

### What the result will look like

A searcher opening Providers sees up to 4 "Top rated" cards that match the filter, above the usual
list, with a one-line explanation of why. Nothing else in the list changes. An agent calling REST or
MCP gets the same four. When a purchase comes, it fills a separately labelled strip through the same
table.

### Cost

Effort: about 4 to 6 working days of a developer, done here as 14 steps.

| Executor | Steps | Estimated tokens |
|---|---|---|
| haiku | 2, 6, 13b | about 110 thousand |
| sonnet | 1, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13a, 14 | about 2.45 million (range 2.0 to 2.8) |
| opus | none (the design is decided by your answers below) | 0 |

Total about 2.1 to 2.9 million tokens, most of it in the starter, the UI strip and the tests. The
estimate is mine and not measured.

### Risks

- **Too few qualifying providers** at launch: the strip is often absent. Low harm (no empty box);
  lowering the threshold is a setting.
- **Fake reviews** inflate a profile into the strip until moderation acts. Self-review guard
  (decision 2) and nightly runs limit it.
- **Duplicates on page 1** (decision 3): the same provider may appear in the strip and in the list.
- **Reading the strip as advertising:** a label "Top rated" plus the "Why these?" line reduce it; the
  Danish authority's test showed a clear label nearly triples awareness of why a result is placed.
- **Order with improvement-236:** the strip has its own order (random) and does not depend on the list
  order, so building it need not wait; the task file says it waits. See decision 8.

### Where the project's practice differs from best practice

- **Deduplication** (conflict 3): best practice shows each entry once; the project's rule that lists stay
  untouched keeps duplicates. Cost of following best practice: a filter on page 1 and a total that no
  longer equals what the reader counts.
- **Earned status checked on a schedule** (Airbnb, Thumbtack, Etsy): the project recomputes the rating
  live on every write. The proposal keeps the live rating and adds a nightly check only for featuring;
  no change to the project's practice.
- **Ranking explanation:** best practice (EU ranking rules, badge studies) explains why an entry is
  placed; the project has no such text anywhere yet. The proposal adds one line to the strip.
- **Earlier sketch in the owner's notes** put a promotion column on the advertisement table with a sort
  priority; the project's practice (own starter by entity type and id) and the brief's decided generic
  mechanism replace it. Recorded only for traceability.


## Decisions taken (user, 2026-11-05)

1. **Threshold:** a provider profile is "Top rated" at an average of at least 4.8 from at least 5 visible reviews; both are settings.
2. **Self-review:** a profile owner is blocked from reviewing their own profile when the review is created; done as its own task, [improvement-238](../completed/tasks/improvement-238-block-self-review-of-provider-profile.md), not as a step of this one.
3. **Duplicates:** a featured profile also stays in its normal place in the list, so the list, its total count and its paging do not change.
4. **Strip size:** at most 4 cards, picked at random among the qualifying profiles that match the caller's filter, first page only, no automatic rotation. A later idea: a "show all top rated" filter chip.
5. **Label:** "Top rated" / "Високий рейтинг" with a "Why these?" / "Чому ці?" line stating the rule; a paid reason later gets its own strip labelled "Реклама" / "Advertising".
6. **Frequency:** the rating rule runs nightly (and once at start-up).
7. **Advertisements:** not decided now; the mechanism is generic over entity type, so an advertisement reason (for example inherited from the owner's profile) can be added later without changing the table.
8. **Order of work:** build after improvement-236 (now done), as the user chose; the research found the strip does not depend on the list order, so this is a sequencing choice, not a technical need.

The execution plan below is the research's plan for the recommended first version. Its step 9 (self-review guard) is replaced by improvement-238; everything else applies as written, with the decisions above as its precondition.

## Execution plan

Purpose: the result for the AI that executes it, with the detail each step's executor model needs
(exact content for a weaker model, exact files and behavior for a stronger one).

Scope: the recommended first version only: mechanism m3 (a table of placements, one writer per
reason), the `TOP_RATED` rating rule on provider profiles, the featured strip in the Providers tab,
REST and MCP parity, tests and documentation. Advertisements, moderator picks and purchases are out of
scope.

**Precondition.** The person has answered the decisions in `plan-human.md`. The values below are the
proposed answers (threshold 4.8 and 5, nightly, strip of 4 on the first page only, duplicates kept,
self-review guard included). If an answer differs, change only the named setting or skip step 9; no
other step changes.

Every step obeys the project rules: `.claude/rules.md`, root `CLAUDE.md` and the `.claude/rules/<module>.md`
of the module touched (read an existing file of that module first so its rule file loads); pure SQL via
`JdbcClient`; `lombok.NonNull` on public parameters; Lombok first; comments one line or none, never a
ticket or ADR number; all user-visible text through `I18nKey`; every Liquibase column and table with
`remarks=`. Before each file edit the main thread presents the plan and gets approval (Approval Rule).

### Steps

#### 1. Design record

- **Depends on:** the person's answers.
- **Files:** `platform-commons/DECISIONS.md` (new ADR, via `/record-decision`), annotation on the
  existing feedback/`EntityRef` ADR in the same file, `.claude/nav/adr-index.md` (regenerated).
- **Executor:** sonnet.
- **Input:** `plan-human.md` sections 1-6 and the answers; the existing ADR found in
  `.claude/nav/adr-index.md` as "feedback-spring-boot-starter reuses EntityRef and stays
  self-contained" (extract it with `node .claude/nav/scripts/md-to-decisions-json.js --extract platform-commons <id>`).
- **Change:** run `/record-decision platform-commons — Featured placement is its own starter keyed by
  entity type, id and reason; each reason has one writer` with: context (the goal, the three mechanisms
  m1/m2/m3 and why m3), decision (table `featured_placement`, `FeaturedPlacementPort`, the rating rule
  as a nightly writer in the orchestrator, the strip outside the paged list, separate REST endpoint and
  MCP tool, duplicates kept), rejected alternatives (rule on read; job-rebuilt table; rank boost;
  interleaved slots; column on the domain table), consequences (a purchase adds a reason value, a
  label, a single-grant port method and its own labelled strip). `**Also affects:**` the new starter,
  `marketplace-orchestrator`, `marketplace-app`, `marketplace-rest-api`, `marketplace-mcp`,
  `feedback-spring-boot-starter`, `provider-profile-spring-boot-starter`. Annotate the feedback ADR:
  "the featured strip is the first card-level reader of the aggregate, through `FeedbackPort` as this
  decision allows". Regenerate the ADR index (`bash .claude/nav/scripts/generate-adr-index.sh`).
- **Verification:** `/record-decision`'s own checks pass; the index has the new row; no ADR number or
  ticket number appears in any other file.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://www.airbnb.com/help/article/829 , https://business.ucr.edu/news/2023/11/28/online-consumers-shy-away
- **Estimate:** about 80 thousand tokens.

#### 2. Port, DTO and reason enum in platform-commons

- **Depends on:** 1.
- **Files (new):**
  `platform-commons/src/main/java/org/ost/platform/featured/model/FeaturedReason.java`,
  `platform-commons/src/main/java/org/ost/platform/featured/dto/FeaturedPlacementDto.java`,
  `platform-commons/src/main/java/org/ost/platform/featured/spi/FeaturedPlacementPort.java`.
- **Executor:** haiku.
- **Input:** none (new files).
- **Exact content:**

`FeaturedReason.java`
```java
package org.ost.platform.featured.model;

/** Why an entry is lifted out of the catalog; each value has exactly one writer that owns its rows. */
public enum FeaturedReason {
    TOP_RATED
}
```

`FeaturedPlacementDto.java`
```java
package org.ost.platform.featured.dto;

import java.time.Instant;
import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.featured.model.FeaturedReason;

/** One featured placement: which entry, why, and until when ({@code validUntil} null means until its writer removes it). */
public record FeaturedPlacementDto(
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull FeaturedReason reason,
        @NonNull Instant validFrom,
        Instant validUntil
) {
}
```

`FeaturedPlacementPort.java`
```java
package org.ost.platform.featured.spi;

import java.util.List;
import java.util.Set;
import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.featured.dto.FeaturedPlacementDto;
import org.ost.platform.featured.model.FeaturedReason;

/** Featured placements of any owning entity, generic over entity type and id; implementation lives in featured-spring-boot-starter. */
public interface FeaturedPlacementPort {

    /** Placements of this entity type that are active now, for every reason. */
    List<FeaturedPlacementDto> findActive(@NonNull EntityType entityType);

    /** Makes the rows of this reason for this entity type exactly {@code entityIds}; an empty set removes them all. */
    void replaceForReason(@NonNull EntityType entityType, @NonNull FeaturedReason reason, @NonNull Set<Long> entityIds);
}
```
- **Verification:** `bash scripts/build-and-test.sh --no-unit --no-integration` compiles (run through
  `scripts/activity-monitor.sh` per `.claude/rules.md` "Scripts").
- **Sources:** project precedent `platform-commons/src/main/java/org/ost/platform/feedback/spi/FeedbackPort.java`.
- **Estimate:** about 30 thousand tokens.

#### 3. Feedback port: the qualifying query

- **Depends on:** 2 (compiles in the same reactor; no code dependency).
- **Files:** `platform-commons/.../feedback/spi/FeedbackPort.java`,
  `feedback-spring-boot-starter/.../repository/FeedbackRepository.java`,
  `feedback-spring-boot-starter/.../services/FeedbackService.java`,
  `feedback-spring-boot-starter/.../spi/FeedbackPortImpl.java`,
  `marketplace-orchestrator/.../services/FeedbackAccessService.java`.
- **Executor:** sonnet.
- **Input:** the files above; `.claude/rules/feedback-spring-boot-starter.md`.
- **Change:**
  - Port: `Set<Long> findEntityIdsWithRatingAtLeast(@NonNull EntityType entityType, double minAverage, int minReviews);`
    with a one-line Javadoc ("entities whose visible-review average and count reach both thresholds").
  - Repository: one text-block query on `feedback_aggregate` only:
    `SELECT entity_id FROM feedback_aggregate WHERE entity_type = :entityType AND avg_rating >= :minAverage AND review_count >= :minReviews`.
    The aggregate already counts only `moderation_status = 'NEW'` reviews; do not join other tables.
  - Service and port implementation: pure delegation, in the "Query & filter" section of the service.
  - `FeedbackAccessService`: same-named wrapper returning `Set.of()` when the feedback starter is absent
    (same `findIfAvailable().map(...).orElse(...)` shape as `getAggregate`).
- **Verification:** compiles; the repository test of step 13a covers the threshold (inclusive on both)
  and the exclusion of hidden reviews.
- **Sources:** https://spiegel.medill.northwestern.edu/how-online-reviews-influence-sales/ ,
  https://pro-center.thumbtack.com/?p=21959
- **Estimate:** about 120 thousand tokens.

#### 4. Provider profile port: match a filter among given ids

- **Depends on:** 2.
- **Files:** `platform-commons/.../providerprofile/spi/ProviderProfilePort.java`,
  `provider-profile-spring-boot-starter/.../services/ProviderProfileService.java`,
  its port implementation, `ProviderProfileRepository.java` (only if a new repository method is needed),
  `marketplace-orchestrator/.../services/ProviderProfileReadService.java`.
- **Executor:** sonnet.
- **Input:** the files above; `.claude/rules/provider-profile-spring-boot-starter.md`.
- **Change:**
  - Port: `List<ProviderProfileDto> findMatchingAmong(@NonNull ProviderProfileFilterDto filter, @NonNull Set<Long> ids);`
    one-line Javadoc: "profiles among {@code ids} that match the filter, unpaged, by id".
  - Service: validate the filter like `getFiltered` (`@Valid`); resolve the taxon filter the same way
    (`resolveTaxonFilter`); if it resolves to an empty set return an empty list (existing pattern);
    otherwise intersect it with `ids` (or use `ids` alone when there is no taxon filter) and call the
    existing `findByFilter` path with that set as `allowedIds`, sorted by `id`, without a page limit.
    Reuse `buildIdClause`; do not add a second id-clause builder. Callers pass a non-empty `ids`; no
    empty guard on `ids`.
  - `ProviderProfileReadService`: wrapper, `List.of()` when the starter is absent.
- **Verification:** compiles; step 13a tests filter by kind, category and city among ids, and an id
  outside the set never appears.
- **Sources:** project precedent `AdvertisementService.getFiltered` (allowedIds from `TaxonPort`).
- **Estimate:** about 120 thousand tokens.

#### 5. The new starter `featured-spring-boot-starter`

- **Depends on:** 2.
- **Files (new):** `featured-spring-boot-starter/pom.xml`;
  `src/main/java/org/ost/featured/config/FeaturedAutoConfiguration.java`;
  `src/main/java/org/ost/featured/repository/FeaturedPlacementRepository.java`;
  `src/main/java/org/ost/featured/services/FeaturedPlacementService.java`;
  `src/main/java/org/ost/featured/spi/FeaturedPlacementPortImpl.java`;
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`;
  `src/main/resources/db/featured-changelog/featured-changelog-master.xml`;
  `src/main/resources/db/featured-changelog/changes/01-featured-schema.xml`.
- **Executor:** sonnet.
- **Input:** `contact-spring-boot-starter/` as the template (pom with `architecture.boundedContext`,
  the enforcer `bannedDependencies` rule against sibling starters, autoconfiguration with Liquibase and
  the `ComponentFactory<...Port>` bean, master changelog with its header comment);
  `.claude/rules/contact-spring-boot-starter.md`.
- **Change:**
  - Schema, table `featured_placement` (remarks: "A featured placement of any owning entity: which
    entry, why and until when; one writer owns each reason's rows; no column on the featured
    entity's own table"):
    `id BIGSERIAL` PK; `entity_type VARCHAR(30) NOT NULL` (EntityType, no FK, resolved through
    `FeaturedPlacementPort`); `entity_id BIGINT NOT NULL` (no FK); `reason VARCHAR(30) NOT NULL`
    (FeaturedReason; each value has one writer); `valid_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`;
    `valid_until TIMESTAMP WITH TIME ZONE` (null = until the writer removes the row; a purchase sets
    it); `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`. Unique index
    `idx_featured_placement_entity_reason (entity_type, entity_id, reason)`. Every column has
    `remarks=`; the table remark names the design record by its title, not its number.
  - Repository (`@Repository`, `@RequiredArgsConstructor`, `@SuppressWarnings("java:S1192")`,
    `JdbcClient`, `private static final RowMapper<FeaturedPlacementDto>`), no entity class and no
    `CrudRepository` (there is no trivial save/find):
    `findActive(entityType)`: `... WHERE entity_type = :entityType AND valid_from <= NOW() AND (valid_until IS NULL OR valid_until > NOW())`.
    `deleteForReasonExcept(entityType, reason, Long[] ids)`: `DELETE ... WHERE entity_type = :t AND reason = :r AND NOT (entity_id = ANY(:ids))`.
    `insertMissing(entityType, reason, Long[] ids)`: `INSERT INTO featured_placement (entity_type, entity_id, reason) SELECT :t, unnest(:ids), :r ON CONFLICT (entity_type, entity_id, reason) DO NOTHING`.
    Bind ids as `Long[]` (same reason as `AdvertisementRepository.buildIdClause`).
  - Service: `findActive` delegates; `replaceForReason` runs delete then insert in one
    `@Transactional` method; an empty set deletes every row of that reason and skips the insert.
  - Port implementation: pure delegation.
  - Autoconfiguration: Liquibase bean for this starter's master changelog (same shape as contact),
    `ComponentFactory<FeaturedPlacementPort>` bean, component scan of `org.ost.featured`.
- **Verification:** compiles; step 13a repository test.
- **Sources:** https://www.postgresql.org/docs/current/sql-refreshmaterializedview.html (why not a
  materialized view across starters), project precedent ADR for the contact starter (see
  `.claude/nav/adr-index.md`, "contact-spring-boot-starter module owns contact_info").
- **Estimate:** about 300 thousand tokens.

#### 6. Reactor wiring

- **Depends on:** 5.
- **Files:** `pom.xml`, `.gitignore`, `scripts/sonar/sonar-project.properties`,
  `marketplace-orchestrator/pom.xml`, `integration-tests/pom.xml`.
- **Executor:** haiku.
- **Input:** the five files.
- **Exact edits:**
  - `pom.xml`, modules: old `        <module>feedback-spring-boot-starter</module>` →
    new `        <module>feedback-spring-boot-starter</module>` + newline + `        <module>featured-spring-boot-starter</module>`.
  - `pom.xml`, dependencyManagement: after the block
    ```
                <dependency>
                    <groupId>org.ost</groupId>
                    <artifactId>feedback-spring-boot-starter</artifactId>
                    <version>${project.version}</version>
                </dependency>
    ```
    insert the same block with `featured-spring-boot-starter`.
  - `.gitignore`: after `/feedback-spring-boot-starter/target/` add `/featured-spring-boot-starter/target/`.
  - `scripts/sonar/sonar-project.properties`: after `  feedback-spring-boot-starter/src/main/java,\`
    add `  featured-spring-boot-starter/src/main/java,\`; after
    `  feedback-spring-boot-starter/target/classes,\` add `  featured-spring-boot-starter/target/classes,\`.
  - `marketplace-orchestrator/pom.xml`: after the `feedback-spring-boot-starter` runtime dependency
    block, add the same block with `featured-spring-boot-starter` and `<scope>runtime</scope>`.
  - `integration-tests/pom.xml`: after the `feedback-spring-boot-starter` dependency block (no scope),
    add the same block with `featured-spring-boot-starter`.
- **Verification:** `bash scripts/build-and-test.sh --no-unit --no-integration` builds the reactor
  including the new module.
- **Sources:** project files only.
- **Estimate:** about 40 thousand tokens.

#### 7. Orchestrator: the nightly rating rule

- **Depends on:** 3, 5, 6.
- **Files:** new `marketplace-orchestrator/.../config/FeaturedProperties.java`, new
  `marketplace-orchestrator/.../services/FeaturedRatingRuleService.java`, edit
  `marketplace-orchestrator/.../config/OrchestratorAutoConfiguration.java`, the orchestrator's
  `application` defaults if such a file exists (otherwise only `@DefaultValue`).
- **Executor:** sonnet.
- **Input:** `OrchestratorAutoConfiguration.userCleanupScheduler` and `CleanupProperties` as the
  pattern; `.claude/rules/marketplace-orchestrator.md`.
- **Change:**
  - `FeaturedProperties` record, prefix `app.featured`, `@DefaultValue`s: `topRatedMinAverage` 4.8,
    `topRatedMinReviews` 5, `cronExpression` `0 30 2 * * *`, `timezone` `Europe/Kyiv`, `stripSize` 4.
    Registered with `@EnableConfigurationProperties` next to `CleanupProperties`.
  - `FeaturedRatingRuleService.refreshTopRated()`: ids from
    `FeedbackAccessService.findEntityIdsWithRatingAtLeast(PROVIDER_PROFILE, min, count)`, then
    `ComponentFactory<FeaturedPlacementPort>.ifAvailable(p -> p.replaceForReason(PROVIDER_PROFILE, TOP_RATED, ids))`.
    One domain port through `ComponentFactory` plus one orchestrator service, within the two-port rule.
    No audit (rows are recomputable from audited reviews). Log start and end with the number of ids.
  - Scheduler bean `featuredTopRatedScheduler` (`SchedulingConfigurer`, `CronTrigger` with the
    properties), and one run on `ApplicationReadyEvent` so a fresh deploy does not wait for the night.
- **Verification:** step 13a Level 2 test; the application log shows one run at start-up.
- **Sources:** https://www.airbnb.com/help/article/829 , https://help.etsy.com/hc/en-us/articles/4403058372503
- **Estimate:** about 150 thousand tokens.

#### 8. Orchestrator: the strip read

- **Depends on:** 4, 5, 6, 7 (properties).
- **Files:** new `marketplace-orchestrator/.../services/FeaturedProviderProfile.java` (record), new
  `marketplace-orchestrator/.../services/FeaturedProviderProfileService.java`.
- **Executor:** sonnet.
- **Input:** `ProviderProfileReadService`, `ProviderProfileDisplayEnrichmentService`,
  `FeedbackAccessService`, `FeaturedProperties`.
- **Change:**
  - Record `FeaturedProviderProfile(@NonNull FeaturedReason reason, double avgRating, int reviewCount, @NonNull ProviderProfileDto profile)`.
  - `List<FeaturedProviderProfile> findForStrip(@NonNull ProviderProfileFilterDto filter, @NonNull Locale locale)`:
    active placements for `PROVIDER_PROFILE` (empty list when the featured starter is absent); when
    there are none, return an empty list without further calls; map id to reason (several reasons
    for one id: keep the lowest enum ordinal); `ProviderProfileReadService.findMatchingAmong(filter, ids)`;
    shuffle; keep `stripSize`; enrich with categories/city and actor via
    `ProviderProfileDisplayEnrichmentService`; read each kept profile's aggregate through
    `FeedbackAccessService.getAggregate` (at most `stripSize` calls). The caller's sort is not taken.
    Dependencies: `ComponentFactory<FeaturedPlacementPort>` (the only domain port) and orchestrator
    services.
- **Verification:** step 13a Level 2 test: filter respected, at most `stripSize`, empty when nothing
  qualifies, no profile outside the featured set.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://www.vidhuk.ua/olx/review-2185307 (rotation)
- **Estimate:** about 180 thousand tokens.

#### 9. Self-review guard (moved to improvement-238)

- **Depends on:** none.
- **Files:** `marketplace-orchestrator/.../services/FeedbackWriteService.java`,
  `marketplace-app/.../ui/views/components/FeedbackPanel.java`,
  `marketplace-rest-api/.../api/FeedbackApiController.java` (OpenAPI text only), language files.
- **Executor:** sonnet.
- **Input:** the files above; `SelfActionNotAllowedException` (already mapped by `ApiExceptionHandler`).
- **Change:** in `create`, after loading the profile, throw `SelfActionNotAllowedException("Cannot
  review your own provider profile")` when `profile.getActorId()` equals `actingUserId`. In
  `FeedbackPanel`, do not offer the add form when the signed-in user is `ownerUserId` (the field
  already exists); show nothing instead. Add the response code to the REST operation's
  `@ApiResponse` list with the status `ApiExceptionHandler` already uses.
- **Verification:** step 13a: `FeedbackMatrixScenarioTest` gets the owner case; Playwright: the
  owner sees no review form on their own profile.
- **Sources:** https://wsgr.com/en/insights/ftc-issues-final-rule-banning-fake-and-misleading-consumer-reviews-and-testimonials.html
- **Estimate:** about 100 thousand tokens.

#### 10. Vaadin: the featured strip in the Providers tab

- **Depends on:** 8.
- **Files:** new `marketplace-app/.../ui/views/main/tabs/providers/FeaturedProviderProfileStrip.java`,
  edit `ProvidersView.java`, new `marketplace-app/src/main/frontend/themes/my-app/featured-strip.css`
  (and its import wherever the theme imports the other view CSS files), `I18nKey.java`,
  `messages_en.properties`, `messages_uk.properties`, the UI bean registration if cards use
  `UiComponentFactory` (`ComponentFactoryConfig`).
- **Executor:** sonnet.
- **Input:** `ProvidersView`, `ProviderProfileCardView` (its kind badge is the model for the label),
  `.claude/rules/marketplace-app.md`, `.claude/rules.md` "View Pattern".
- **Change:**
  - The strip: a section with a heading (the reason label, plus the active filter's category and
    city names when set), a "Why these?" control that opens a Vaadin `Popover` with one line built
    from `FeaturedProperties` values, and up to 4 items. Each item reuses `ProviderProfileCardView`
    unchanged inside a wrapper that adds the label "Top rated" and the line "4.9 ★ (12)". No
    automatic rotation, no dots, no arrows. CSS: one row of cards on desktop; on narrow screens a
    single horizontally scrolling row with the next card partly visible (`overflow-x: auto`,
    `scroll-snap-type`). Heading is a real heading element; the item list has `role="list"` and an
    `aria-label` from i18n.
  - `ProvidersView.refresh()`: on the first page only, call `findForStrip(filter, locale)` and render
    the strip; hide it when empty or on any later page. The strip call has its own try/catch inside
    `refresh()` so a failure hides the strip and logs, while the list still renders. `init()` order:
    the strip goes between the query block and the card container. The list, count and
    `PaginationBar` code stays unchanged.
  - i18n keys (en / uk): `featured.reason.top_rated` = "Top rated" / "Високий рейтинг";
    `featured.strip.why` = "Why these?" / "Чому ці?"; `featured.strip.why.top_rated` = "Providers with
    an average of at least {0} from at least {1} reviews. Updated daily. Shown in random order." /
    "Виконавці із середньою оцінкою щонайменше {0} не менше ніж з {1} відгуків. Оновлюється щодня.
    Порядок випадковий."; `featured.strip.rating` = "{0} ★ ({1})" in both; `featured.strip.list`
    = "Top rated providers" / "Виконавці з високим рейтингом". Use the placeholder syntax the existing
    keys use.
- **Verification:** Playwright scenario of step 13a; manual check at 375 px width.
- **Sources:** https://www.nngroup.com/articles/auto-forwarding/ , https://www.nngroup.com/articles/mobile-carousels/ ,
  https://www.w3.org/WAI/tutorials/carousels/ , https://searchengineland.com/new-eu-regulations-compel-more-transparency-for-search-rankings-344482 ,
  https://arxiv.org/abs/2204.12552
- **Estimate:** about 350 thousand tokens.

#### 11. REST: `GET /api/provider-profiles/featured`

- **Depends on:** 8.
- **Files:** `marketplace-rest-api/.../api/ProviderProfileApiController.java`,
  `marketplace-rest-api/.../config/ApiSecurityConfig.java`.
- **Executor:** sonnet.
- **Input:** the `list` method of the controller (filter binding per the REST list ADR in
  `.claude/nav/adr-index.md`, "External REST API list endpoints").
- **Change:** `@GetMapping("/featured")`, `operationId = "listFeaturedProviderProfiles"`, binds
  `@ModelAttribute @Valid ProviderProfileFilterDto` and `locale` exactly like the list, returns
  `List<FeaturedProviderProfile>` as a plain array, no `Link`, no `X-Total-Count`. Description:
  "Public. Up to 4 provider profiles featured now, matching the same filter parameters as
  listProviderProfiles, in random order. Each item carries its reason (TOP_RATED: an average of at
  least 4.8 from at least 5 visible reviews, checked daily), its rating and the profile. Not paged and
  not counted in listProviderProfiles' X-Total-Count; the same profile also appears in that list.
  Empty array when none." (numbers from `FeaturedProperties`, not hard-coded, if the annotation
  allows; otherwise keep the text and note the settings name). Security: add
  `"/api/provider-profiles/featured"` explicitly to the public GET matcher list (it is already covered
  by `/api/provider-profiles/**`; the explicit line documents it).
- **Verification:** step 13a Level 3 scenario; `OperationCoverageTest` passes.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://developers.google.com/search/docs/essentials/spam-policies
- **Estimate:** about 150 thousand tokens.

#### 12. MCP tool and Postman collections

- **Depends on:** 11.
- **Files:** `marketplace-mcp/.../tools/ProviderProfileTools.java`, `marketplace-mcp/.../rest/RestPath.java`
  if paths are listed there, `docs/api-collections/postman/marketplace-rest-api.postman_collection.json`,
  `docs/api-collections/postman/marketplace-mcp.postman_collection.json`, `docs/api-collections/README.md`
  if it lists operations.
- **Executor:** sonnet.
- **Input:** `listProviderProfiles` tool as the pattern; `.claude/rules/marketplace-mcp.md`;
  `docs/api-collections/DECISIONS.md` (MCP flows cover every tool).
- **Change:** tool `listFeaturedProviderProfiles` with the list tool's filter parameters minus `page`
  and `sort`, and `locale`; description states what the REST description states. Add one REST request
  and one MCP flow step to the collections, asserting a 200 and an array.
- **Verification:** `bash docs/api-collections/api-collection-tests.sh` (per its header) passes both
  collections.
- **Sources:** project ADR "The MCP server is a separate module ... wraps the REST API" (see
  `.claude/nav/adr-index.md`).
- **Estimate:** about 200 thousand tokens.

#### 13a. Tests

- **Depends on:** 3-12.
- **Files:** `integration-tests/src/main/java/.../support/TestDataCleaner.java` (add
  `featured_placement`), the `RepositoryTestAutoConfig` allow-list (add `FeaturedAutoConfiguration`),
  new `level1/featured/FeaturedPlacementRepositoryTest.java`, additions to
  `level1/feedback/FeedbackRepositoryTest.java` and the provider-profile repository or service test,
  new `level2/orchestrator/FeaturedRatingRuleServiceTest.java` and
  `FeaturedProviderProfileServiceTest.java`, new `level3/restapi/FeaturedProviderProfileScenarioTest.java`,
  `FeedbackMatrixScenarioTest.java` (owner case, if step 9), a Playwright spec under `playwright/e2e/`.
- **Executor:** sonnet.
- **Input:** `.claude/rules/integration-tests.md`, `.claude/rules/playwright.md`, the existing
  feedback tests as the pattern.
- **Change (cases):** replace is idempotent; rows missing from the set are removed; an empty set
  removes all rows of that reason; rows of another reason are untouched; a row with `valid_until` in
  the past (inserted by SQL in the test) is not active. Threshold inclusive (4.8 and 5 qualify, 4.7 or
  4 reviews do not); a hidden review drops a profile below the count. Filter by kind, category and city
  among ids. Rule service writes exactly the qualifying ids. Strip service: at most 4, matches the
  filter, empty without placements. REST: public, filter applied, array shape, the list endpoint's
  `X-Total-Count` for the same filter is unchanged. Playwright (`--ux`): the strip is absent when
  nothing qualifies; present with label and "Why these?" when a profile qualifies at start-up. If the
  e2e stack cannot make a profile qualify before the start-up run, cover the absent state only and
  record the gap in the task file.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox`; `bash scripts/playwright.sh e2e --full --ux`
  (both through `scripts/activity-monitor.sh`).
- **Sources:** https://www.evanmiller.org/how-not-to-sort-by-average-rating.html (why a minimum count).
- **Estimate:** about 450 thousand tokens.

#### 13b. Documentation lines

- **Depends on:** 5.
- **Files:** `CLAUDE.md`, `.claude/rules/README.md`.
- **Executor:** haiku.
- **Exact edits:**
  - `CLAUDE.md`, module layout: after the line starting `├── feedback-spring-boot-starter` insert
    `├── featured-spring-boot-starter        — Featured placement domain: which entries are lifted out of the catalog and why, generic over EntityRef-owning entities, FeaturedPlacementPortImpl (auto-configured starter)`.
  - `CLAUDE.md`, platform-commons package list: after the line starting `- \`feedback.*\`` insert
    ``- `featured.*` — `featured.spi` (`FeaturedPlacementPort`), `featured.dto` (`FeaturedPlacementDto`), `featured.model` (`FeaturedReason`)``.
  - `CLAUDE.md`, pointer lines: after `→ Feedback domain (rating+text feedback, owned classes): \`.claude/rules/feedback-spring-boot-starter.md\``
    insert a blank line and `→ Featured placement domain (featured entries by reason, owned classes): \`.claude/rules/featured-spring-boot-starter.md\``.
  - `CLAUDE.md`, the "Note:" paragraph listing modules with no hand-authored `DECISIONS.md`: add
    `featured-spring-boot-starter` to that list and change "seven" to "eight" in both places.
  - `.claude/rules/README.md`: after the `feedback-spring-boot-starter.md` bullet insert
    ``- [`featured-spring-boot-starter.md`](featured-spring-boot-starter.md) — `featured-spring-boot-starter/**` ``.
- **Verification:** `git diff` shows only these lines.
- **Estimate:** about 40 thousand tokens.

#### 14. Module documentation and whole-plan verification

- **Depends on:** all.
- **Files:** new `.claude/rules/featured-spring-boot-starter.md` (shape of the contact rules file:
  what it owns, schema, key constraints including "no Maven dependency on any sibling starter" and
  "each reason has one writer"), new `featured-spring-boot-starter/README.md` (per
  `.claude/skills/module-readme-standards/SKILL.md`), `.claude/rules/marketplace-orchestrator.md` (the
  two new services and the scheduler), `.claude/rules/marketplace-app.md` if it lists Providers view
  components, the generated architecture model and pointer `DECISIONS.md` files.
- **Executor:** sonnet.
- **Change:** write the files; run `bash .claude/nav/scripts/check-rule-path-globs.sh` for the new rule
  file; run `bash docs/architecture/scripts/generate-architecture-model.sh`; run
  `/check-architecture-map`; regenerate the ADR index.
- **Verification of the whole:**
  1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green.
  2. `bash scripts/playwright.sh e2e --full --ux` green.
  3. API collections green (step 12).
  4. With the app running: `GET /api/provider-profiles/featured` without a key returns 200 and an
     array of at most 4; `GET /api/provider-profiles` returns the same `X-Total-Count` as before for
     the same filter; `/sitemap.xml` is unchanged.
  5. The MCP tool list contains `listFeaturedProviderProfiles`.
  6. No new file contains a ticket number or an ADR number; every new column has `remarks=`.
- **Estimate:** about 250 thousand tokens.

### Cost summary

| Executor | Steps | Tokens (estimate) |
|---|---|---|
| haiku | 2, 6, 13b | about 110 thousand |
| sonnet | 1, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13a, 14 | about 2.45 million |
| opus | none | 0 |

### Where a purchase plugs in later (not part of this plan)

Add `SPONSORED` to `FeaturedReason`; add a port method that writes one grant with `valid_from` and
`valid_until` (the payment module is its only writer, audited with the payer as actor); add the
"Реклама" / "Advertising" label key; render `SPONSORED` items in their own strip above or below the
`TOP_RATED` one, never mixed. The table, `findActive`, the REST endpoint shape and the MCP tool stay
as they are.

## Related

- [improvement-238](../completed/tasks/improvement-238-block-self-review-of-provider-profile.md) — the self-review guard the rating rule relies on.
- [improvement-237](improvement-237-purchase-reason-and-payment-module.md) — the purchase as one more reason for featuring, with the payment module; out of scope here, it plugs into this mechanism later.
- The private product notes (outside version control): the roadmap and the promoted-listings feature spec; read them as input, not as the decision.
- [improvement-236](../completed/tasks/improvement-236-list-order-and-edit-bump-rest-mcp-ui-parity.md) — the default list order and the free bump on edit, found by this task's research; settle it before any placement design.
- `marketplace-app/DECISIONS.md` and `.claude/nav/adr-index.md` hold the decisions on the REST list endpoints, paging and filters that the design must respect.

## Operational notes
- token_cost_review: n/a
- token_cost_research: 230115 (Fable deep-researcher, 70 tool uses, 818 s)
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
