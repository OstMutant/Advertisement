# Timeline Tab — Plan

## Steps

<!-- Each step = one commit. Format:
     ### Step N — <title>
     **Status:** [ ] todo / [~] in progress / [x] done
     **Prompt:** <exact prompt to execute this step>
     **Notes:** <gotchas or deviations found during execution>
-->

### Step 1 — platform-commons: AuditTimelineFilterDto + AuditPort methods + UserSettingsDto + SettingsSnapshotDto

**Status:** [ ] todo

**Files:**
- NEW `platform-commons/src/main/java/org/ost/platform/audit/dto/AuditTimelineFilterDto.java`
- MODIFY `platform-commons/src/main/java/org/ost/platform/audit/spi/AuditPort.java`
- MODIFY `platform-commons/src/main/java/org/ost/platform/user/dto/UserSettingsDto.java`
- MODIFY `platform-commons/src/main/java/org/ost/platform/user/dto/SettingsSnapshotDto.java`

---

### Step 2 — audit-starter: findTimeline + countTimeline in repository, service, port

**Status:** [ ] todo

**Files:**
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/repository/AuditLogRepository.java`
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/services/AuditReadService.java`
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/services/DefaultAuditPort.java`

---

### Step 3 — Settings: timelinePageSize field in UI and DTO

**Status:** [ ] todo

**Files:**
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/ui/dto/SettingsEditDto.java`
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/settings/SettingsFormModeHandler.java`
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/common/I18nKey.java` (SETTINGS_TIMELINE_PAGE_SIZE_LABEL)
- MODIFY `marketplace-app/src/main/resources/i18n/messages_en.properties`
- MODIFY `marketplace-app/src/main/resources/i18n/messages_uk.properties`

---

### Step 4 — marketplace-app: TimelineView + QueryBlock + MainView tab

**Status:** [ ] todo

**Files:**
- NEW `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/timeline/TimelineView.java`
- NEW `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/timeline/TimelineQueryBlock.java`
- NEW `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/timeline/TimelineQueryConfig.java`
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/MainView.java`
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/common/I18nKey.java` (MAIN_TAB_TIMELINE + filter keys)
- MODIFY `marketplace-app/src/main/resources/i18n/messages_en.properties`
- MODIFY `marketplace-app/src/main/resources/i18n/messages_uk.properties`

---

### Step 5 — Playwright tests + remove inline Timeline tabs from overlays

**Status:** [ ] todo

**Notes:** Remove `AuditTimelinePanel` usage from `UserViewOverlayModeHandler` and `SettingsFormModeHandler` only after this step's tests pass.
