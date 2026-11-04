# E2E Test Suite

End-to-end tests that simulate real user scenarios against a live app + database.
Every test creates the data it needs, with unique names, and checks only its own data — any test runs alone, in any order and in parallel.

---

## Test naming pattern

```
{actor} {action} {subject} — {verification1}, {verification2}, {verification3}
```

Example: `moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check`

- After the dash: list each major verified behaviour explicitly
- Use concrete words: "discard", "save with activity diff", "timeline check", "restore", "pagination"
- Avoid vague labels like "badge check" or "flow" as the only descriptor
- Version numbers → plain words ("two saves", not "v5/v6")

---

Shared step sequences live in `_flows/` — see its own `README.md`. Each spec file's own header
states what it covers — also browsable via `architecture-map.html`'s Tooling & Pipelines card.
