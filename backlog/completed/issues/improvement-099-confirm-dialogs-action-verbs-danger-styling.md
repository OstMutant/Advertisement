# improvement-099: Confirm dialogs use generic "Yes/Cancel" instead of action verbs; destructive confirms aren't danger-styled

**Type:** improvement — UX (error prevention / recognition over recall). Found via UX review over
the 2026-07-19 e2e screenshot set.
**Module:** `marketplace-app` (confirm dialog usages: logout, advertisement delete, user delete,
discard-changes)
**Priority:** medium (UX) — small, localized change; biggest payoff on the two destructive
confirms
**When:** Batch L (UX quick pass) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

Verified from screenshots: the logout confirm renders "Ви справді хочете вийти?" with buttons
**"Так" / "Скасувати"** ("Yes"/"Cancel"), the primary "Так" in standard blue. Best practice
(Nielsen: recognition over recall; platform HIGs) is to label the confirming button with the
action verb — the user shouldn't have to re-read the question to know what "Yes" does. The same
generic pattern presumably applies to the delete confirms (advertisement card delete, user row
delete) — where a second issue compounds it: a **destructive** primary action styled like any
other primary button instead of Lumo's error/danger variant.

## Suggested fix

- Logout: "Вийти" / "Log Out" as the confirm label (keys already exist for the header button —
  reuse), "Скасувати"/"Cancel" stays.
- Advertisement/user delete: confirm label "Видалити"/"Delete", styled
  `ButtonVariant.LUMO_ERROR` (+`LUMO_PRIMARY`), cancel unchanged. Subject in the dialog body
  ("Delete 'UK Оголошення'?") rather than a generic question, so the dialog is self-describing.
- Discard-changes confirm (overlay close with unsaved edits): "Discard changes" as the verb.
- Sweep: one shared confirm-dialog helper likely already centralizes this (check
  `EntityOverlaySupport` / dialog builders) — fix at the helper level, not per call site, and
  add the verb + variant as parameters.

## Related

- `backlog/issues/improvement-089-userservice-hard-delete-no-audit-trail.md` — the user-delete
  confirm this issue restyles sits in front of that hard delete; coordinate copy if 089 changes
  delete semantics to soft-delete.
