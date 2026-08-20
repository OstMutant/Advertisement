# playwright/e2e/_flows

The `e2e` spec suite's own shared step sequences — no entry-point chain of its own, purely
organizational. Extract a helper here only when 2+ spec files need it — spec-only helpers stay
local to the spec file. Each file's own header states what it provides and which spec files
require it.
