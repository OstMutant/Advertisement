# html-sanitizer-lib

A shared HTML sanitizer + visible-text-length validator. No Spring Boot autoconfiguration, no
Vaadin dependency, no domain knowledge.

---

## Package structure

```
org.ost.sanitizer
  HtmlSanitizer   — sanitize(html, maxVisibleTextLength): strips disallowed markup via OWASP's
                    Sanitizers.FORMATTING/LINKS/BLOCKS policy (plus <pre>), then rejects input
                    whose Jsoup-parsed visible text exceeds the caller-supplied max length
```

That's the entire module — one class, one package.

---

## Admission criterion

This module exists for logic that is genuinely duplicated across ≥2 starters *and* doesn't fit
`platform-commons` because of an external-dependency concern `platform-commons`'s own governance
rule would reject — not merely "avoids duplication" as a standalone reason. Do not add a class
here on a "might be useful later" basis; check every candidate against both conditions before
adding it.
