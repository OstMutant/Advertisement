// ── Tooling & Pipelines screen ───────────────────────────────────────────────────────────────
// Real link to a source file, relative to this file's own location (two levels above the repo
// root) -- opens the actual file, "resolve to source, never restate it" rule.
function sourceLink(relPath) {
  return `<a href="../../${esc(relPath)}" target="_blank"><code class="path">${esc(relPath)}</code></a>`;
}

// Link to a generated report file, relative to this file's location, opened in a new tab.
function reportLink(relPath, label) {
  return `<a href="../../${esc(relPath)}" target="_blank">${esc(label)}</a>`;
}

function renderScriptGroupSection(n, skipHeading) {
  // Only chip files that DON'T already get their own full block in the "Script headers" list
  // below -- a file with a real parsed header would otherwise appear twice on the same card (a
  // bare chip up here, the real thing right after), the exact kind of duplicate this generator's
  // "one fact, one canonical home" discipline exists to avoid.
  const headeredFiles = new Set((n.headers || []).map(h => h.file.split("/").pop()));
  const chipFiles = (n.files || []).filter(f => !headeredFiles.has(f));
  let html = "";
  // The chip-row is a last-resort file listing -- it only ever renders when nothing else on this
  // card already covers those files. A node with its own README.md is assumed to already name and
  // link its own files in context (the README IS the canonical place for that, per
  // infra-readme-standards) -- showing a second, context-free chip-row above/beside it would
  // duplicate the exact same links the README already gives real explanation around. Only a node
  // with NO README at all (e.g. .claude/commands, whose *.md files carry no infra-doc-standards
  // header either) falls back to the chip-row, so its files are still visible/clickable somewhere.
  if (!skipHeading && (n.description || (!n.readme && chipFiles.length))) {
    html += `<section class="block"><h3>${esc(n.id)}</h3>`;
    if (n.description) html += `<div class="screen-desc">${esc(n.description)}</div>`;
    if (!n.readme && chipFiles.length) {
      html += `<div class="table-chip-row">` + chipFiles.map(f => sourceLink(`${n.id}/${f}`)).join(" ") + `</div>`;
    }
    html += `</section>`;
  } else if (skipHeading && !n.readme && chipFiles.length) {
    html += `<div class="table-chip-row">` + chipFiles.map(f => sourceLink(`${n.id}/${f}`)).join(" ") + `</div>`;
  }
  // Last Dagu-backed CI run's per-step status/duration (scripts/ci only) -- opt-in
  // (--with-ci-metrics), passively read from whatever scripts/ci/reports/pipeline-metrics.json
  // already contains; no live query against Dagu itself, same reasoning as Sonar/ArchUnit above.
  // Renders nothing at all (not even the heading) until real data exists -- an empty placeholder
  // box is dead-weight noise on a screen someone is browsing for real content.
  if (n.id === "scripts/ci" && MODEL.ciPipelineMetrics) {
    const ci = MODEL.ciPipelineMetrics;
    html += `<section class="block"><h3>Last CI run</h3>
      <div class="empty-hint">Source: Dagu run <code>${esc(ci.dagRunId || "unknown")}</code>, finished ${esc(ci.generatedAt || "unknown")}.</div>
        <table class="simple"><thead><tr><th>Step</th><th>Status</th><th>Duration</th></tr></thead><tbody>` +
        (ci.steps || []).map(s => `<tr><td>${esc(s.name || "?")}</td><td>${esc(s.status || "?")}</td><td>${s.durationSeconds != null ? esc(s.durationSeconds.toFixed(0) + "s") : "-"}</td></tr>`).join("") +
        `</tbody></table></section>`;
  }
  // Each file's own structured Description/Usage/Uses/Env/Input/Outputs/Returns header (see
  // .claude/skills/infra-doc-standards/SKILL.md) -- read live from the file itself, self-contained
  // ("what does this do, how do I run it, what do I get"), not a restatement of README's own job
  // (the flow *between* files, rendered right after via n.readme below). A vertical list (one
  // block per file, divided by a horizontal rule) reads far better than a wide table once a file
  // has multi-line field values -- a table cell wraps awkwardly, a block doesn't. Order matches
  // n.files (SCRIPT_GROUP_FILE_ORDER when the dir declares one, entry points listed first).
  if (n.headers && n.headers.length) {
    html += `<section class="block"><h3>Script headers</h3>`;
    n.headers.forEach(h => {
      html += `<div class="header-entry"><div class="header-entry-file">${sourceLink(h.file)}</div>`;
      [["Description", h.description], ["Usage", h.usage], ["Uses", h.uses], ["Env", h.env],
       ["Input", h.input], ["Outputs", h.outputs], ["Returns", h.returns]].forEach(([label, value]) => {
        if (value) html += `<div class="header-entry-field"><strong>${esc(label)}:</strong> ${esc(value)}</div>`;
      });
      html += `</div>`;
    });
    html += `</section>`;
  }
  // The dir's own README.md (if any) -- the flow *between* files, not each file's own behavior
  // again (that's n.headers above) -- rendered as its own block right after.
  if (n.readme) {
    html += `<section class="block">${mdBlockToHtml(n.readme, n.id)}<div class="empty-hint">Source: ${sourceLink(`${n.id}/README.md`)}</div></section>`;
  }
  return html;
}

// Same list-then-drill-in shape as renderDiagrams() (no view.groupId -> card grid of tools;
// view.groupId set -> that one tool's content only).
function renderPipelines() {
  if (!view.groupId) {
    let html = backButtonHtml();
    html += `<h2 class="screen-title">Tooling &amp; Pipelines</h2>
      <div class="screen-desc">Slash commands, skills, custom subagents, and scripts available in this repo — sourced from .claude/commands, .claude/skills, .claude/agents, and scripts/**. Click a card to see its content.</div>`;

    html += `<div class="card-grid">`;
    PIPELINE_GROUP_ORDER.forEach(id => {
      const g = PIPELINE_GROUPS[id];
      html += `<div class="card" onclick="navigate({screen:'pipelines',groupId:'${id}'})">
        <div class="card-title">${g.icon} ${esc(g.label)}</div>
        <div class="card-desc">${esc(g.desc)}</div>
      </div>`;
    });
    html += `</div>`;

    html += `<h3 class="section-title">Services</h3>
      <div class="screen-desc">Local web pages from the Local Services table of ${sourceLink("INFRASTRUCTURE.md")}; each opens in a new tab.</div>
      <div class="card-grid">${MODEL.services.map(s => `<a class="card link-card" href="${esc(s.url)}" target="_blank" rel="noopener">
        <div class="card-title">${esc(s.name)}</div>
        <div class="card-desc">${esc(s.solves).replace(/`([^`]+)`/g, "<code>$1</code>")}</div>
        <div class="card-desc"><code class="path">${esc(s.label)}</code> · ${esc(s.container)}</div>
      </a>`).join("")}</div>`;

    // Root INFRASTRUCTURE.md, rendered inline at the bottom of this same page -- not its own
    // card/drill-down, the same way README.md renders inline at the bottom of the System page.
    html += `<section class="block">${mdBlockToHtml(MODEL.rootInfrastructure || "", "")}<div class="empty-hint">Source: ${sourceLink("INFRASTRUCTURE.md")}</div></section>`;

    document.getElementById("content").innerHTML = html;
    renderFlowBlocks();
    return;
  }

  const g = PIPELINE_GROUPS[view.groupId];
  if (!g) { navigate({ screen: "pipelines" }); return; }

  // ai-tooling and scripts-tree are both tree-shaped (TREE_ROOTS), sharing one drill-down
  // renderer -- see renderScriptTree()'s own header comment for why groupId is passed through.
  if (view.groupId === "scripts-tree" || view.groupId === "ai-tooling") { renderScriptTree(g, view.groupId); return; }

  let html = backButtonHtml();
  html += `<h2 class="screen-title">${g.icon} ${esc(g.label)}</h2>`;

  // build-architecture-page has two real subdirectories worth their own folder-card (scripts/,
  // data/) -- reuses view.path, the exact same mechanism renderScriptTree() already drives
  // navigateBack()/renderBreadcrumb() with (both already groupId-agnostic), instead of inventing a
  // second, parallel "current subview" field that those two functions wouldn't know about. The
  // only PIPELINE_GROUPS entry reaching this point -- ai-tooling/scripts-tree both return early
  // via the tree dispatch above, so no further groupId branch is needed here.
  if (!view.path || !view.path.length) {
    html += `<div class="card-grid">
      <div class="card" onclick='navigate({screen:"pipelines",groupId:"build-architecture-page",path:["scripts"]})'>
        <div class="card-title">📁 scripts</div>
      </div>
      <div class="card" onclick='navigate({screen:"pipelines",groupId:"build-architecture-page",path:["data"]})'>
        <div class="card-title">📁 data</div>
      </div>
    </div>`;
    // docs/architecture's own entry-point files (architecture-doc.sh/.bat) + its own README --
    // a real SCRIPT_GROUP node (readme_json_for() already picks up its README.md the same way
    // it does for every other SCRIPT_GROUP dir), not a separate one-off MODEL field -- one fact,
    // one mechanism, same as scripts/data above. Cards always come first on this page.
    const archNode = scriptGroupNodes.find(x => x.id === "docs/architecture");
    if (archNode) html += renderScriptGroupSection(archNode, true);

    // A fact about this page's own rendering tech stack -- not per-file, so it doesn't belong in
    // any single file's own header (renderScriptGroupSection() already shows every file's real
    // Description/Usage/Uses/Input/Output, mechanically, no second copy of that table here).
    html += `<div class="empty-hint"><strong>Rendering:</strong> Cytoscape.js + cytoscape-dagre for every graph, including the Database ERD and the README flow blocks.</div>`;
  } else {
    const targetId = view.path[0] === "scripts" ? "docs/architecture/scripts" : "docs/architecture/data";
    const n = scriptGroupNodes.find(x => x.id === targetId);
    // skipHeading=true -- same reasoning as renderScriptTree()'s own leaf-level call: the
    // breadcrumb/page title/folder-card just clicked already say the id, and a dir with no
    // parsed file headers (docs/architecture/data) would otherwise show an empty heading+chip-row
    // directly above a README that already fully describes the same files -- two places for one
    // fact, exactly what "one fact, one canonical home" exists to prevent.
    if (n) html += renderScriptGroupSection(n, true);
  }

  document.getElementById("content").innerHTML = html;
  // A README's own ```mermaid fenced block (a .flow-block from mdBlockToHtml()) holds only its source
  // until renderFlowBlocks() draws it.
  renderFlowBlocks();
}

// Walks MODEL's top-level SCRIPT_GROUP nodes down through view.path, for the tree belonging to
// "roots" (TREE_ROOTS[groupId] -- e.g. ["scripts","playwright"] or [".claude"]). The primary root
// (roots[0]) is implicit -- never a path segment of its own, so that card's root view has no
// redundant click-through -- so path[0] resolves against any further root id first (e.g.
// "playwright") and falls back to treating path[0] as the primary root's own child basename
// otherwise; each further segment is the next child directory's own basename (e.g.
// ["ci","dagu"] resolves to scripts/ci/dagu). Returns null if the path doesn't resolve (e.g. a
// stale bookmark after a rename).
function scriptTreeNodeAt(pathSegs, roots) {
  if (!pathSegs.length) return null;
  let node = scriptGroupNodes.find(n => n.id === pathSegs[0]);
  if (!node) {
    const primaryRoot = scriptGroupNodes.find(n => n.id === roots[0]);
    node = primaryRoot ? (primaryRoot.children || []).find(c => c.id.split("/").pop() === pathSegs[0]) : null;
  }
  for (let i = 1; node && i < pathSegs.length; i++) {
    node = (node.children || []).find(c => c.id.split("/").pop() === pathSegs[i]);
  }
  return node || null;
}

// A tree-shaped pipeline card's own arbitrary-depth drill-down -- folder-cards (one per
// node.children, clickable to extend view.path one segment deeper) rendered first, then the flat
// file list for the current level via the existing renderScriptGroupSection() (reused as-is, same
// shape as any flat SCRIPT_GROUP dir). No view.path (or an empty one) shows the primary root's own
// children/files directly (no intermediate click-through card), with any further TREE_ROOTS
// entries (only scripts-tree has one: "playwright") inserted as sibling folder-cards at that same
// level. Navigation back up is the page-level breadcrumb (crumbStack, which records entry into
// this card as one hop) plus the "Back" button already rendered above -- no separate in-card
// breadcrumb of its own. groupId picks which TREE_ROOTS entry drives this render and which
// navigate() calls the folder-cards below use, so scripts-tree and ai-tooling share this one
// function instead of two near-duplicate copies.
function renderScriptTree(g, groupId) {
  const roots = TREE_ROOTS[groupId];
  const path = view.path || [];
  let html = backButtonHtml();
  // Depth suffix (matches the breadcrumb's own " › " join) so the title itself shows current
  // depth at a glance, not just the breadcrumb above it.
  const titleSuffix = path.length ? ` — ${path.join(" › ")}` : "";
  html += `<h2 class="screen-title">${g.icon} ${esc(g.label)}${esc(titleSuffix)}</h2>`;
  // Root-only -- the whole-tree description doesn't apply any more specifically once drilled into
  // a subfolder, and repeating identical text at every depth is exactly the kind of duplication
  // "One fact, one canonical home" already governs elsewhere on this page.
  if (!path.length) html += `<div class="screen-desc">${esc(g.desc)}</div>`;

  let node;
  let children;
  if (!path.length) {
    // Root view: no separate primary-root click-through -- show the primary root's own children
    // and files directly, with any further TREE_ROOTS entries inserted as sibling folder-cards
    // (even though structurally separate top-level nodes, not literally the primary root's own
    // children).
    node = scriptGroupNodes.find(n => n.id === roots[0]);
    const primaryChildren = node ? (node.children || []).map(c => ({ label: c.id.split("/").pop(), path: [c.id.split("/").pop()] })) : [];
    const siblingCards = roots.slice(1)
      .filter(r => scriptGroupNodes.find(n => n.id === r))
      .map(r => ({ label: r, path: [r] }));
    children = primaryChildren.concat(siblingCards);
  } else {
    node = scriptTreeNodeAt(path, roots);
    if (!node) { navigate({ screen: "pipelines", groupId, path: [] }); return; }
    children = (node.children || []).map(c => ({ label: c.id.split("/").pop(), path: path.concat([c.id.split("/").pop()]) }));
  }

  // Folder-cards first: every child directory of the current level, clickable to extend the path
  // one segment deeper. Cards are folders only -- a file never navigates, it only ever renders as
  // a plain row inside renderScriptGroupSection() below.
  if (children.length) {
    html += `<div class="card-grid">`;
    children.forEach(c => {
      const p = JSON.stringify(c.path);
      html += `<div class="card" onclick='navigate({screen:"pipelines",groupId:"${groupId}",path:${p}})'>
        <div class="card-title">📁 ${esc(c.label)}</div>
      </div>`;
    });
    html += `</div>`;
  }

  // Flat file list for the current level, below the folder-cards -- same renderer every flat
  // SCRIPT_GROUP dir already uses, so file headers/README/chip-list all just work unchanged, minus
  // the redundant heading+description+chip-row block (skipHeading=true -- see
  // renderScriptGroupSection()'s own comment for why). No node at the root (path-less) view -- the
  // folder-cards above are the whole list there.
  if (node) html += renderScriptGroupSection(node, true);

  document.getElementById("content").innerHTML = html;
  renderFlowBlocks();
}

