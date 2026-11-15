# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Self-documenting tooling: each script's own header fields and the nested tooling tree nodes of the Tooling & Pipelines screen.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, find, sed, python3.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: The header blocks of the files under the SCRIPT_GROUP_DIRS and SCRIPT_TREE_ROOTS directories.
# Outputs: defines script_headers_json, emit_script_tree_node.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Self-documenting tooling: extracts each file's own "Description:"/"Usage:"/"Uses:"/"Env:"/
# "Input:"/"Outputs:"/"Returns:" header comment fields (the header convention of the infra-doc-standards skill) -- single source of truth right next to the code, feeds each SCRIPT_GROUP node's own
# Tooling & Pipelines card instead of hand-written prose that can silently drift from what the
# files actually do. Takes the same file list already computed for that node (never re-globs
# separately, so "files shown" and "headers parsed" can't drift from each other) -- a file whose
# comment style this can't parse (#, // only) or that simply has no header yet yields no entry, not
# an error, so a new file with the convention shows up with no generator edit needed.
script_headers_json() {
  local dir="$1" file_list="$2"
  python3 -c "
import json, os, re, sys

repo_root = sys.argv[1]
dir_rel = sys.argv[2]
files = [f for f in sys.argv[3].splitlines() if f]

def extract(path):
    with open(path) as fh:
        lines = fh.readlines()
    text = []
    for l in lines:
        l = l.rstrip('\n')
        if l.strip().lower() == '@echo off':
            continue  # .bat's structural equivalent of a shebang -- always first, never the header
        if l.startswith('#'):
            text.append(l[1:].lstrip())
        elif l.startswith('//'):
            text.append(l[2:].lstrip())
        elif re.match(r'^REM(\s|$)', l, re.I):
            text.append(l[3:].lstrip())
        elif l.startswith('/*'):
            # JS-style block-comment open line, e.g. /* -- Header -- ... . Strip the leading /*
            # only -- a trailing */ (a one-line block comment) is stripped the same way the
            # continuation-line branch below strips it off the closing delimiter line.
            rest = l[2:].lstrip()
            if rest.rstrip().endswith('*/'):
                rest = rest.rstrip()[:-2].rstrip()
            text.append(rest)
        elif re.match(r'^\s*\*\s?', l):
            # JS-style block-comment continuation line, e.g. a Description: line or the closing
            # dash-delimiter line ending in */ -- strip the leading * prefix, then a trailing */
            # if this is that closing line.
            rest = re.sub(r'^\s*\*\s?', '', l)
            if rest.rstrip().endswith('*/'):
                rest = rest.rstrip()[:-2].rstrip()
            text.append(rest)
        else:
            break
    fields = {'Description': '', 'Usage': '', 'Uses': '', 'Env': '', 'Input': '', 'Outputs': '', 'Returns': ''}
    current = None
    for l in text:
        m = re.match(r'^(Description|Usage|Uses|Env|Input|Outputs|Returns):\s*(.*)', l)
        if m:
            current = m.group(1)
            fields[current] = m.group(2)
        elif current and (l.strip() == '' or re.match(r'^[─-]+$', l.strip())):
            break  # a blank line or the closing #-delimiter line ends the whole header block --
                   # don't keep scanning for a stray field-name match further down in unrelated
                   # prose (a real bug this once hit), and don't swallow the delimiter itself into
                   # the last field's value as a continuation line (another real bug this once hit)
        elif current:
            fields[current] += '\n' + l.strip()
    return fields

out = []
for f in files:
    fields = extract(os.path.join(repo_root, dir_rel, f))
    if not fields['Description']:
        continue
    out.append({
        'file': dir_rel + '/' + f,
        'description': fields['Description'],
        'usage': fields['Usage'],
        'uses': fields['Uses'],
        'env': fields['Env'],
        'input': fields['Input'],
        'outputs': fields['Outputs'],
        'returns': fields['Returns'],
    })
print(json.dumps(out))
" "$REPO_ROOT" "$dir" "$file_list"
}

# Emits one SCRIPT_GROUP-shaped JSON object for directory "$1" (relative to REPO_ROOT), recursing
# into every real child subdirectory (skipping SCRIPT_TREE_EXCLUDE_DIRS and hidden dirs) as a
# "children" array of the same shape -- an arbitrary-depth drill-down tree, not a fixed number of
# levels. "$2" is the display category the top-level root itself carries (nested children carry no
# category -- only a top-level SCRIPT_GROUP node needs one, to be found by PIPELINE_GROUPS'
# category filter; a nested node is only ever reached by walking a parent's "children" array, never
# looked up by category). Classification of "is this a folder-card or a file" is purely "is it a
# real subdirectory" -- never a naming convention or whether a same-named file also exists (a real
# counterexample: architecture-doc.sh has no architecture-doc/ subfolder at all).
emit_script_tree_node() {
  local d="$1" category="${2:-}"
  local desc=""
  [ -f "$REPO_ROOT/.claude/rules/$d.md" ] && desc="$(sed -n '5{s/^#* *//;p}' "$REPO_ROOT/.claude/rules/$d.md")"

  local files_list="$(script_group_files_list "$d")"
  local files_json decisions_field readme_field headers_field evidence_file
  files_json="$(json_str_array "$files_list")"
  decisions_field="$(decisions_json_for "$d")"
  readme_field="$(readme_json_for "$d")"
  headers_field="$(script_headers_json "$d" "$files_list")"
  evidence_file="$d/DECISIONS.md"
  [ -f "$REPO_ROOT/$d/DECISIONS.md" ] || evidence_file="$d"

  # Immediate child directories only (one level -- recursion handles deeper levels), real
  # subdirectories, alphabetical, minus the excluded generated/report dirs and anything hidden.
  # Skipped entirely for a SCRIPT_TREE_LEAF_DIRS entry -- real subdirectories exist on disk but are
  # deliberately never surfaced as folder-cards (see SCRIPT_TREE_LEAF_DIRS's own comment).
  local child_dirs=() child is_leaf=false ld
  for ld in "${SCRIPT_TREE_LEAF_DIRS[@]}"; do [ "$d" = "$ld" ] && is_leaf=true; done
  if ! $is_leaf; then
    while IFS= read -r child; do
      [ -n "$child" ] && child_dirs+=("$child")
    done < <(find "$REPO_ROOT/$d" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort)
  fi

  local children_json="" first_child=true excluded
  for child in "${child_dirs[@]}"; do
    excluded=false
    for ex in "${SCRIPT_TREE_EXCLUDE_DIRS[@]}"; do [ "$child" = "$ex" ] && excluded=true; done
    [[ "$child" == .* ]] && excluded=true
    $excluded && continue
    $first_child || children_json="$children_json,"
    first_child=false
    children_json="$children_json$(emit_script_tree_node "$d/$child" "")"
  done

  echo "    {"
  echo "      \"id\": \"$(json_escape "$d")\","
  echo "      \"type\": \"SCRIPT_GROUP\","
  if [ -n "$category" ]; then echo "      \"category\": \"$(json_escape "$category")\","; fi
  echo "      \"provenance\": \"OBSERVED\","
  echo "      \"lifecycle\": \"ACTIVE\","
  echo "      \"disposition\": \"KEEP\","
  echo "      \"confidence\": \"extracted\","
  echo "      \"evidence\": [{\"file\": \"$(json_escape "$evidence_file")\", \"line\": 1}],"
  echo "      \"description\": \"$(json_escape "$desc")\","
  echo "      \"files\": $files_json,"
  echo "      \"decisions\": $decisions_field,"
  echo "      \"readme\": $readme_field,"
  echo "      \"headers\": $headers_field,"
  echo "      \"children\": [$children_json],"
  echo "      \"edges\": {}"
  echo "    }"
}

