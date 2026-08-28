Run a full code review via the `deep-review-orchestrator` agent -- evidence-verified SOLID/DRY/
KISS/YAGNI findings, every one independently re-verified against the real file before being
reported. Never writes code itself.

Usage: /review [scope]
Examples:
  /review                          # uncommitted working-tree changes (default)
  /review HEAD~3                   # one specific commit
  /review module platform-commons  # every file in one module's src/main
  /review all                      # whole repo, one module at a time in parallel

Steps:
1. Dispatch: `Agent({description: "Code review", subagent_type: "deep-review-orchestrator",
   prompt: "$ARGUMENTS"})` -- if `$ARGUMENTS` is empty, the orchestrator's own step 1 already
   defaults to uncommitted changes.
2. Read the orchestrator's final result in full -- it is not this command's job to re-verify
   findings, only to act on what the orchestrator already verified.
3. If the result includes a `ReportFindings` JSON payload block: call `ReportFindings` with it.
4. If the result includes any prepared-but-unwritten `backlog/issues/*.md` file content: present
   it to the user and, only after explicit approval, write it via the `Write` tool -- never write
   it automatically, per the standing Approval Rule.
