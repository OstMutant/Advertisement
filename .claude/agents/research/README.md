# `.claude/agents/research/`

Research subagents. [`deep-researcher`](deep-researcher.md) researches a task in depth on Opus (best practices
on the web, the project's own practice, a compared choice) and writes two plans, one for people and one
for AI executors, into a topic folder under `backlog/research/`. It is started by the
[`/deep-research`](../../commands/deep-research.md) command with only a topic name, reads its brief from the
topic folder and may start `haiku` sub-agents for bulk reading. It records its steps and sources with
the helper scripts of [`backlog/research/tools`](../../../backlog/research/tools/README.md), which is
also how the person watching can see whether it is progressing, without reading its transcript.

## Flow

Entry point: the [`/deep-research`](../../commands/deep-research.md) command, which starts the agent directly with

```
Agent({description: "Deep research <topic>", subagent_type: "deep-researcher", prompt: "Topic: <topic>", run_in_background: true})
```

```mermaid
flowchart LR
    C[/deep-research] --> T[start-topic.sh and brief.md]
    T --> P[deep-researcher]
    P --> H[haiku sub-agents]
    P --> S[log-progress.sh, add-source.sh, add-note.sh]
    P --> R[plan-human.md and plan-machine.md]
    C --> W[show-status.sh while it runs]
```
