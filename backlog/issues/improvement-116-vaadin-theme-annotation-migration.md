# improvement-116: Migrate off deprecated Vaadin `@Theme` annotation

**Type:** improvement — framework deprecation with `forRemoval = true`
**Module:** `marketplace-app` (`org.ost.marketplace.config.ui.AppShell`)
**Priority:** low — already consciously deferred (see below), no live bug, no user-visible issue today
**When:** independent, no blockers — but requires a full visual pass (Playwright `e2e --full --ux`)
before merging, not just a compile check; batch with any other theme/styling touch

## Problem

`AppShell.java` uses `@Theme("my-app")` (`com.vaadin.flow.theme.Theme`), which carries
`@Deprecated(since = "25.0", forRemoval = true)` at the class level (confirmed via `javap -v` on
the installed `flow-server-25.2.4.jar` — not just a javadoc note, an actual forRemoval annotation).
The class already carries `@SuppressWarnings("java:S1874")` (SonarQube's "deprecated code should
not be used" rule) directly above the annotation, meaning this was already noticed and
consciously deferred at some earlier point rather than left as an oversight.

Found while triaging an IntelliJ IDEA inspection export (see improvement-115) — surfaced here as
its own issue because, unlike the rest of that cleanup, this one isn't a safe mechanical fix:
Vaadin 25's replacement mechanism is automatic theme discovery via the `frontend/themes/<name>/`
folder structure (no annotation needed at all), and switching to it changes how the app's CSS is
loaded — verifying it didn't silently break styling requires a full visual pass, not just a green
compile.

## Suggested fix

1. Confirm the current theme folder already matches Vaadin's expected `frontend/themes/my-app/`
   convention (it should, since `@Theme("my-app")` already points at it).
2. Remove the `@Theme("my-app")` annotation and its `@SuppressWarnings("java:S1874")` from
   `AppShell.java` once Vaadin's automatic discovery is confirmed to pick up the same folder.
3. Rebuild via `bash scripts/deploy.sh` (full rebuild, not the JAR hot-swap path, since frontend
   bundling changes) and run the full `bash scripts/playwright.sh e2e --full --ux` scenario,
   comparing screenshots against the pre-change baseline to confirm no visual regression.

## Related

Sibling cleanup: [improvement-115](improvement-115-intellij-inspection-cleanup-pass.md)
(IntelliJ inspection export triage) — this item was carved out of that issue's Deprecation
sub-pass because it needs a visual-regression pass, not just a compile check.
