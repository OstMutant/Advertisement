---
paths: ["platform-commons/**"]
---

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

<!-- #arch-embed:spi-glossary -->
**What is SPI?** SPI (Service Provider Interface) is the general pattern this project uses for
every cross-module extension point: a plain interface owned by the layer that needs to call out,
implemented by whichever module can actually satisfy it, resolved at runtime instead of a
compile-time import — the same shape as `java.util.ServiceLoader` or Spring's own
auto-configuration provider interfaces. This project narrows the pattern to a `*Port`/`*Hook`
naming convention (see the table below) so the call direction is visible from the interface name
alone.
<!-- /#arch-embed -->

<!-- #arch-embed:port-glossary -->
**What is a Port?** A `*Port` interface is the SPI shape where marketplace (or
`marketplace-orchestrator`) calls into a domain starter — e.g. `AdvertisementPort`, `UserPort`.
The starter implements it; the caller depends only on the interface, never on the starter's own
classes.
<!-- /#arch-embed -->

<!-- #arch-embed:hook-glossary -->
**What is a Hook?** A `*Hook` interface is the reverse SPI shape: a starter (or
`marketplace-orchestrator`, for the two forwarder Hooks) calls back into the layer above it for
something only that layer can supply — domain data, translations, session state, e.g.
`AuditDomainHook`, `CurrentActorHook`. The layer above implements it; the starter depends only on
the interface.
<!-- /#arch-embed -->

<!-- #arch-embed:why-port-hook-glossary -->
**Why use Port/Hook?** Every domain starter (advertisement, user, taxon, ...) is optional —
marketplace compiles and boots without it, wiring it in only via `ObjectProvider`. Port/Hook is
the mechanism that makes that possible: the caller depends only on a `platform-commons` interface,
never on a starter's own classes, so adding, removing, or swapping a starter never breaks
compilation or forces a change in the layer that calls it.
<!-- /#arch-embed -->

## Package Semantics

Sub-packages inside each subsystem namespace carry distinct roles:

- `*.api` — what **marketplace contributes to the starter**: marker interfaces (`AuditableSnapshot`) that marketplace places on its own classes so the starter can read them. Only `audit.*` has an `api` package; other subsystems need no marker contracts from marketplace.
- `*.spi` — **extension points between modules** with **no Vaadin dependency**: interfaces declaring a callback boundary for domain data, events, and commands. Who calls vs. who implements varies by suffix (see table below).
- `*.dto` — **data carriers** crossing the module boundary: plain value objects with no behavior, named with the `Dto` suffix.

**Rule:** do not add behavior to `*.dto` classes; do not add Spring annotations to `*.api` markers; do not put data records in `*.spi`. Non-UI consumers can depend on `*.spi` and `*.dto` without pulling Vaadin onto their classpath.

**Narrow exception:** a pure derivation over a value type's own fields, with no external
dependencies, is allowed — it's not business logic, just a convenience view of data the type
already carries. Applies uniformly across `*.dto`, `*.api` markers, and `core.model` value types,
not just `*.dto` literally (e.g. `AuditTimelineItemDto.withChanges()`/`.expandedChanges()` in
`*.dto`; `ChangeEntry.replaceIfField()`/`.mapField()` in `core.model`). Do not stretch this to
anything that calls another service, branches on domain state beyond the type's own fields, or
produces a different DTO type. See `platform-commons/DECISIONS.md`.

## SPI Interface Naming

All cross-module extension points live in `platform-commons/*.spi`. The suffix encodes the call direction and semantic role:

| Suffix | Caller → Implementor | Semantic role | Examples |
|--------|----------------------------------|---------------|---------|
| `*Port` | marketplace → starter | marketplace calls the starter (commands, queries) | `AuditPort`, `AttachmentPort`, `AttachmentAuditPort`, `UserPort`, `UserAccountPort`, `UserAuthorizationPort`, `UserPreferencesPort`, `AdvertisementPort`, `TaxonPort` — a single starter may expose more than one `*Port` when grep against every real consumer shows its methods cluster into concerns different callers use independently (query vs. mutation vs. authorization vs. preferences, `user-spring-boot-starter`'s case). Method count alone is never sufficient reason to split — see `.claude/nav/adr-index.md` for the evidence-first process this precedent requires before splitting any other `*Port`. |
| `*Hook` | starter → marketplace-app or marketplace-orchestrator | starter calls back for domain data, events, or contributions | `CurrentActorHook`, `AuditDomainHook`, `AuditActivityEnrichHook`, `UserSettingsChangedHook` (`AttachmentMediaChangeHook` does not exist; `AttachmentAuditPort`'s call direction is the `*Port` semantic, not `*Hook` — see `.claude/nav/adr-index.md`; `AuditActivityFieldsHook` does not exist either — its only real caller was already `marketplace-app`'s own `AuditTimelineRowRenderer`, and every implementation had converged to a one-line delegation with zero domain-specific logic, so the whole per-domain Hook pattern was removed in favor of one field-name-to-label mapping directly in `AuditTimelineRowRenderer`, see `.claude/nav/adr-index.md` for that refinement). Implementor is `marketplace-orchestrator` for `CurrentActorHook`/`AuditDomainHook` since the Hook-relocation work (`org.ost.orchestrator.spi` — see `marketplace-orchestrator/CLAUDE.md`); `AuditActivityEnrichHook` stayed implemented in `marketplace-app`. `UiLabelHook`/`SessionActorHook` — the two forwarder SPIs `marketplace-orchestrator`'s relocated Hook implementations use to reach a UI-shell resource (translations, HTTP session) — do **not** live in `platform-commons`: they're called only by `marketplace-orchestrator` itself (never a starter), and `marketplace-orchestrator` is a mandatory, never-optional dependency of `marketplace-app`, so the starter-optionality reasoning below doesn't apply to this pair — they live in `org.ost.orchestrator.spi` instead, see `.claude/nav/adr-index.md`. |

**Rule:** do not introduce new suffixes without updating this table and adding a `platform-commons/DECISIONS.md` entry. Existing suffixes must not be repurposed for a different direction or role.

**Why ports and hooks must live in `platform-commons` and not in the starter:**
Starters are optional — marketplace compiles and runs without them on the classpath (all injections use `ObjectProvider`). If a port or hook interface lived inside a starter, removing that starter would break marketplace compilation even though the feature is optional. Keeping all interfaces in `platform-commons` ensures marketplace always has the type visible, regardless of which starters are present.

**Concrete signs a starter has absorbed domain-specific logic it shouldn't carry:** a hardcoded
field name belonging to another domain (e.g. `"title"`, `"email"`) inside the starter's own SQL or
rendering code; a branch on a specific `EntityType` value inside starter logic instead of a generic
`(EntityType, Long)`-shaped SPI call; a method named after another domain's concept (e.g.
`buildAdvertisementFieldsList` inside `audit-spring-boot-starter`); SQL reading a domain-specific
JSON field by name. Any of these belongs behind an SPI interface here in `platform-commons`,
implemented in `marketplace-app`/`marketplace-orchestrator` — never hardcoded inside the starter
itself.

**Every `*.spi` interface must carry a Javadoc purpose paragraph directly above its declaration.**
This is the single source of truth for what the interface is for — `docs/architecture/scripts/generate-architecture-model.sh`'s SPI Map reads it live (the Javadoc block immediately preceding `interface X`, first paragraph up to any `@`-tag) and shows it in the interactive diagram's detail popup. Do not also maintain a separate description of the same interface anywhere else (a generator-side lookup table, a wiki page, etc.) — if the purpose changes, edit the Javadoc, not a second copy.

```java
/**
 * Write side: captures entity creation, update, deletion, and restore as immutable audit entries.
 * Read side: resolves snapshot content, per-entity activity, and the cross-entity timeline feed.
 * Implementation lives in audit-spring-boot-starter.
 */
public interface AuditPort {
```

Convention: 1-3 sentences on what the interface actually does. No `Port: <caller> → <implementor>.`/`Hook: <caller> → <implementor>.` opening line — the direction is already conveyed by the `*Port`/`*Hook` suffix itself and the table above; restating it in every interface's own Javadoc added no information a reader didn't already have. `@FunctionalInterface`-annotated interfaces put the annotation between the Javadoc and the `interface` keyword — the extraction skips over it.

## Hook and Port Implementation Rules

<!-- #arch-embed:spi-implementation-rules -->
**Port Implementation (`*PortImpl`, `Default*Port`):** same module as the port interface. Example: `org.ost.audit.services.DefaultAuditPort` delegates all methods to `AuditLogRepository` and `AuditReadService`.

**Hook Implementation (`*HookImpl`):** service module that implements the hook — `marketplace-orchestrator/spi` for Hooks needing only domain-port access, `marketplace-app/spi` for the two forwarder Hooks needing a UI-shell resource (translations, HTTP session). Example: `org.ost.orchestrator.spi.CurrentActorHookImpl` calls `SessionActorHook.getCurrentActorId()`, implemented by `org.ost.marketplace.spi.SessionActorHookImpl` against `AuthContextService`.
<!-- /#arch-embed -->

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
