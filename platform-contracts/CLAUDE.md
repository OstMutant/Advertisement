## platform-contracts: Package Semantics

Three sub-packages inside each subsystem namespace carry distinct roles:

- `*.api` — what **marketplace contributes to the starter**: marker interfaces (`AuditableSnapshot`) and annotations (`@AuditedField`, `@ConditionalOnAuditEnabled`) that marketplace places on its own classes so the starter can read them. Only `audit.*` has an `api` package; attachment has no equivalent because it needs no marker contracts from marketplace.
- `*.spi` — **extension points between modules**: interfaces declaring a callback boundary. Who calls vs. who implements varies by suffix (see table below).
- `*.dto` — **data carriers** crossing the module boundary: plain value objects with no behavior, named with the `Dto` suffix.

**Rule:** do not add behavior to `*.dto` classes; do not add Spring annotations to `*.api` markers; do not put data records in `*.spi`.

## SPI Interface Naming

All cross-module extension points live in `platform-contracts/*.spi`. The suffix encodes the call direction and semantic role:

| Suffix | Caller → Implementor | Semantic role | Examples |
|--------|---------------------|---------------|---------|
| `*Port` | marketplace → starter | Service facade: marketplace issues commands/queries to the starter | `AuditPort`, `AttachmentPort` |
| `*Extension` | caller → data/UI owner | One module requests domain data or a UI component from the module that owns it; direction varies (marketplace→starter or starter→starter) | `AuditUiExtension`, `AttachmentGalleryExtension`, `ActivityFeedExtension`, `MediaHistoryExtension` |
| `*Consumer` | starter → marketplace | Starter notifies marketplace of an infrastructure event | `MediaChangeConsumer` |
| `*Provider` | starter → marketplace | Starter pulls current-request context or domain logic from marketplace | `CurrentActorProvider`, `ActivityItemFieldsProvider` |
| `*Resolver` | starter → marketplace | Starter asks marketplace to resolve a human-readable name | `AuditActorNameResolver`, `EntityDisplayNameResolver` |
| `*Checker` | starter → marketplace | Starter asks marketplace to verify domain state | `AuditEntityExistenceChecker` |
| `*Binding` | starter → marketplace | Starter asks marketplace to contribute a UI row renderer into the starter's own feed | `ActivityRowBinding` |

**Rule:** do not introduce new suffixes without updating this table and adding a `platform-contracts/DECISIONS.md` entry. Existing suffixes must not be repurposed for a different direction or role.
