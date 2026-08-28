# `.claude/agents/dagu/`

Inspects the local Dagu CI runner's workflow state and run history via Dagu's own built-in MCP
server — no separate package or wrapper process, Dagu exposes MCP directly as a Streamable HTTP
endpoint on its existing web server. Replaces polling
[`scripts/ci/watch-run.py`](../../../scripts/ci/watch-run.py) or reading the Dagu web UI
(`http://localhost:8082`) by hand for a one-off status check.

## Flow

Entry point: [`dagu-analyst`](dagu-analyst.md), dispatched directly —

```
Agent({description: "<short task description>", subagent_type: "dagu-analyst", prompt: "<what you want to know>"})
```

Unlike [`sonar-analyst`](../sonar/sonar-analyst.md), this needs no wrapper script and no auth
token: `ci-runner` runs with `DAGU_AUTH_MODE=none` (see `scripts/ci/Dockerfile`), and MCP is just
an HTTP endpoint on Dagu's own already-running server (`http://localhost:18080/mcp`) — nothing to
launch fresh per dispatch, since there's no process for Claude Code to start in the first place.
An HTTP-type `mcpServers` entry has no launch-command hook to auto-start anything the way
`sonar-analyst`'s wrapper script does, so `dagu-analyst.md`'s own body instructs it to check
`docker ps` and run `bash scripts/ci.sh` itself first if `ci-runner` isn't already up, before
attempting any MCP tool call. Confirmed directly: MCP connections are established before an
agent's own instructions run, so starting `ci-runner` mid-dispatch does not make the `dagu` MCP
tools available *within that same dispatch* — only on a later one. `dagu-analyst.md` falls back to
querying Dagu's own REST API directly (the same one
[`scripts/ci/dagu/pipeline-metrics.py`](../../../scripts/ci/dagu/pipeline-metrics.py) already
uses) rather than failing outright when this happens.
`ci-runner` runs `--network host` (same as the `claude-dev` container itself), so this port is
directly reachable without the `ci-runner-dagu-proxy` sidecar — that proxy exists only so a real
browser outside any Docker network can reach the Dagu UI, not for container-to-container MCP
traffic.
