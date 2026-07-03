# Feature: Rich Text Description for Advertisements

## Goal

Replace the plain-text `description` field in Advertisement create/edit form with a
Vaadin `RichTextEditor` (Quill.js-based). Store HTML in the existing `TEXT` column.
Render formatted HTML in the view overlay and card.

---

## Scope

| Area | Change |
|---|---|
| Create/Edit form | `TextArea` → `RichTextEditor` |
| View overlay (description field) | render stored HTML (not plain text) |
| Advertisement card / grid | strip tags → plain excerpt (no HTML in grid cell) |
| DB schema | none — `description TEXT` already fits |

## Out of scope

- Custom Quill toolbar configuration (use Vaadin default toolbar)
- Image embedding inside description (attachments handled separately)
- Per-user formatting preferences

---

## Technical Notes

### Component
`com.vaadin.flow.component.richtexteditor.RichTextEditor` (Vaadin 25, included in platform).

### Storage
Store raw HTML string in `advertisement.description`. No schema migration needed.

### Rendering saved HTML
```java
Div descriptionDiv = new Div();
descriptionDiv.getElement().setProperty("innerHTML", sanitize(description));
```

### XSS sanitization (required)
Before saving: sanitize with OWASP Java HTML Sanitizer (`policyFactory.sanitize(html)`).
Allowlist: `<b>`, `<i>`, `<u>`, `<s>`, `<em>`, `<strong>`, `<ul>`, `<ol>`, `<li>`,
`<p>`, `<br>`, `<h1>`–`<h3>`, `<a href>` (http/https only), `<blockquote>`, `<pre>`, `<code>`.

Dependency to add in `advertisement-spring-boot-starter/pom.xml`:
```xml
<dependency>
  <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
  <artifactId>owasp-java-html-sanitizer</artifactId>
  <version>20240325.1</version>
</dependency>
```

### Grid/card excerpt
Strip HTML tags before displaying in grid column or card preview:
```java
Jsoup.clean(html, Safelist.none())  // plain text, no tags
```
Or a simple regex for stripped preview if Jsoup already on classpath.

---

## Open Questions

- Should the sanitizer live in `AdvertisementService` (save-time) or in the UI layer?
  → Recommendation: service layer — guarantees clean data regardless of caller.
- Existing plain-text descriptions after migration: display as-is (already safe, no tags).
- Max length: keep current DB constraint or raise it?

---

## Known Problems

### Problem 1 — isCurrentState badge missing on v1 after restore

Test `userEn restores advertisement` fails: expects 3 restore buttons, receives 4.
After restoring to v7, version v1 (CREATED) should show "Current state" badge because the
restored state matches the original — but it doesn't.

Root cause: `getUrlsAtVersion(1)` returns empty result.
`AttachmentSnapshotRepository` uses `attachment_snapshot.created_at < audit_log[rn=2].created_at`
as boundary. Inside a single PostgreSQL transaction `NOW()` returns the same timestamp for all
INSERTs — so the condition `T == T` evaluates to false and no snapshot is found.

### Problem 2 — cross-module SQL coupling between attachment and audit

`AttachmentSnapshotRepository` (attachment-spring-boot-starter) contains SQL queries that
read directly from the `audit_log` table (owned by audit-spring-boot-starter).

This violates module independence: removing audit-spring-boot-starter would break
attachment-spring-boot-starter at the SQL level even though they are supposed to be
independent optional starters.
