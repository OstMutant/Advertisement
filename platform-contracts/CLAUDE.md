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
