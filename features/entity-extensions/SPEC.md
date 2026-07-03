# Feature: Entity Extensions — Generic JSONB Metadata Column

## Goal

Replace module-specific denormalized columns on entity tables (e.g. `advertisement.media_url`,
`advertisement.media_content_type`) with a single generic `extensions JSONB` column that any
optional starter can write its own data into — without adding new columns or creating cross-module
schema dependencies.

---

## Problem

Currently `advertisement` table has `media_url TEXT` and `media_content_type TEXT` — data that
belongs to the attachment module but lives in the advertisement table. This is implicit coupling:
the advertisement schema "knows" about attachments.

Every new optional module that wants to store auxiliary data per entity must either:
- Add a new column to the entity table (schema coupling), or
- Maintain its own join table (extra complexity)

---

## Proposed Solution

Add `extensions JSONB` column to entity tables (`advertisement`, `app_user`, `taxon`, etc.).

Each optional module writes its own namespace into the JSON:

```json
{
  "media": {
    "url": "https://...",
    "content_type": "image/jpeg",
    "primary_attachment_id": 42
  }
}
```

Modules read/write only their own namespace key. No module knows about another's keys.

IDs stored inside the JSON (e.g. `primary_attachment_id`) serve as soft references for future
synchronization — no FK constraint, no cross-module schema dependency, but enough context to
resolve the related record when needed.

---

## Benefits

- No cross-module FK or column coupling
- Extensible without schema migrations per new module
- PostgreSQL JSONB supports indexing on specific keys if needed

---

## Concerns to resolve before implementation

- Who owns the `extensions` column updates — each module writes independently or via a hook?
- Concurrent writes from multiple modules to the same JSONB column (merge vs overwrite)
- Type safety — no compile-time guarantees on JSON structure
- Query performance — JSONB vs dedicated columns for frequently filtered fields

---

## Scope

Deferred — implement incrementally starting with `advertisement.media_url` / `media_content_type`
as the first candidate once the concerns above are resolved.
