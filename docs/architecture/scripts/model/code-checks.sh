# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Live architecture checks: coupling grep checks with PASS/FAIL, the largest Java files, constructor injection complexity and god-package analysis.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, grep, find, sed, awk.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Java sources of every module.
# Outputs: defines coupling_checks_json, largest_java_files_json, constructor_injection_json, god_packages_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Live coupling checks: re-runs real grep commands, each producing a PASS/FAIL with the real
# evidence, not hand-typed "checkmark" text.
coupling_checks_json() {
  local checks_json="" first_c=true name pass evidence

  name="No Vaadin imports in starters"
  evidence="$(grep -rl 'import com\.vaadin\.' "$REPO_ROOT"/*-spring-boot-starter/src/main/java --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||" | paste -sd, -)"
  [ -z "$evidence" ] && pass=true || pass=false
  [ -z "$evidence" ] && evidence="grep for com.vaadin imports across every *-spring-boot-starter -- no matches"
  first_c=false
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  name="No direct starter-to-starter internal imports"
  local viol="" mod other
  for mod in "${MODULES[@]}"; do
    [[ "$mod" == *-spring-boot-starter ]] || continue
    for other in "${MODULES[@]}"; do
      [[ "$other" == *-spring-boot-starter ]] || continue
      [ "$mod" = "$other" ] && continue
      # Real package root from the real directory structure, not a string-stripped guess -- module
      # names don't always match their package 1:1 (provider-profile-spring-boot-starter's real
      # root is org.ost.provider, not org.ost.provider-profile, which a naive suffix-strip would
      # produce and which can never match a real import).
      local other_pkg
      other_pkg="org.ost.$(find "$REPO_ROOT/$other/src/main/java/org/ost" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | head -1)"
      local hit
      hit="$(grep -rl "import ${other_pkg}\." "$REPO_ROOT/$mod/src/main/java" --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||")"
      [ -n "$hit" ] && viol="$viol$hit,"
    done
  done
  [ -z "$viol" ] && pass=true || pass=false
  [ -z "$viol" ] && evidence="checked every starter pair for cross-package imports -- no matches" || evidence="$viol"
  $first_c || checks_json="$checks_json,"$'\n'
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  name="No UI to Repository direct imports"
  evidence="$(grep -rl 'import org\.ost\.[a-z]*\.repository\.' "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/ui" --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||" | paste -sd, -)"
  [ -z "$evidence" ] && pass=true || pass=false
  [ -z "$evidence" ] && evidence="grep for org.ost.*.repository imports under marketplace-app ui package -- no matches"
  $first_c || checks_json="$checks_json,"$'\n'
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  echo "[$checks_json"$'\n'"  ]"
}

# ── Largest Java files by line count across the whole repo -- the same `find ... | sort -rn |
# head` command 07-risk-report.md (now deleted) had written as text, re-run every generation
# instead of showing a dated static snapshot.
largest_java_files_json() {
  local items_json="" first_i=true line file lines mod
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    lines="$(echo "$line" | awk '{print $1}')"
    file="$(echo "$line" | awk '{$1=""; print substr($0,2)}')"
    file="${file#"$REPO_ROOT"/}"
    mod="${file%%/*}"
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"file\": \"$(json_escape "$(basename "$file")")\", \"path\": \"$(json_escape "$file")\", \"lines\": $lines, \"module\": \"$(json_escape "$mod")\"}"
  done < <(find "$REPO_ROOT" -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' -not -path '*/.claude/worktrees/*' -exec wc -l {} \; 2>/dev/null | sort -rn | head -10)
  echo "[$items_json"$'\n'"  ]"
}

# ── Constructor Injection Complexity: every class with @RequiredArgsConstructor and 4+
# constructor-injected (private final) fields -- the full list, not the 4 hand-picked examples
# 07-risk-report.md (now deleted) used to show.
constructor_injection_json() {
  local items_json="" first_i=true f class_name field_count mod

  while IFS= read -r f; do
    [ -z "$f" ] && continue
    field_count="$(grep -cP '^\s*private final \S' "$f" 2>/dev/null)"
    [ "${field_count:-0}" -lt 4 ] && continue
    class_name="$(basename "$f" .java)"
    mod="${f#"$REPO_ROOT"/}"; mod="${mod%%/*}"
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"class\": \"$(json_escape "$class_name")\", \"module\": \"$(json_escape "$mod")\", \"fieldCount\": $field_count, \"file\": \"$(json_escape "${f#"$REPO_ROOT"/}")\"}"
  done < <(grep -rl '@RequiredArgsConstructor' "$REPO_ROOT" --include='*.java' 2>/dev/null | grep -v '/target/' | grep -v '/.claude/worktrees/' | grep '/src/main/java/' | sort)

  echo "[$items_json"$'\n'"  ]"
}

# ── Package God-Package Analysis: every package (recursive) with more than 20 .java files --
# the full list, not 4 hand-picked examples.
god_packages_json() {
  local items_json="" first_i=true pkg count depth

  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    # Only 2-3 segments past org/ost (org.ost.X or org.ost.X.Y) -- deeper segments would flood the
    # list with small, already-well-organized leaf packages instead of the handful of genuinely
    # broad ones worth flagging.
    depth="$(echo "${pkg#"$REPO_ROOT"/}" | sed 's|.*/org/ost/||' | tr '/' '\n' | wc -l)"
    [ "$depth" -gt 2 ] && continue
    count="$(find "$pkg" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
    [ "$count" -le 20 ] && continue
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"package\": \"$(json_escape "${pkg#"$REPO_ROOT"/}")\", \"fileCount\": $count}"
  done < <(find "$REPO_ROOT" -path '*/src/main/java/*' -not -path '*/target/*' -not -path '*/.claude/worktrees/*' -type d 2>/dev/null | sort)

  echo "[$items_json"$'\n'"  ]"
}

