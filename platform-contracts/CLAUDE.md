## platform-contracts: Package Semantics

Three sub-packages inside each subsystem namespace carry distinct roles:

- `*.api` — what **marketplace contributes to the starter**: marker interfaces (`AuditableSnapshot`) and annotations (`@AuditedField`, `@ConditionalOnAuditEnabled`) that marketplace places on its own classes so the starter can read them. Only `audit.*` has an `api` package; attachment has no equivalent because it needs no marker contracts from marketplace.
- `*.spi` — **extension points between modules**: interfaces declaring a callback boundary. Who calls vs. who implements varies by suffix (see table below).
- `*.dto` — **data carriers** crossing the module boundary: plain value objects with no behavior, named with the `Dto` suffix.

**Rule:** do not add behavior to `*.dto` classes; do not add Spring annotations to `*.api` markers; do not put data records in `*.spi`.

## SPI Interface Naming

All cross-module extension points live in `platform-contracts/*.spi`. The suffix encodes the call direction and semantic role:

| Suffix | Caller → Implementor | Semantic role | Examples |
|--------|----------------------------------|---------------|---------|
| `*Port` | marketplace → starter | marketplace calls the starter (commands, queries, UI components) | `AuditPort`, `AttachmentPort`, `AuditUiPort`, `AttachmentGalleryPort` |
| `*Hook` | starter → marketplace or starter → starter | starter calls back for domain data, events, or UI contributions | `CurrentActorHook`, `MediaChangeHook`, `AuditDomainHook`, `EntityNameHook`, `ActivityFieldsHook`, `ActivityRowHook`, `AttachmentAuditHook` |

**Rule:** do not introduce new suffixes without updating this table and adding a `platform-contracts/DECISIONS.md` entry. Existing suffixes must not be repurposed for a different direction or role.

## Hook and Port Implementation Rules

**Naming:** `*Hook` implementations → `*HookImpl`; `*Port` implementations → `*PortImpl` or `Default*Port` (for primary implementations with non-trivial coordination logic).

**`*HookImpl` — pure delegation only.** No business logic, no JSON parsing, no conditionals beyond entity-type routing. Each method calls exactly one service method.

```java
// ✅ correct
@Override
public List<ActivityItemDto> merge(EntityType t, Long id, List<ActivityItemDto> items) {
    return myService.mergeMediaChanges(items);
}

// ❌ wrong — logic belongs in a service
@Override
public List<ActivityItemDto> merge(EntityType t, Long id, List<ActivityItemDto> items) {
    return items.stream().map(item -> { /* ... parsing, merging ... */ }).toList();
}
```

**`Default*Port` / `*PortImpl` — may coordinate.** A port is a facade over the starter's internal service layer. Orchestrating multiple service calls, resolving fallbacks, or managing transactions within the port is acceptable — it is the port's role to present a clean interface to the outside world. Logic that belongs in a domain service (business rules, data transformation) must not leak into the port.
