# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Per-module dependency extraction from each module's own pom.xml, the root project's own artifactId/version and the reverse ("who depends on me") lookup.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, awk.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Root pom.xml and each module's pom.xml.
# Outputs: defines module_deps, reverse_deps_for_module, MODULES_JOINED, ROOT_ARTIFACT_ID, ROOT_VERSION.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Per-module dependency extraction from each module's own pom.xml ────────────────────────────
# State machine over one-line-per-tag <dependency>...</dependency> blocks (this repo's consistent
# pom.xml formatting, confirmed by direct inspection) -- not a general XML parser.
module_deps() {
  local module="$1"
  local pom="$REPO_ROOT/$module/pom.xml"
  [ -f "$pom" ] || return 0
  awk -v mods="${MODULES_JOINED}" '
    BEGIN { split(mods, modarr, ",") }
    /<dependency>/ { artifact=""; scope="compile"; optional="false"; next }
    /<artifactId>/ {
      gsub(/.*<artifactId>|<\/artifactId>.*/, "", $0)
      artifact = $0
      next
    }
    /<scope>/ {
      gsub(/.*<scope>|<\/scope>.*/, "", $0)
      scope = $0
      next
    }
    /<optional>true<\/optional>/ { optional = "true"; next }
    /<\/dependency>/ {
      is_module = 0
      for (i in modarr) if (modarr[i] == artifact) is_module = 1
      if (is_module) print artifact "|" scope "|" optional
    }
  ' "$pom"
}
MODULES_JOINED="$(IFS=,; echo "${MODULES[*]}")"

# ── Root pom.xml's own artifactId/version (not the Spring Boot parent's) -- the project's own
# <artifactId>/<version> lines are the two immediately after </parent> closes. Feeds the Module
# Dependencies diagram page's "Module Versions" line so it's mechanical, not hand-typed. ──────────
ROOT_ARTIFACT_ID="$(awk '/<\/parent>/{f=1;next} f && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$REPO_ROOT/pom.xml")"
ROOT_VERSION="$(awk '/<\/parent>/{f=1;next} f && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' "$REPO_ROOT/pom.xml")"

# ── Reverse dependency lookup ("who depends on me") -- for the human drill-down view; the pom.xml
#    dependency direction alone only tells a reader "what do I depend on," not "what would break if
#    I changed" -- the latter is exactly the question the plan's own §4 "blast-radius" goal names. ──
reverse_deps_for_module() {
  local target="$1"
  for m in "${MODULES[@]}"; do
    [ "$m" = "$target" ] && continue
    if module_deps "$m" | awk -F'|' -v t="$target" '$1==t{found=1} END{exit !found}'; then
      echo "$m"
    fi
  done
}

