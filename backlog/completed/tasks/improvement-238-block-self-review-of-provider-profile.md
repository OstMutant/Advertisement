# improvement-238: A profile owner can review their own provider profile — block it when the review is created

**Status:** done 2026-11-05
**Type:** bug — a gap in the rating that any rating-based feature (the featured strip of improvement-235 first of all) relies on
**Module:** `marketplace-orchestrator` (`FeedbackWriteService.create`), `marketplace-app` (`FeedbackPanel`), `marketplace-rest-api` (`FeedbackApiController` response documentation), `marketplace-mcp` (tool description if it lists errors), the language files, `integration-tests` (`FeedbackMatrixScenarioTest`), `playwright/e2e`
**Priority:** 🟡 top — a real hole in the rating: a profile owner can write reviews of their own profile, which would let them qualify for the featured strip alone
**When:** independent, no blockers; should be done before the featured strip of [improvement-235](improvement-235-featured-placement-mechanism.md) goes live

## Current state

Verified against the code on 2026-11-05:

- `FeedbackWriteService.create` only checks that the provider profile exists, then saves the review with the acting user as author; it never compares the author with the profile's owner.
- `FeedbackService` in the feedback starter already refuses a user flagging their own review or reply (`SelfActionNotAllowedException`), and `ApiExceptionHandler` already maps that exception for REST. The same guard does not exist for creating a review.
- `FeedbackPanel` already carries the profile owner's user id (`ownerUserId`), so the UI knows who the owner is.

## Why change

A rating is only worth showing, ranking or featuring if the owner cannot write it. With a minimum of five reviews for the featured strip, one owner's own review is a fifth of the qualification.

## Expected benefit

Every door (UI, REST, MCP) refuses a review of one's own profile with the same error; the rating reflects other people's opinion only.

## Approach

Decided with the user (2026-11-05): block at creation, in the orchestrator, so all three doors share it.

1. `FeedbackWriteService.create`: after loading the profile, throw `SelfActionNotAllowedException` ("Cannot review your own provider profile") when the profile's owner equals `actingUserId`.
2. `FeedbackPanel`: do not offer the add form when the signed-in user is the profile's owner.
3. REST: add the response to the operation's documented responses, with the status `ApiExceptionHandler` already uses for this exception; the MCP tool description follows the REST text.
4. Tests: the owner case in `FeedbackMatrixScenarioTest`; a Playwright check that the owner sees no review form on their own profile.

Technical plan (verified against the code 2026-11-05):

- The profile owner is `ProviderProfileDto.actorId`.
- `FeedbackWriteService.create`: keep the loaded profile; `if (profile.actorId().equals(actingUserId)) throw new SelfActionNotAllowedException("Cannot review your own provider profile")` (`org.ost.platform.core`, already mapped to 403 by `ApiExceptionHandler`).
- `FeedbackPanel`: a `canAdd()` helper (`access.isLoggedIn() && !hasOwnEntry && !isOwner`) replaces the two identical conditions in `buildFormContainer` and `updateAddControlsVisibility`; `isOwner` is derived from the existing `ownerUserId`.
- `FeedbackApiController.createProviderProfileFeedback`: add `@ApiResponse(responseCode = "403", description = "The caller owns the reviewed profile")`.
- Tests: owner case in `FeedbackWriteServiceTest`, `FeedbackMatrixScenarioTest` and a Playwright check.

Rejected: leaving the review in place and ignoring it only in the featured rule — more code, and the owner would still see a review of themselves counted on their own page.

Reviews that already exist from an owner on their own profile are not checked here; whether any exist in the data is for the integration or seed data check during the work.

## Related

- [improvement-235](improvement-235-featured-placement-mechanism.md) — the featured strip whose rating rule needs this guard.
- `.claude/rules/feedback-spring-boot-starter.md` and `.claude/rules/marketplace-orchestrator.md`.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: Bug fix, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: build the reactor and run targeted tests, then deploy and verify the UI change
- flows_chosen: scripts/build-and-test.sh, scripts/deploy-and-run.sh and scripts/playwright.sh, each through the activity monitor
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 0 / 1
- adrs_referenced: none

### Agent calls
- apply the three main-code edits | subagent_type=general-purpose | tokens=105296 | tool_uses=12 | duration_s=66 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --unit-test FeedbackWriteServiceTest --integration-test FeedbackMatrixScenarioTest --sandbox | duration_s=236 | mode=background | result=pass
- scripts/deploy-and-run.sh --reset-only-db --reuse-jar | duration_s=47 | mode=background | result=pass
- scripts/playwright.sh 04-provider-profile-flow --ux | duration_s=232 | mode=background | result=pass
