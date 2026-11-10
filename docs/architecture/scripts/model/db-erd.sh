# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: The Database ERD: tables, foreign keys, indexes and the no-FK edges taken from each column's remarks, read from the Liquibase changelogs.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: run_node (liquibase-schema-to-json.js).
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: The changelog files in DB_ERD_CHANGELOG_FILES.
# Outputs: defines db_erd_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Database ERD: tables, FKs and indexes from the changelogs; no-FK edges from each column's remarks= marker (see module-doc-standards) ──
db_erd_json() {
  local files=()
  local f
  for f in "${DB_ERD_CHANGELOG_FILES[@]}"; do files+=("$REPO_ROOT/$f"); done
  local tables_json relationships_json
  tables_json="$(run_node "$REPO_ROOT/docs/architecture/scripts/liquibase-schema-to-json.js" "$REPO_ROOT" "${files[@]}")"
  relationships_json="$(echo "$tables_json" | run_node -e '
    let d = "";
    process.stdin.on("data", c => d += c);
    process.stdin.on("end", () => {
      const refRe = /References\s+((?:\w+\(\w+\)\s*(?:or\s+)?)+)(?:depending on \w+\s*)?,\s*no FK/i;
      const derived = [];
      const refCols = /^(entity_id|author_id|actor_id|created_by|.*_actor_id|.*_taxon_id)$/;
      const unmarked = [];
      JSON.parse(d).forEach(t => t.columns.forEach(c => {
        const remarks = c.remarks || "";
        const m = refRe.exec(remarks);
        if (!m && !c.foreignKeyTable && refCols.test(c.name)) unmarked.push(t.name + "." + c.name);
        if (!m) return;
        const targetRe = /(\w+)\((\w+)\)/g;
        let tm;
        while ((tm = targetRe.exec(m[1])) !== null) {
          derived.push({ from: tm[1].toUpperCase(), to: t.name.toUpperCase(), label: c.name + " (no FK)", column: c.name, refColumn: tm[2] });
        }
      }));
      if (unmarked.length) {
        process.stderr.write("ERROR: a column that references another table without a foreign key has no \"References <table>(<column>), no FK\" marker in its remarks=: " + unmarked.join(", ") + "\n");
        process.exit(1);
      }
      process.stdout.write(JSON.stringify(derived));
    });
  ')" || return 1
  echo "{\"tables\": $tables_json, \"conceptualRelationships\": $relationships_json}"
}

