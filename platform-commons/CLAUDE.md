## platform-commons: Governance

### What belongs here

ALLOWED:
- Stable platform abstractions (SPI interfaces, Port/Hook interfaces)
- Shared value objects and cross-module DTOs
- Domain events and marker annotations
- Core config records (`CleanupProperties`)
- Utility classes used by ≥2 modules (e.g. `YoutubeUtil` in `attachment.util`)

NOT ALLOWED:
- Business logic of any kind
- Feature helpers or generic utils (`DateUtils`, `StringUtils`, `JsonUtils`, etc.)
- Spring `@Service` or `@Component` beans
- Random abstractions without ≥2 cross-module consumers
- Feature-specific models or persistence logic
- Anything that belongs to a single module

**Rule:** if only one module needs it — it lives in that module, not here.

---

## Package Semantics

Sub-packages inside each subsystem namespace carry distinct roles:

- `*.api` — what **marketplace contributes to the starter**: marker interfaces (`AuditableSnapshot`) that marketplace places on its own classes so the starter can read them. Only `audit.*` has an `api` package; other subsystems need no marker contracts from marketplace.
- `*.spi` — **extension points between modules** with **no Vaadin dependency**: interfaces declaring a callback boundary for domain data, events, and commands. Who calls vs. who implements varies by suffix (see table below).
- `*.dto` — **data carriers** crossing the module boundary: plain value objects with no behavior, named with the `Dto` suffix.

**Rule:** do not add behavior to `*.dto` classes; do not add Spring annotations to `*.api` markers; do not put data records in `*.spi`. Non-UI consumers can depend on `*.spi` and `*.dto` without pulling Vaadin onto their classpath.

**Narrow exception:** a pure derivation over a `*.dto` record's own fields, with no external
dependencies (e.g. `AuditTimelineItemDto.withChanges()`/`.expandedChanges()`), is allowed — it's
not business logic, just a convenience view of data the record already carries. Do not stretch
this to anything that calls another service, branches on domain state beyond the record's own
fields, or produces a different DTO type. See `platform-commons/DECISIONS.md`.

## SPI Interface Naming

All cross-module extension points live in `platform-commons/*.spi`. The suffix encodes the call direction and semantic role:

| Suffix | Caller → Implementor | Semantic role | Examples |
|--------|----------------------------------|---------------|---------|
| `*Port` | marketplace → starter | marketplace calls the starter (commands, queries) | `AuditPort`, `AttachmentPort`, `AttachmentAuditPort`, `UserPort`, `UserAccountPort`, `UserAuthorizationPort`, `UserPreferencesPort`, `AdvertisementPort`, `TaxonPort` — a single starter may expose more than one `*Port` when grep against every real consumer shows its methods cluster into concerns different callers use independently (query vs. mutation vs. authorization vs. preferences, `user-spring-boot-starter`'s case). Method count alone is never sufficient reason to split — see `platform-commons/DECISIONS.md` ADR-026 for the evidence-first process this precedent requires before splitting any other `*Port`. |
| `*Hook` | starter → marketplace | starter calls back for domain data, events, or contributions | `CurrentActorHook`, `AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`, `UserSettingsChangedHook` (`AttachmentMediaChangeHook` does not exist; `AttachmentAuditPort`'s call direction is the `*Port` semantic, not `*Hook` — see `platform-commons/DECISIONS.md` ADR-025) |

**Rule:** do not introduce new suffixes without updating this table and adding a `platform-commons/DECISIONS.md` entry. Existing suffixes must not be repurposed for a different direction or role.

**Why ports and hooks must live in `platform-commons` and not in the starter:**
Starters are optional — marketplace compiles and runs without them on the classpath (all injections use `ObjectProvider`). If a port or hook interface lived inside a starter, removing that starter would break marketplace compilation even though the feature is optional. Keeping all interfaces in `platform-commons` ensures marketplace always has the type visible, regardless of which starters are present.

**Every `*.spi` interface must carry a Javadoc purpose paragraph directly above its declaration.**
This is the single source of truth for what the interface is for — `scripts/architecture/generate-architecture-model.sh`'s SPI Map reads it live (the Javadoc block immediately preceding `interface X`, first paragraph up to any `@`-tag) and shows it in the interactive diagram's detail popup. Do not also maintain a separate description of the same interface anywhere else (a generator-side lookup table, a wiki page, etc.) — if the purpose changes, edit the Javadoc, not a second copy.

```java
/**
 * Port: marketplace → audit-starter.
 * Write side: captures entity creation, update, deletion, and restore as immutable audit entries.
 * Read side: resolves snapshot content, per-entity activity, and the cross-entity timeline feed.
 * Implementation lives in audit-spring-boot-starter.
 */
public interface AuditPort {
```

Convention: first line states `Port: <caller> → <implementor>.` / `Hook: <caller> → <implementor>.` (matching the direction in the table above), followed by 1-3 sentences on what the interface actually does. `@FunctionalInterface`-annotated interfaces put the annotation between the Javadoc and the `interface` keyword — the extraction skips over it.

## Hook and Port Implementation Rules

**Naming:** `*Hook` implementations → `*HookImpl`; `*Port` implementations → `*PortImpl` or `Default*Port` (for primary implementations with non-trivial coordination logic).

**`*HookImpl` — pure delegation only.** No business logic, no JSON parsing, no conditionals beyond entity-type routing. Each method calls exactly one service method.

```java
// ✅ correct
@Override
public List<AuditActivityItemDto> merge(EntityType t, Long id, List<AuditActivityItemDto> items) {
    return myService.mergeMediaChanges(items);
}

// ❌ wrong — logic belongs in a service
@Override
public List<AuditActivityItemDto> merge(EntityType t, Long id, List<AuditActivityItemDto> items) {
    return items.stream().map(item -> { /* ... parsing, merging ... */ }).toList();
}
```

**`*PortImpl` — pure delegation only.** Same rule as `*HookImpl`: each method calls exactly one service method, no logic in the port itself. A port is not a facade that orchestrates — it is a thin adapter that exposes service methods through the platform-commons contract. All business logic belongs in the service.
