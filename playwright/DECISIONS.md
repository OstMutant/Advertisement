# Architecture & Technical Decisions — playwright

---

## 2026-05-12 — `--ux` flag controls screenshots

**Decision:** Named screenshots are only taken and attached to the HTML report when `--ux` is passed to `run.sh`. Without `--ux`, `screenshot()` calls are no-ops.

**Why:** Screenshots slow down CI runs and add noise to reports when not needed for UX analysis. Keeping them opt-in makes the default run fast and clean.

**Implementation:** `run.sh` sets `PW_SCREENSHOTS=1` env var when `--ux` is present; `_test-helpers.js` `screenshot()` guards on `process.env.PW_SCREENSHOTS`. Screenshots are attached to the HTML report via `test.info().attach()` and stored in `pw-report/data/`.
