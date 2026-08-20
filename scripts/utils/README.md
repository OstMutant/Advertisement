# scripts/utils

Shared library scripts sourced by multiple script-groups — never entry points on their own, no
`## Flow` section (there's no entry-point chain to diagram). Each file's own header states what it
provides; each caller's own header states that it sources a file here.
