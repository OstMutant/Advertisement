# `.claude/agents/`

Custom Claude Code subagent definitions — real, persisted `.claude/agents/*.md` files (YAML
frontmatter + markdown body as the subagent's own system prompt), each dispatched via the `Agent`
tool. Claude Code scans this directory recursively — a subagent's identity comes from its own
`name` frontmatter field, not its file path, so grouping into subfolders below is purely
organizational.

## Entry points

| Entry point | Real logic lives in |
|---|---|
| [`review/deep-review-orchestrator`](review/deep-review-orchestrator.md) | self-contained — see [`review/README.md`](review/README.md) for the full dispatch flow |
| [`sonar/sonar-analyst`](sonar/sonar-analyst.md) | self-contained — see [`sonar/README.md`](sonar/README.md) for the full dispatch flow |
