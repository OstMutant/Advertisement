# Playwright UI Tests

Automated E2E tests using `@playwright/test` (headless Chromium).

## Requirements

- Docker Desktop (Windows) or Docker Engine (Linux/WSL2)
- App image already built: `docker build -f Dockerfile -t marketplace-app .`
- DB + MinIO containers running: `docker-compose -f docker-compose.db.yml -f docker-compose.minio.yml up -d`

The `marketplace-app` container is started automatically if stopped. Test accounts are seeded automatically before each run.

## Running

### Linux / WSL

```bash
bash /app/playwright/run.sh                        # all tests
bash /app/playwright/run.sh core                   # all core tests
bash /app/playwright/run.sh audit                  # all audit tests
bash /app/playwright/run.sh attachment             # all attachment tests
bash /app/playwright/run.sh smoke                  # one scenario by name
bash /app/playwright/run.sh audit/advertisement-history  # by group/name
bash /app/playwright/run.sh --ux                   # all tests with screenshots
bash /app/playwright/run.sh smoke --ux             # one scenario with screenshots
```

### Windows

`run.bat` is the entry point on Windows. It delegates to `run.sh` via WSL.

```bat
playwright\run.bat
playwright\run.bat core
playwright\run.bat audit
playwright\run.bat attachment
playwright\run.bat smoke
playwright\run.bat audit\advertisement-history
playwright\run.bat --ux
playwright\run.bat /?
```

Requirements: WSL2 + Docker Desktop with WSL integration enabled (`Settings → Resources → WSL Integration`).

After run:
- HTML report (with screenshots on failure): `/app/playwright/pw-report/index.html`
- Local screenshots (`--ux` only): `/app/playwright/screenshots/`

## What run.sh does automatically

1. **App readiness** — if `marketplace-app` is stopped, starts it and waits for `Started Application` in Docker logs (up to 120 s). If the container doesn't exist, prints the exact `docker run` command and exits.
2. **Test account seeding** — runs `/app/database/seed.sql` against the postgres container (detected by port 5432). Creates `user1–user3@example.com` with password `password` if they don't exist. Safe to run repeatedly (`ON CONFLICT DO NOTHING`).

## Test accounts

| Email | Role |
|---|---|
| `user1@example.com` | regular user |
| `user2@example.com` | regular user |
| `user3@example.com` | admin |

Password for all accounts: `password`

---

## Scenarios

### Core (`core/`)

Run with: `bash run.sh core`

| File | What it tests |
|---|---|
| `smoke.spec.js` | Language switch, full user flow, admin flow, YouTube lightbox |
| `add-advertisement.spec.js` | Creating an advertisement |
| `edit-advertisement.spec.js` | Editing an advertisement title |
| `filter-advertisements.spec.js` | Filter panel: text search, sort, clear |
| `filter-users.spec.js` | Filter users by name and role (admin) |
| `users-view.spec.js` | Users grid: view overlay + edit overlay (admin) |
| `test-view.spec.js` | Opening advertisement detail overlay |
| `change-language.spec.js` | Language switching (unauthenticated) |
| `settings.spec.js` | Settings overlay: change page size |
| `settings-defaults.spec.js` | New user restores settings to defaults |

### Audit (`audit/`)

Run with: `bash run.sh audit`

| File | What it tests |
|---|---|
| `advertisement-history.spec.js` | History: create → edit → restore |
| `history-deep.spec.js` | Multiple versions, badges, restore flow |
| `activity-types.spec.js` | CREATED / UPDATED / DELETED badges |
| `user-activity.spec.js` | Activity tab in settings after creating an ad |
| `settings-activity.spec.js` | Settings change activity + restore |
| `user-edit-diff.spec.js` | User edit shows diff + unchanged fields in activity |
| `fields-always-shown.spec.js` | All text fields shown in history and activity (unchanged = current value) |

### Attachment (`attachment/`)

Run with: `bash run.sh attachment`

| File | What it tests |
|---|---|
| `upload-image.spec.js` | Uploading a single image |
| `upload-gallery.spec.js` | Uploading multiple images, delete one |
| `upload-video.spec.js` | MP4 upload, play icon, attachment + card lightbox |
| `lightbox-video-switch.spec.js` | YouTube ↔ image switching in CardMediaLightbox |
| `verify-media-history.spec.js` | Media changes visible in history tab |
| `verify-thumbnail-history.spec.js` | Card thumbnails + media history |
| `media-history-always-shown.spec.js` | Media field shown in text-only edit rows |
| `admin-media-edit.spec.js` | Admin edits another user's media — single current-state badge |
| `media-activity.spec.js` | Media diffs in history and activity; text+image = 1 row |

---

### `change-language.spec.js` — Language switching (unauthenticated)

**Steps:**
1. Open `/` without logging in.
2. Verify `.locale-combobox` is visible.
3. Click the locale combobox, select English — wait for `networkidle`.
4. Click the locale combobox again, select Ukrainian — wait for `networkidle`.

**Validation:**
- Locale combobox exists on the login/home page before authentication.
- No JS errors on language switch.

**Screenshots:** `lang-02-after-switch`, `lang-03-back-to-ukrainian`

---

### `test-view.spec.js` — Advertisement view overlay

**Steps:**
1. Login as `user1@example.com`.
2. Wait for `.advertisement-card` list to load.
3. Click the first card.
4. Verify `.overlay__view-title` is visible inside `.base-overlay.overlay--visible`.
5. Click breadcrumb back — overlay closes.

**Validation:**
- View overlay opens on card click.
- `.overlay__view-title` is visible after open.
- Overlay closes when navigating back.

**Screenshots:** `view-overlay-01-open`

---

### `add-advertisement.spec.js` — Creating an advertisement

**Steps:**
1. Login as `user1@example.com`.
2. Click `.add-advertisement-button`.
3. Fill title `Spec Test Ad`, description `Created by spec test`.
4. Click Save — wait for overlay to close.
5. Verify the new card appears in the list.

**Validation:**
- Card with title `Spec Test Ad` is visible in the main list after create.

**Screenshots:** `add-ad-01-created`

---

### `edit-advertisement.spec.js` — Editing an advertisement

**Steps:**
1. Login as `user2@example.com`.
2. Wait for advertisement list to load.
3. Click first card — overlay opens.
4. Click Edit button.
5. Clear title field and fill `Updated by Playwright`.
6. Click Save — wait for `.overlay__view-title` to appear.
7. Verify updated title is visible in the page body.

**Validation:**
- Title `Updated by Playwright` is visible in the view after save.

**Screenshots:** `edit-02-advertisement-detail`

---

### `filter-advertisements.spec.js` — Filter panel: text search, sort, clear

**Steps:**
1. Login as `user1@example.com`.
2. Wait for advertisement cards to load.
3. Click `.query-status-bar` to open filter panel.
4. Fill text filter with `Test`, click Apply.
5. Click Clear — cards reload.
6. Click sort button if present.

**Validation:**
- Filter panel (`.advertisement-query-block`) becomes visible on click.
- Apply button triggers filter; cards reload.
- Clear button restores full card list.

**Screenshots:** `filter-ads-03-filtered-by-title`

---

### `filter-users.spec.js` — Filter users (admin)

**Steps:**
1. Login as `user3@example.com` (admin).
2. Click Users tab — wait for `vaadin-grid.user-grid`.
3. Click `.query-status-bar` to open filter panel.
4. Fill name filter with `User 1`, click Apply — wait for grid rows.
5. Click Clear — wait for grid rows.
6. Open role multi-select, choose ADMIN, click Apply — wait for grid rows.
7. Click Clear again.

**Validation:**
- `.user-query-block` visible after filter panel opens.
- Grid rows reload after each apply/clear.
- Role filter reduces grid to admin users only.

**Screenshots:** `filter-users-02-filter-panel-open`, `filter-users-03-filtered-by-name`, `filter-users-04-filtered-by-role`

---

### `settings.spec.js` — Settings overlay: change page size

**Steps:**
1. Login as `user1@example.com`.
2. Verify `.header-settings-button` is visible.
3. Click it — overlay opens.
4. Verify `vaadin-integer-field` is visible in `.settings-overlay-content`.
5. Change the value to `20`.
6. Click Save — wait for `networkidle`.

**Validation:**
- Settings overlay opens.
- Integer field for ads page size is present.
- Save completes without error.

**Screenshots:** `settings-02-overlay`, `settings-03-saved`

---

### `settings-defaults.spec.js` — New user can restore settings to defaults

**Steps:**
1. Sign up a new user with a unique email (`newuser-{timestamp}@example.com`).
2. Log in as the new user.
3. Open settings — verify default ads page size is `20`.
4. Change page size to `10`, click Save.
5. Close overlay, re-open settings, open Activity tab.
6. Verify restore button is visible.
7. Click restore — confirm dialog.
8. Verify ads page size is restored to `20`.

**Validation:**
- Default page size for a new account is `20`.
- After first change, a restore button appears in activity.
- Clicking restore reverts the value to `20`.

**Screenshots:** `settings-defaults-01-changed`, `settings-defaults-02-restore-visible`, `settings-defaults-03-restored`

---

### `users-view.spec.js` — Users grid (admin)

**Steps:**
1. Login as `user3@example.com` (admin).
2. Verify Users tab is visible.
3. Click Users tab — wait for `vaadin-grid.user-grid`.
4. Verify grid has at least one cell.
5. Click a user name cell — overlay opens.
6. Click breadcrumb back — overlay closes.
7. Click the edit (actions) button for the first user — edit overlay opens.
8. Click Cancel/Close — overlay closes.

**Validation:**
- Users grid is non-empty for admin.
- Click on name opens view overlay.
- Edit button opens edit overlay.

**Screenshots:** `users-01-list`, `users-02-view-overlay`, `users-03-edit-overlay`

---

### `user-edit-diff.spec.js` — User edit shows diff in settings activity

**Steps:**
1. Login as `user3@example.com` (admin).
2. Open Users tab — wait for grid.
3. Click edit button for first user — overlay opens.
4. Read current name; toggle ` Edited` suffix; click Save.
5. Open Settings → Activity tab.
6. Verify `.user-activity-changes` rows exist.
7. Verify `→` arrow is present in the activity text (diff shown).
8. Verify unchanged fields (email, role) appear with `--unchanged` CSS class.

**Validation:**
- Editing a user name creates an activity entry with a before→after diff.
- Unchanged fields (email, role) are still listed in the activity row, styled as unchanged.

**Screenshots:** `user-edit-diff-01-activity`

---

### `user-activity.spec.js` — User activity tab in settings

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `User Activity Test Ad`.
3. Open Settings.
4. Verify an Activity tab is present (tab label matches `activ|активн`).
5. Click Activity tab — verify `.user-activity-list` is visible with at least one row.

**Validation:**
- Settings overlay has an Activity tab.
- At least one `.user-activity-row` is visible after creating an ad.

**Screenshots:** `activity-01-ad-created`

---

### `activity-types.spec.js` — CREATED / UPDATED / DELETED badges + entity type badges

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `Activity Types Test Ad`.
3. Open it, edit description to `Updated content`, save.
4. If delete button is available on the card, delete the ad and confirm.
5. Open Settings → Activity tab.
6. Verify `.user-activity-type--advertisement` badge is present.
7. Verify CREATED action text (`Створ|Creat`) appears in some row.
8. Verify UPDATED action text (`Оновл|Updat`) appears in some row.
9. If ad was deleted: verify `.user-activity-row--deleted` marker exists and contains "видалено|deleted".
10. Verify `.user-activity-time` exists and is non-empty.

**Validation:**
- Each action type (CREATED, UPDATED, optionally DELETED) is represented in the activity feed.
- Deleted entities are marked visually with `--deleted` CSS class.
- Every row has a non-empty timestamp.

**Screenshots:** `acttypes-01-created`, `acttypes-02-edited`

---

### `advertisement-history.spec.js` — History: create → edit → restore

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `History Test Ad` with description `Original description v1`.
3. Open it, open History tab — verify list is visible and has at least 1 row.
4. Switch to view tab, click Edit, change description to `Updated description v2`, save.
5. Open History — verify at least 2 rows, at least one `.adv-history-changes` element.
6. Verify at least one `.adv-history-restore-btn` is present.
7. Click last restore button — confirm dialog appears, confirm it.
8. Verify view overlay is shown after restore.
9. Re-open History — verify no `.adv-history-action--restored` badges, at least one `.adv-history-action--updated`.

**Validation:**
- History tab shows entries for both CREATED and UPDATED events.
- Changes summary (`.adv-history-changes`) is present.
- Restore button triggers a dialog and then shows the view.
- Restore creates a new UPDATED entry (no "RESTORED" badge).

**Screenshots:** `adv-history-01-after-edit`, `adv-history-02-after-restore`

---

### `history-deep.spec.js` — History: multiple versions, badges, restore

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `History Deep Test` (v1).
3. Open History — verify CREATED badge, version label `v1`, no restore button (only 1 entry).
4. Edit twice: description → `Version 2` (v2), `Version 3` (v3). Re-open History after each edit.
5. Verify 3+ rows; version labels include `v3`; UPDATED badges present.
6. Verify newest row (v3) has no restore button; older rows have restore buttons.
7. Verify `.adv-history-changes` elements exist.
8. Click last restore button (v1) — confirm dialog — confirm.
9. Verify view shown after restore; re-open History and verify row count increased.

**Validation:**
- CREATED badge on initial entry; UPDATED badges on edits.
- Only one `v1/v2/v3` label ordering (latest first).
- Latest version has no restore button; older versions do.
- Restore adds a new entry (row count grows).

**Screenshots:** `history-deep-01-three-versions`, `history-deep-02-after-restore`

---

### `verify-media-history.spec.js` — Media changes in history tab

**Test 1: create with image → media change visible in history**

Steps:
1. Login as `user1@example.com`.
2. Create advertisement `Verify Media History` with a generated PNG.
3. Open History tab.
4. Verify at least 1 row; verify history text contains `зображення|image`.

Validation:
- Image upload during create is recorded in the history entry.

Screenshots: `verify-media-01-history`

**Test 2: delete image → media deletion visible in history**

Steps:
1. Login as `user1@example.com`.
2. Create advertisement `Verify Media Delete History` with a generated PNG.
3. Open edit mode, click the delete button on the gallery item.
4. Save, open History.
5. Verify history text contains `зображення|image`.

Validation:
- Image deletion is recorded in the history entry.

Screenshots: `verify-media-02-delete-history`

---

### `verify-thumbnail-history.spec.js` — Card thumbnails + media history

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `Thumbnail Verify Ad` with a downloaded PNG avatar.
3. Verify at least one `.advertisement-card img` has a non-empty `src` (real thumbnail).
4. Open the ad, open History tab.
5. Verify history text contains `зображення|image`.

**Validation:**
- Advertisement cards show real thumbnail images (not placeholders) after image upload.
- Media change is visible in the History tab.

**Screenshots:** `thumbnail-01-cards`, `thumbnail-02-history`

---

### `upload-image.spec.js` — Single image upload

**Steps:**
1. Login as `user1@example.com`.
2. Download a PNG avatar to `/tmp/upload-single.png`.
3. Create advertisement `Upload Image Test` (no image yet).
4. Open the ad, click Edit.
5. Upload `/tmp/upload-single.png` via `vaadin-upload`.
6. Wait for `.attachment-gallery__item` to appear.
7. Click Save, verify at least 1 `img` is visible inside the overlay.

**Validation:**
- Uploaded image appears in the attachment gallery in edit mode.
- After save, at least one image is visible in the view overlay.

**Screenshots:** `upload-image-01-saved`

---

### `upload-gallery.spec.js` — Multiple image upload (gallery)

**Steps:**
1. Login as `user1@example.com`.
2. Download 3 PNG avatars (`gallery-a`, `gallery-b`, `gallery-c`).
3. Create advertisement `Gallery Upload Test`.
4. Open edit mode, upload all 3 files simultaneously.
5. Save — verify at least 2 images visible in view overlay.
6. Re-open edit mode, click delete on the first gallery item, save.

**Validation:**
- All 3 images appear in the gallery after upload.
- Deleting one image updates the gallery (no error on save).

**Screenshots:** `gallery-01-multi-images`

---

### `upload-video.spec.js` — MP4 video upload

**Test 1: upload mp4 — gallery shows play icon, lightbox shows video element**

Steps:
1. Login as `user1@example.com`.
2. Create a minimal valid MP4 at `/tmp/test-upload.mp4`.
3. Create advertisement `Video Upload Test`.
4. Open edit mode, upload the MP4 file.
5. Verify `.attachment-gallery__play-icon` is visible in the gallery.
6. Save; verify play icon is still visible in view mode.
7. Click the play icon — `.attachment-lightbox` opens with a `<video>` element and non-empty `src`.
8. Close lightbox; navigate back to main list.
9. Click the card thumbnail — card lightbox opens; verify `.card-lightbox__main-video` has a non-empty, non-blank `src`.
10. Close via backdrop click (outside the dialog). Verify lightbox closes.
11. Open card lightbox again; close via X button. Verify lightbox closes.

Validation:
- MP4 uploads are treated as video: play icon shown in gallery.
- Attachment lightbox shows a `<video>` element (not an image).
- Card thumbnail lightbox shows video with valid `src`.
- Backdrop click and X button both close the card lightbox.

Screenshots: `upload-video-01-temp`, `upload-video-02-saved`, `upload-video-03-lightbox`, `upload-video-04-card-lightbox`, `upload-video-04b-lightbox-closed`, `upload-video-05-backdrop-close`, `upload-video-06-x-close`

**Test 2: video appears in history**

Steps:
1. Login as `user1@example.com`.
2. Create advertisement `Video History Test` with MP4 attached.
3. Open History tab — verify history text is non-empty.

Validation:
- Video upload is recorded in the history tab.

Screenshots: `upload-video-04-history`

---

### `lightbox-video-switch.spec.js` — CardMediaLightbox: YouTube ↔ image switching

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `Lightbox Switch Test` with one YouTube URL (`dQw4w9WgXcQ`) and one uploaded PNG.
3. Click the card thumbnail — card lightbox opens.
4. Verify the iframe `src` matches `youtube.com/embed` (YouTube is the active item).
5. Click thumbnail strip item index 1 (image) — verify `.card-lightbox__main-image` is visible and iframe `src` is `about:blank`.
6. Click thumbnail strip item index 0 (YouTube) again — verify iframe `src` matches `youtube.com/embed` again.

**Validation:**
- Opening lightbox on a YouTube-first ad shows the YouTube embed immediately.
- Switching to an image blanks the iframe (no video sound leaking).
- Switching back to YouTube restores the embed URL (iframe `src` is not blank).

**Screenshots:** `lightbox-01-youtube-open`, `lightbox-02-switched-to-image`, `lightbox-03-youtube-restored`

---

### `admin-media-edit.spec.js` — Admin editing another user's ad media

**Steps:**
1. Login as `user1@example.com`. Create `Admin Media Edit Bug Test` (text only), then edit description (creates v2 text-only audit entry).
2. Log out; login as `user3@example.com` (admin).
3. Open the ad in edit mode, upload an image — save (creates audit v3 + attachment snapshot rn=1).
4. Open edit mode again, delete the existing image, upload a different image — save (creates audit v4 + attachment snapshot rn=2).
5. Open History tab; count `.adv-history-current-badge` elements.

**Validation:**
- Exactly 1 `.adv-history-current-badge` is present.
- Multiple attachment snapshots from different edit sessions must not all be marked as current.

**Screenshots:** `admin-media-edit-history`

---

### `media-history-always-shown.spec.js` — Media field always present in history rows

**Steps:**
1. Login as `user1@example.com`.
2. Create advertisement `Media Always Shown {timestamp}` with a generated PNG (v1 — with image).
3. Open edit mode, delete the image, save (v2 — image deleted).
4. Open edit mode again, change the title, save (v3 — text-only edit, no media change).
5. Open History tab; verify at least 3 rows.
6. In the latest row (v3), check all `.adv-history-changes-item` texts.
7. Verify a line matching `зображення|images:` exists.
8. Verify that line contains `—` (em dash — no images at current state).

**Validation:**
- The media line always appears in every history row, even when no media change occurred in that edit.
- For v3 (text-only edit), the media field shows `—` because images were deleted in v2.

**Screenshots:** `media-always-shown-01-v3-has-media-line`

---

### `media-activity.spec.js` — Media diffs in history and activity feed

**Steps:**
1. Login as `user3@example.com`.
2. Create advertisement `Media Activity Smoke {timestamp}` with one image (paths[0]).
3. Open the ad, open History tab — verify history text contains `зображення|image`.
4. Close overlay; open Settings → Activity tab.
5. Verify activity text contains `зображення|image`.
6. Verify the ad appears as exactly **1** row in activity (not 2 separate rows for text + image).
7. Close settings; open the ad in edit mode, add a second image (paths[1]), save.
8. Open History tab — verify media diff arrow `→` is present.
9. Verify at least one `.adv-history-restore-btn` exists.
10. Close overlay; open the ad, open History, click the last restore button, confirm.
11. Re-open History — verify 3+ rows; verify media change is in history text.

**Validation:**
- After create with title+image, activity feed shows **1 combined row** (not separate rows for text and image changes).
- Media diffs use `→` arrow format in history, matching text field diff display.
- Restore adds a new history entry.

**Screenshots:** `media-activity-01-history`, `media-activity-02-settings`, `media-activity-03-history-diff`, `media-activity-04-after-restore`

---

### `settings-activity.spec.js` — Settings change activity + restore

**Test 1: page size change creates activity entry, restore reverts it**

Steps:
1. Login as `user3@example.com`.
2. Open Settings — read current ads page size.
3. Change to a different value (toggle between 10 and 15), save. Wait 1100 ms (debounce).
4. Change back to original value, save.
5. Verify an Activity tab exists in the settings overlay.
6. Open Activity tab — verify `.user-activity-row` rows exist; text contains `сторінці|page`.
7. Verify `.user-activity-changes` elements exist and `→` arrow is present in activity text.
8. Verify `.adv-history-restore-btn` is present.
9. Click restore button — confirm dialog — confirm.
10. Verify settings overlay stays open after restore (`.overlay--visible`, `.overlay__form-fields-card` both visible).
11. Verify ads page size equals the `newSize` value from step 3 (restore brought it back).

Validation:
- Settings changes appear in the activity feed with a before→after diff.
- Restore button restores the snapshot value and the overlay remains open.

Screenshots: `settings-activity-01-activity-list`, `settings-activity-02-after-restore`

**Test 2: create ad with title+image shows as single activity row**

Steps:
1. Login as `user3@example.com`.
2. Create advertisement `Settings Activity Merge Test {timestamp}` with a generated PNG.
3. Open Settings → Activity tab.
4. Find all `.user-activity-row` elements that contain the ad title.
5. Verify count === 1 (text + image changes merged into one row).
6. In that single row, verify both `зображення|image` and the ad title are present.

Validation:
- Creating an ad with both text fields and an image produces exactly 1 activity row (not 2).
- The merged row contains both the title change and the image change.

Screenshots: `settings-activity-03-merge-check`

---

### `smoke.spec.js` — Smoke: language, full user flow, admin flow

**Test 1: language switch**
- Switch locale combobox from Ukrainian to English; verify UI updates.

**Test 2: full user flow**
- Login as `user1@example.com`.
- Create, upload image, save ad.
- Edit ad title, save.
- Open filter, search, clear.
- Open settings, change page size, save.
- Open History tab; verify entries.
- Open Settings → Activity tab; verify entries.
- Open ad, add YouTube URL, save.

**Test 3: admin flow (users grid)**
- Login as `user3@example.com`.
- Open Users tab; verify grid loads.

**Test 4: history tab (smoke)**
- Create ad, edit once, open History, verify rows and changes summary.

**Test 5: YouTube lightbox (smoke)**
- Create ad with YouTube URL; click card thumbnail; verify iframe with `youtube.com/embed`.

**Validation (all smoke tests):**
- No JS crash blocks the primary flows.
- Core elements (cards, overlays, tabs) are reachable and render.

**Screenshots:** Various per sub-test (prefixed with step name).

---

### `fields-always-shown.spec.js` — All text fields in history and activity

**Test 1: history — edit description only → unchanged title shown in every row**

Steps:
1. Login as `user1@example.com`.
2. Create advertisement `Fields Always Shown {timestamp}` with title + description `Original description v1`.
3. Open History tab — check CREATED row has ≥ 2 `.adv-history-changes-item` elements containing both title text and description text.
4. Edit description to `Updated description v2` (title unchanged), save.
5. Open History tab — check the newest UPDATED row:
   - ≥ 2 `.adv-history-changes-item` elements.
   - At least one item contains the original title (unchanged).
   - At least one item contains `→` (changed description).
   - At least one item has `.adv-history-changes-item--unchanged` CSS class.

Validation:
- CREATED row always shows all fields, not just the non-empty ones.
- UPDATED row (one field changed) shows the changed field as diff AND the unchanged field as current value with `--unchanged` styling.

Screenshots: `fields-01-history-created-both-fields`, `fields-02-history-updated-title-unchanged`

**Test 2: activity — create shows both fields; update title only → description shown as unchanged**

Steps:
1. Login as `user1@example.com`.
2. Create advertisement `Activity Fields Test {timestamp}` with title + description `Description stays the same`.
3. Open Settings → Activity tab; find the CREATED row for this ad:
   - ≥ 2 `.user-activity-changes-item` elements.
   - Contains title text and description text.
4. Edit the title to `{TITLE_V1} edited` (description unchanged), save.
5. Open Settings → Activity tab; find the UPDATED row:
   - ≥ 2 `.user-activity-changes-item` elements.
   - At least one contains `→` (title diff).
   - At least one contains the original description text (unchanged).
   - At least one has `.user-activity-changes-item--unchanged` CSS class.

Validation:
- Activity CREATED row shows all fields (not just non-empty ones).
- Activity UPDATED row shows diff for the changed field and current value (styled as unchanged) for every field that did not change.

Screenshots: `fields-03-activity-created-both-fields`, `fields-04-activity-updated-desc-unchanged`

---

## Adding a new scenario

1. Create `/app/playwright/my-scenario.spec.js`
2. `const { test, expect, loginAs, ... } = require('./_test-helpers');`
3. Run with: `bash /app/playwright/run.sh my-scenario`
