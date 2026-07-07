# Sequence Diagrams

## Real Code Paths

These diagrams trace actual class-to-class interactions based on source code inspection.

## 1. Advertisement Creation Flow

**Classes involved:**
- `org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.AdvertisementOverlay` (UI overlay)
- `org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler` (form handler)
- `org.ost.platform.advertisement.spi.AdvertisementPort` (interface in platform-commons)
- `org.ost.advertisement.spi.AdvertisementPortImpl` (implementation in starter)
- `org.ost.advertisement.services.AdvertisementService` (business logic)
- `org.ost.audit.services.DefaultAuditPort` (audit capture)
- `org.ost.marketplace.spi.AuditDomainHookImpl` (marketplace callback)

```mermaid
sequenceDiagram
    participant UI as AdvertisementOverlay
    participant Handler as AdvertisementFormOverlayModeHandler
    participant AdvPort as AdvertisementPort
    participant AdvImpl as AdvertisementPortImpl
    participant AdvService as AdvertisementService
    participant AuditPort as DefaultAuditPort
    participant AuditHook as AuditDomainHookImpl
    
    UI->>Handler: configure(onSave callback)
    Handler->>Handler: init form UI
    
    UI->>Handler: submit form click
    Handler->>Handler: getFormValue() → AdvertisementSaveDto
    Handler->>AdvPort: save(dto, actingUserId)
    
    AdvPort->>AdvImpl: (delegation)
    AdvImpl->>AdvService: save(dto, actingUserId)
    
    AdvService->>AdvService: validation
    AdvService->>AdvService: create Advertisement entity
    AdvService->>AdvService: repository.save(entity)
    
    Note over AdvService: Advertisement auto-triggers<br/>@CreatedDate audit events
    
    AdvService->>AuditPort: captureCreation(id, snapshot, actorId)
    AuditPort->>AuditPort: compute snapshot JSON
    AuditPort->>AuditPort: insert INTO audit_log
    
    AuditPort->>AuditHook: on(ActionType.CREATE, ADVERTISEMENT, id)
    AuditHook->>AuditHook: enrichAdditionalInfo()
    
    AdvService-->>AdvImpl: Long id
    AdvImpl-->>AdvPort: Long id
    AdvPort-->>Handler: Long id
    
    Handler->>Handler: afterSave(true)
    Handler->>Handler: updateSession(fresh entity)
    Handler-->>UI: notify complete
```

---

## 2. Advertisement Update with Media Change

**Classes involved:**
- `org.ost.advertisement.services.AdvertisementService`
- `org.ost.attachment.spi.DefaultAttachmentPort` (attachment starter)
- `org.ost.advertisement.spi.MediaChangeHookImpl` (advertisement listens to attachment changes)
- `org.ost.audit.services.DefaultAuditPort`
- `org.ost.attachment.services.AttachmentService`

```mermaid
sequenceDiagram
    participant App as marketplace-app UI
    participant AttPort as AttachmentPort
    participant AttImpl as DefaultAttachmentPort
    participant AttService as AttachmentService
    participant MediaHook as MediaChangeHookImpl
    participant AdvService as AdvertisementService
    participant AuditPort as DefaultAuditPort
    
    App->>AttPort: upload(file, ADVERTISEMENT, advId)
    
    AttPort->>AttImpl: (delegation)
    AttImpl->>AttService: save(file, entityType, entityId)
    
    AttService->>AttService: StorageService.upload() → S3
    AttService->>AttService: INSERT INTO attachment
    
    Note over AttService: Trigger media change hook
    
    AttService->>MediaHook: onChange(ADVERTISEMENT, advId)
    MediaHook->>AdvService: updateMediaMetadata(advId)
    
    AdvService->>AdvService: SELECT advertisement WHERE id=advId
    AdvService->>AdvService: UPDATE advertisement SET media_url, media_count
    
    Note over AdvService: Capture advertisement change in audit
    
    AdvService->>AuditPort: captureUpdate(advId, before, after, actorId)
    AuditPort->>AuditPort: INSERT INTO audit_log (action=UPDATE)
    
    AttService->>AttService: INSERT INTO attachment_snapshot
    
    AttService-->>AttImpl: success
    AttImpl-->>AttPort: success
    AttPort-->>App: success
```

---

## 3. Activity Feed Timeline Query

**Classes involved:**
- `org.ost.marketplace.ui.views.main.tabs.timeline.TimelineView` (UI view)
- `org.ost.audit.services.DefaultAuditPort` (audit read side)
- `org.ost.audit.repository.AuditLogRepository` (queries audit_log table)
- `org.ost.marketplace.spi.AdvertisementActivityFieldsHookImpl` (field enrichment)
- `org.ost.marketplace.spi.ActivityEnrichHookImpl` (cross-cutting activity merge)

```mermaid
sequenceDiagram
    participant UI as TimelineView
    participant AuditPort as DefaultAuditPort
    participant AuditRepo as AuditLogRepository
    participant AdvFields as AdvertisementActivityFieldsHookImpl
    participant Enrich as ActivityEnrichHookImpl
    
    UI->>UI: init() → refresh()
    UI->>UI: build filter, sort, pagination
    
    UI->>AuditPort: getTimelinePage(filter, sort, page, size)
    
    AuditPort->>AuditRepo: queryTimeline(filter, sort, offset, limit)
    AuditRepo->>AuditRepo: SELECT * FROM audit_log ORDER BY created_at DESC LIMIT/OFFSET
    
    Note over AuditRepo: Result: List<AuditLogProjection>
    
    AuditPort->>AuditPort: for each log entry, build AuditTimelineItemDto
    
    loop For each entity type
        AuditPort->>AdvFields: fields(EntityType.ADVERTISEMENT)
        AdvFields-->>AuditPort: Map<String, String> field labels
    end
    
    Note over AuditPort: Enrich with user names<br/>and field-level diffs
    
    AuditPort->>Enrich: merge(timelineItems)
    Enrich->>Enrich: group related activities by entity
    Enrich->>Enrich: merge media changes with advertisement changes
    Enrich-->>AuditPort: List<AuditTimelineItemDto>
    
    AuditPort-->>UI: List<AuditTimelineItemDto>
    
    UI->>UI: render grid/cards with activity
```

---

## 4. Restore from Snapshot

**Classes involved:**
- UI view initiates restore (e.g., `AdvertisementViewOverlayModeHandler`)
- `org.ost.audit.services.DefaultAuditPort` (retrieve snapshot + capture restore event)
- `org.ost.audit.services.AuditReadService` (load snapshot data)
- `org.ost.audit.repository.AuditLogRepository` (queries audit_log table)
- `org.ost.advertisement.services.AdvertisementService` (applies entity restore)

```mermaid
sequenceDiagram
    participant UI as UI
    participant AdvPort as AdvertisementPort
    participant AdvService as AdvertisementService
    participant AuditPort as DefaultAuditPort
    participant AuditRead as AuditReadService
    participant AuditRepo as AuditLogRepository
    
    UI->>UI: user clicks "restore to this snapshot"
    UI->>UI: gets snapshotId from activity feed
    
    UI->>AuditPort: getSnapshotContent(snapshotId, ADVERTISEMENT)
    
    AuditPort->>AuditRead: getSnapshotContent(snapshotId, entityType)
    AuditRead->>AuditRepo: SELECT * FROM audit_log WHERE id=snapshotId
    
    AuditRepo-->>AuditRead: AuditLogProjection
    AuditRead-->>AuditPort: AuditSnapshotContentDto
    AuditPort-->>UI: snapshot (advertisement fields)
    
    Note over UI: Apply restored fields to entity
    
    UI->>AdvPort: save(restoreDto, actorId)
    AdvPort->>AdvService: save(dto, actorId)
    AdvService->>AdvService: UPDATE advertisement SET title=..., description=...
    
    Note over AdvService: Capture RESTORED action (ActionType.RESTORED)
    
    AdvService->>AuditPort: captureRestore(entityId, snapshot, actorId)
    AuditPort->>AuditRepo: INSERT INTO audit_log (action_type='RESTORED', snapshot_data={...})
    
    AdvService-->>AdvPort: entityId
    AdvPort-->>UI: success
    
    UI->>UI: reload entity, close restore dialog
```

---

## 5. User Settings Change Propagation

**Classes involved:**
- `org.ost.marketplace.ui.views.main.header.settings.SettingsFormModeHandler` (UI form for settings)
- `org.ost.platform.user.spi.UserPort` (interface)
- `org.ost.user.spi.UserPortImpl` (implementation)
- `org.ost.user.services.UserSettingsService` (business logic)
- `org.ost.platform.user.spi.UserSettingsChangedHook` (callback interface)

```mermaid
sequenceDiagram
    participant UI as SettingsFormModeHandler
    participant UserPort as UserPort
    participant UserImpl as UserPortImpl
    participant UserService as UserSettingsService
    participant Hook as UserSettingsChangedHook
    
    UI->>UI: user changes locale or page size
    UI->>UI: clicks Save
    
    UI->>UserPort: updateSettings(userId, newSettings)
    
    UserPort->>UserImpl: (delegation)
    UserImpl->>UserService: updateSettings(userId, settings)
    
    UserService->>UserService: UPDATE user_information SET settings=jsonb_set(...) WHERE id=userId
    
    Note over UserService: Fire hook: notify starters<br/>that settings changed
    
    UserService->>Hook: onChanged(userId, changedSettings)
    
    Note over Hook: Currently no implementations<br/>but infrastructure ready for future listeners
    
    Hook-->>UserService: (void)
    
    UserService-->>UserImpl: success
    UserImpl-->>UserPort: success
    UserPort-->>UI: success
    
    UI->>UI: refresh locale provider, paginator defaults
    UI->>UI: notify success
```

---

## 6. Filter and Sort Advertisement List

**Classes involved:**
- `org.ost.marketplace.ui.views.main.tabs.advertisements.AdvertisementsView` (grid view)
- `org.ost.marketplace.ui.views.main.tabs.advertisements.query.AdvertisementQueryBlock` (filter/sort UI)
- `org.ost.platform.advertisement.spi.AdvertisementPort` (interface)
- `org.ost.advertisement.spi.AdvertisementPortImpl` (implementation)
- `org.ost.advertisement.repository.AdvertisementRepository` (custom SQL queries using query-lib)

```mermaid
sequenceDiagram
    participant UI as AdvertisementsView
    participant QueryBlock as AdvertisementQueryBlock
    participant AdvPort as AdvertisementPort
    participant AdvImpl as AdvertisementPortImpl
    participant AdvService as AdvertisementService
    participant AdvRepo as AdvertisementRepository
    participant QueryLib as SqlFilterBuilder
    
    UI->>QueryBlock: submit filter & sort
    QueryBlock->>QueryBlock: buildFilterDto(), buildSort()
    
    QueryBlock->>AdvPort: getFiltered(filterDto, page, size, sort, locale)
    
    AdvPort->>AdvImpl: (delegation)
    AdvImpl->>AdvService: getFiltered(filterDto, page, size, sort)
    AdvService->>AdvRepo: findByFilter(filterDto, pageable, sort)
    
    AdvRepo->>QueryLib: FILTER.bind(filterDto)
    QueryLib->>QueryLib: for each filter field, build WHERE condition
    QueryLib-->>AdvRepo: Map<String, Object> params, String whereClause
    
    AdvRepo->>AdvRepo: build SQL query with WHERE + ORDER BY + LIMIT/OFFSET
    AdvRepo->>AdvRepo: NamedParameterJdbcTemplate.query(sql, params, rowMapper)
    
    Note over AdvRepo: Execute SELECT * FROM advertisement<br/>WHERE (title ILIKE ? OR created_at >= ?)<br/>ORDER BY created_at DESC<br/>LIMIT ? OFFSET ?
    
    AdvRepo-->>AdvService: List<Advertisement>
    AdvService-->>AdvImpl: List<AdvertisementInfoDto>
    AdvImpl-->>AdvPort: List<AdvertisementInfoDto>
    AdvPort-->>QueryBlock: List<AdvertisementInfoDto>
    
    QueryBlock-->>UI: populate grid
    UI->>UI: render rows
```

---

---

## 7. Taxon Category Assignment to Advertisement

**Classes involved:**
- `org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler` (saves categories on form submit)
- `org.ost.platform.taxon.spi.TaxonPort` (interface)
- `org.ost.taxon.services.DefaultTaxonPort` (implementation)
- `org.ost.taxon.services.TaxonAssignmentService` (business logic)
- `org.ost.platform.taxon.spi.TaxonAuditHook` (callback to marketplace)
- `org.ost.marketplace.spi.TaxonAuditHookImpl` (records to audit log)

```mermaid
sequenceDiagram
    participant UI as AdvertisementFormOverlayModeHandler
    participant TaxonPort as TaxonPort
    participant TaxonImpl as DefaultTaxonPort
    participant AssignService as TaxonAssignmentService
    participant AuditHook as TaxonAuditHook
    participant HookImpl as TaxonAuditHookImpl
    participant TaxonActivity as TaxonActivityService

    UI->>UI: user selects categories in form
    UI->>TaxonPort: replaceAssignments(ADVERTISEMENT, advId, taxonIds)

    TaxonPort->>TaxonImpl: (delegation)
    TaxonImpl->>AssignService: replaceAssignments(ADVERTISEMENT, advId, taxonIds, actorId)

    AssignService->>AssignService: load existing assignments
    AssignService->>AssignService: compute added/removed diff

    loop For each removed taxon
        AssignService->>AssignService: DELETE FROM taxon_assignment
        AssignService->>AuditHook: onAssignmentChanged(ADVERTISEMENT, advId, taxonId, UNASSIGNED)
        AuditHook->>HookImpl: (delegation)
        HookImpl->>TaxonActivity: recordAssignmentChange(...)
        TaxonActivity->>TaxonActivity: write audit_log entry
    end

    loop For each added taxon
        AssignService->>AssignService: INSERT INTO taxon_assignment
        AssignService->>AuditHook: onAssignmentChanged(ADVERTISEMENT, advId, taxonId, ASSIGNED)
        AuditHook->>HookImpl: (delegation)
        HookImpl->>TaxonActivity: recordAssignmentChange(...)
        TaxonActivity->>TaxonActivity: write audit_log entry
    end

    AssignService-->>TaxonImpl: void
    TaxonImpl-->>TaxonPort: void
    TaxonPort-->>UI: void
```

---

## 8. Login and Registration Rate Limiting

**Classes involved:**
- `org.ost.marketplace.ui.views.main.header.dialogs.LoginDialog` / `SignUpDialog` (UI)
- `org.ost.marketplace.services.auth.AuthService` (login — marketplace-app, transport-aware: injects `HttpServletRequest`)
- `org.ost.platform.user.spi.UserPort` (interface) / `org.ost.user.spi.UserPortImpl` (implementation)
- `org.ost.user.services.UserService` (registration — user-spring-boot-starter, transport-agnostic: takes `clientIp` as `String`)

Both paths use an in-memory Caffeine cache (`expireAfterWrite(15 min)`, `maximumSize(10_000)`) keyed
per client, and only increment the failure counter on an actual failure — never on success. This
was a deliberate fix: an earlier version counted every attempt (including successful ones), which
locked out legitimate bulk usage (e.g. the Playwright e2e suite signing up dozens of accounts from
one IP within the 15-minute window).

```mermaid
sequenceDiagram
    participant UI as LoginDialog
    participant Auth as AuthService
    participant Cache as loginAttempts (Caffeine)
    participant AuthMgr as AuthenticationManager

    UI->>Auth: login(email, password)
    Auth->>Cache: get(remoteAddr + "|" + email)
    alt attempts >= MAX_LOGIN_ATTEMPTS (5)
        Auth-->>UI: throws IllegalStateException
        UI->>UI: show "too many login attempts" notification
    else under limit
        Auth->>AuthMgr: authenticate(email, password)
        alt BadCredentialsException
            Auth->>Cache: increment attempts
            Auth-->>UI: return false
            UI->>UI: show "invalid email or password" notification
        else success
            Auth->>Cache: invalidate(key)
            Auth-->>UI: return true
        end
    end
```

```mermaid
sequenceDiagram
    participant UI as SignUpDialog
    participant Port as UserPort
    participant Impl as UserPortImpl
    participant Service as UserService
    participant Cache as registerAttempts (Caffeine)
    participant Repo as UserRepository

    UI->>Port: register(dto, request.getRemoteAddr())
    Port->>Impl: (delegation)
    Impl->>Service: register(dto, clientIp)
    Service->>Cache: get(clientIp)
    alt failures >= MAX_REGISTER_ATTEMPTS (5)
        Service-->>Impl: throws IllegalStateException
        Impl-->>Port: (propagates)
        Port-->>UI: (propagates)
        UI->>UI: show "too many registration attempts" notification
    else under limit
        Service->>Repo: save(newUser)
        alt DuplicateKeyException (race with client-side email-uniqueness check)
            Service->>Cache: increment failures
            Service-->>UI: (propagates exception)
        else success
            Service-->>Impl: void
            Impl-->>Port: void
            Port-->>UI: void
            UI->>UI: show success notification
        end
    end
```

---

## Key Interaction Patterns

### Port Pattern (Marketplace → Starter)
All calls from marketplace-app to starters go through a `*Port` interface defined in platform-commons:
```
UI → AdvPort.method() → AdvPortImpl (thin delegate) → AdvService (business logic)
```

### Hook Pattern (Starter → Marketplace)
All callbacks from starters to marketplace-app go through a `*Hook` interface:
```
Service → HookInterface.method() → HookImpl (thin delegate) → custom marketplace logic
```

### No Direct Imports
Marketplace UI classes never import from starter internal classes:
- Good: `import org.ost.platform.advertisement.spi.AdvertisementPort`
- Bad: `import org.ost.advertisement.services.AdvertisementService`

### Delegation Pattern
All Port/Hook implementations are pure delegation with no business logic:
- Example: `AdvertisementPortImpl.save()` calls `AdvertisementService.save()` and returns result
- Example: `MediaChangeHookImpl.onChange()` calls `AdvertisementService.updateMediaMetadata()` and returns

