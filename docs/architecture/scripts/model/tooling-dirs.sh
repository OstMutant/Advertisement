# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Which non-Maven tooling directories become script-group nodes, which trees are walked for the Tooling & Pipelines screen, and the helpers that read each one's DECISIONS.md, README and root markdown files, plus the local-services table.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, find, grep, run_node (host node, or the node container).
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Every module's and tooling directory's DECISIONS.md and README.md, the root markdown files, INFRASTRUCTURE.md.
# Outputs: defines the SCRIPT_GROUP_* / SCRIPT_TREE_* / FULL_DECISIONS_* configuration and script_group_files_list, decisions_json_for, readme_json_for, root_md_json_for, services_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Full ADR content (Knowledge Pyramid L1 fact, "decisions" domain) for modules with a real,
# hand-authored DECISIONS.md -- embedded directly into this module's node in
# architecture-model.json (inlined into architecture-map.html's own <script> block at generation
# time, same mechanism the rest of MODEL already uses) -- NOT a separate <module>/DECISIONS.json
# loaded at runtime via <script src>. That design was tried and reverted: it depends on
# browser-specific file:// security policy for cross-directory script loading, an unacceptable
# dependency for a tool meant to just work when double-clicked -- see .claude/nav/adr-index.md.
# Parsing lives in .claude/nav/scripts/md-to-decisions-json.js (Node) -- an earlier awk version hit
# two real bugs on real content (label+list with no blank line merging into one paragraph,
# multi-line list items losing their numbering) that JSON.stringify()'s correct-by-construction
# escaping and normal regex/string methods avoid.
#
# FULL_DECISIONS_MODULES itself is computed dynamically further down (right after
# POINTER_DECISIONS_MODULES, which already detects "has real content" per Maven module) rather
# than hand-typed here, so a new module with a real DECISIONS.md is included automatically. Only
# the non-Maven tooling directories below stay a fixed list -- they aren't in pom.xml's <modules>,
# so there's no dynamic source to derive them from.
FULL_DECISIONS_NON_MODULE_DIRS=(scripts docs/architecture/scripts scripts/ci scripts/sonar playwright)
FULL_DECISIONS_MODULES=()

# ── Non-Maven tooling directories -- get a SCRIPT_GROUP node (same ADR-embedding/popup mechanism
# as MODULE nodes) so their files/decisions are visible on the Tooling & Pipelines screen, not
# invisible outside the interactive tool. "category" is the group heading each dir's card renders
# under. Not every SCRIPT_GROUP dir has its own DECISIONS.md -- .claude/nav/scripts's own history moved
# to docs/architecture/scripts/DECISIONS.md wholesale (see docs/architecture/scripts/DECISIONS.md
# ADR-021), so .claude/nav/scripts now gets a files-only node, no ADR/decisions section
# (decisions_json_for/adr_intent_for_module both degrade to empty for a module with no
# DECISIONS.md, not a special case here). docs/architecture/scripts stays a single-level flat card
# here -- scripts, playwright, and .claude are each the root of their own arbitrary-depth
# SCRIPT_GROUP tree instead (SCRIPT_TREE_ROOTS below, .claude/nav/scripts reached as one of its
# nested children), so they're deliberately not listed in SCRIPT_GROUP_DIRS/SCRIPT_GROUP_CATEGORY.
declare -A SCRIPT_GROUP_CATEGORY=(
  [docs/architecture]="Build architecture page"
  [docs/architecture/scripts]="Build architecture page"
  [docs/architecture/data]="Build architecture page"
)
SCRIPT_GROUP_DIRS=(docs/architecture docs/architecture/scripts docs/architecture/data)

# Top-level roots of the unified, arbitrary-depth drill-down tree (see emit_script_tree_node()
# below) -- each root carries its own display category, so the same recursive mechanism feeds both
# the "Scripts" card (scripts/, playwright/) and the "AI Tooling" card (.claude/) on the Tooling &
# Pipelines screen. playwright/ is a physically separate top-level directory, not nested under
# scripts/, but is treated as one child card of the root "Scripts" card
# (docs/architecture/scripts/DECISIONS.md).
declare -A SCRIPT_TREE_ROOT_CATEGORY=(
  [scripts]="Scripts"
  [playwright]="Scripts"
  [.claude]="AI Tooling"
)
SCRIPT_TREE_ROOTS=(scripts playwright .claude)

# Directories that never become their own card/tree node even though they sit inside a
# SCRIPT_TREE_ROOTS subtree -- generated/report output, never source. "wiki" is Dagu's own
# runtime-created documentation folder (see `dagu config`'s "Wiki directory"), written directly
# into whatever directory Dagu was started with `--dags` pointed at -- not part of the real source
# tree, confirmed by its absence outside a running ci-runner container. "worktrees" holds active
# git worktree checkouts -- full copies of the repo tree, not source of their own.
SCRIPT_TREE_EXCLUDE_DIRS=(reports pw-report report logs node_modules wiki worktrees)

# Directories whose real subdirectories are never recursed into, even though they exist on disk --
# .claude/skills/<name>/ is never a folder-card of its own: each skill's own SKILL.md is already
# that unit's complete, self-contained description (see .claude/skills/infra-readme-standards's
# own ".claude/skills/" section), so drilling into one would show an empty card with no headers and
# no README. The .claude/skills card itself still renders normally (its own top-level README.md).
SCRIPT_TREE_LEAF_DIRS=(.claude/skills)

# Explicit "what matters first" ordering per directory -- entry points and generators before the
# CI gates that verify their output, dev-only tooling last. Falls back to alphabetical (find |
# sort) for any directory not listed here. Applies uniformly to SCRIPT_GROUP_DIRS's flat dirs and
# to every level of the SCRIPT_TREE_ROOTS tree (looked up by the node's own directory path).
declare -A SCRIPT_GROUP_FILE_ORDER=(
  [.claude/nav/scripts]="generate-adr-index.sh check-hardcoded-counts.sh check-flows-completeness.sh check-module-lists.sh check-rule-path-globs.sh md-to-decisions-json.js"
  [docs/architecture]="architecture-doc.sh architecture-doc.bat"
  [docs/architecture/scripts]="generate-architecture-model.sh liquibase-schema-to-json.js check-architecture-model-freshness.sh screenshot-architecture-map.sh Dockerfile"
  [docs/architecture/data]="arch-embed-index.md runtime-notes.md"
  [scripts]="build-and-test.sh deploy-and-run.sh ci.sh run-all-tests.sh playwright.sh sonar.sh reset.sh run-local.bat build-and-test.bat deploy-and-run.bat ci.bat run-all-tests.bat playwright.bat sonar.bat claude.bat clean.bat collect-code.bat"
  [scripts/sonar]="run.sh docker-compose.sonar.yml sonar-project.properties"
  [scripts/build-and-test]="run.sh build.sh build-and-test.properties Dockerfile"
  [scripts/deploy-and-run]="run.sh reset.sh docker-compose.db.yml docker-compose.seaweedfs.yml docker-compose.app.yml"
  [scripts/run-all-tests]="run.sh"
  [scripts/ci]="run.sh Dockerfile docker-entrypoint.sh dagu-rest-run-monitor.py"
  [scripts/ci/dagu]="ci.yaml pipeline-metrics.py"
  [playwright/e2e]="_helpers.js 01-marketplace-empty-flow.spec.js 02-marketplace-authentication-flow.spec.js 03-marketplace-promotion-flow.spec.js 04-provider-profile-flow.spec.js 05-marketplace-advertisement-flow.spec.js 06-seed-filter-sort-pagination.spec.js 07-marketplace-delete-flow.spec.js 08-accessibility.spec.js 09-rest-api-swagger.spec.js 10-rest-api-flow.spec.js 11-api-flow-runner.spec.js"
  [playwright/e2e/_flows]="auth.flow.js signup.flow.js advertisement.flow.js advertisement-filter.flow.js filter.flow.js category.flow.js city.flow.js attachment.flow.js audit.flow.js timeline.flow.js entity-activity.flow.js delete.flow.js user-management.flow.js settings.flow.js language-switch.flow.js seed.flow.js"
)
# Files of one script-group directory: those named in SCRIPT_GROUP_FILE_ORDER first, in that order,
# then every other matching file alphabetically, so a new file shows up without editing the table.
# *.md is matched so a markdown-only directory is not an empty card; README.md renders separately.
script_group_files_list() {
  local d="$1" f ordered="" found
  found="$(find "$REPO_ROOT/$d" -maxdepth 1 \( -name '*.sh' -o -name '*.js' -o -name '*.bat' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.md' -o -name 'Dockerfile' \) -printf '%f\n' 2>/dev/null | grep -v '^README\.md$' | sort)"
  if [ -z "${SCRIPT_GROUP_FILE_ORDER[$d]:-}" ]; then
    printf '%s\n' "$found"
    return 0
  fi
  for f in ${SCRIPT_GROUP_FILE_ORDER[$d]}; do
    [ -f "$REPO_ROOT/$d/$f" ] && ordered="$ordered$f"$'\n'
  done
  printf '%s' "$ordered"
  printf '%s\n' "$found" | grep -vxF -f <(printf '%s' "$ordered") || true
}
decisions_json_for() {
  local module="$1"
  # Off by default (see --with-adr-details above) -- full ADR-body embedding accounts for
  # ~605KB/~72% of a with-flag architecture-model.json, baked in whether or not anyone opens the
  # popup. MODEL.allAdrs (built by all_adrs_json() from .claude/nav/adr-index.md) stays populated
  # either way -- it never carried full body text to begin with. openAdrPopupForAdr() still opens
  # the popup (id/title/status always available from MODEL.allAdrs) when a module's "decisions"
  # field is null, showing a source-file link instead of the full body.
  [ -n "$WITH_ADR_DETAILS" ] || { echo "null"; return; }
  [ -f "$REPO_ROOT/$module/DECISIONS.md" ] || { echo "null"; return; }
  local found=false
  for m in "${FULL_DECISIONS_MODULES[@]}"; do [ "$m" = "$module" ] && found=true; done
  $found || { echo "null"; return; }
  run_node "$REPO_ROOT/.claude/nav/scripts/md-to-decisions-json.js" --stdout "$module"
}

# A SCRIPT_GROUP or MODULE dir's own README.md (if it has one), read raw via Node's
# JSON.stringify -- json_escape() strips newlines, unsuitable for multi-paragraph content. Always
# embedded (unlike decisions_json_for(), which is opt-in behind --with-adr-details) -- a README is
# orders of magnitude smaller than a full ADR history.
readme_json_for() {
  local dir="$1"
  [ -f "$REPO_ROOT/$dir/README.md" ] || { echo "null"; return; }
  run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.stringify(d)))' < "$REPO_ROOT/$dir/README.md"
}

# A root-level markdown file (README.md/INFRASTRUCTURE.md), read raw the same way readme_json_for()
# reads a SCRIPT_GROUP dir's own README.md -- these two aren't SCRIPT_GROUP-scoped, so they need
# their own path (repo root, not a dir/README.md join).
root_md_json_for() {
  local file="$1"
  [ -f "$REPO_ROOT/$file" ] || { echo "null"; return; }
  run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.stringify(d)))' < "$REPO_ROOT/$file"
}

# The web-page rows (an http:// link in the link cell) of INFRASTRUCTURE.md's "Local Services" table as a JSON array.
services_json() {
  [ -f "$REPO_ROOT/INFRASTRUCTURE.md" ] || { echo "services_json: INFRASTRUCTURE.md not found" >&2; return 1; }
  run_node -e '
    const lines = require("fs").readFileSync(process.argv[1], "utf8").split(/\r?\n/);
    const start = lines.findIndex(l => /^##\s+Local Services\s*$/.test(l));
    if (start < 0) { console.error("services_json: Local Services section not found"); process.exit(1); }
    const out = [];
    for (let i = start + 1; i < lines.length && !/^##\s/.test(lines[i]); i++) {
      if (!lines[i].startsWith("|")) continue;
      const c = lines[i].split("|").slice(1, -1).map(s => s.trim());
      if (c.length < 4) continue;
      const m = c[2].match(/\[([^\]]+)\]\((http:\/\/[^)]+)\)/);
      if (!m) continue;
      out.push({ name: c[0], container: c[1].replace(/`/g, ""), label: m[1], url: m[2], solves: c[3] });
    }
    if (!out.length) { console.error("services_json: no web-page rows in Local Services"); process.exit(1); }
    process.stdout.write(JSON.stringify(out));
  ' "$REPO_ROOT/INFRASTRUCTURE.md"
}

