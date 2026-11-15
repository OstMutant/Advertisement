// ── System screen: the entry-point cards (Tooling & Pipelines, Backlog, Diagrams, ADRs, Code Quality) and the README. Module browsing lives under Diagrams ›
// Module Dependencies (one shared graph-building function, not a second copy — see
// renderModuleDependencyGraph() and docs/architecture/scripts/DECISIONS.md ADR-003). ──────────────────────────
function renderSystem() {
  let html = `<h2 class="screen-title">System</h2>
    <div class="screen-desc">${moduleNodes.length} modules across ${domainOrder.length} domains. Pick where to start.</div>`;

  html += `<div class="card-grid">
    <div class="card special-card" onclick="navigate({screen:'pipelines'})">
      <div class="card-title">🛠 Tooling &amp; Pipelines</div>
      <div class="card-desc">${commandNodes.length} commands, ${skillNodes.length} skills, ${agentNodes.length} agents, ${scriptGroupNodes.length} script groups</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'backlog'})">
      <div class="card-title">📋 Backlog</div>
      <div class="card-desc">${backlogNode ? backlogNode.open_issues : "?"} open, ${backlogNode ? backlogNode.completed_issues : "?"} completed</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'diagrams'})">
      <div class="card-title">📐 Diagrams</div>
      <div class="card-desc">${totalDiagramCount} diagrams — dependency graph (click a module to drill in), SPI map, context map, ERD</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'adrs'})">
      <div class="card-title">📜 ADRs</div>
      <div class="card-desc">${(MODEL.allAdrs || []).length} architectural decisions across every module's DECISIONS.md</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'codequality'})">
      <div class="card-title">✅ Code Quality</div>
      <div class="card-desc">SonarQube + ArchUnit metrics per module, one table per source</div>
    </div>
  </div>`;

  // Root README.md, rendered inline at the bottom of this same page -- not a card/screen of its
  // own, the same way renderScriptGroupSection() renders a SCRIPT_GROUP dir's own README directly
  // in place rather than behind a click-through.
  html += `<section class="block">${mdBlockToHtml(MODEL.rootReadme || "", "")}<div class="empty-hint">Source: ${sourceLink("README.md")}</div></section>`;

  document.getElementById("content").innerHTML = html;
  renderFlowBlocks();
}

// Domain-colored, click-to-navigate module dependency graph -- the one and only place this is
// built (previously duplicated: a rich version on the old System page, a generic Mermaid-text
// re-parse under Diagrams). Renders into #diagram-cy and assigns to the shared `diagramCy`
// variable so Diagrams' existing zoom toolbar (zoomDiagram()/#zoom-label) works unmodified.
// Shared node/edge-building + Cytoscape style for both Module Dependencies diagrams (Production
// Modules, Integration Tests) -- same visual language, different node subset. Auto-sized nodes
// ("width"/"height": "label") instead of a fixed box -- a fixed 92x34 box clipped longer labels
// like "marketplace-orchestrator" (confirmed directly: rendered bounding box taller than the box
// itself). Edges are only included when both ends are in this diagram's own node subset, so the
// Integration Tests diagram doesn't reach out to production-only modules it has no edge to anyway.
function moduleDependencyElements(nodes) {
  const ids = new Set(nodes.map(n => n.id));
  const els = nodes.map(n => ({
    data: { id: n.id, label: n.id.replace(/-spring-boot-starter$/, ""), domain: n.domain },
    style: { "background-color": domainColor(n.domain) }
  }));
  nodes.forEach(n => {
    // Layout edges point dependency -> dependent (reverse of the real semantic direction) so
    // dagre's left-to-right ranking puts foundational/most-depended-on modules on the left and
    // consumers on the right -- dagre ranks by source-before-target, and "X depends on Y" should
    // read Y (left) <- X (right).
    // The arrowhead is drawn on the *visual* source/target below, independent of layout ranking.
    ["DEPENDS_ON_COMPILE","DEPENDS_ON_RUNTIME"].forEach(et => {
      (n.edges[et] || []).forEach(target => {
        if (!ids.has(target)) return;
        els.push({ data: { id: n.id+"->"+target+et, source: target, target: n.id, dashed: et==="DEPENDS_ON_RUNTIME" } });
      });
    });
  });
  return els;
}

function renderOneModuleDependencyGraph(containerId, zoomLabelId, nodes, assignCy) {
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  const cy = cytoscape({
    container: document.getElementById(containerId),
    elements: moduleDependencyElements(nodes),
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 10, "color": "#fff", "text-valign": "center", "text-halign": "center",
          "width": "label", "height": "label", "padding": "10px", "shape": "round-rectangle", "text-wrap": "wrap", "text-max-width": 90
        } },
      { selector: "edge", style: {
          "width": 2, "line-color": "#718096", "target-arrow-color": "#718096",
          "source-arrow-shape": "triangle", "source-arrow-color": "#718096", "source-arrow-scale": 1.6, "target-arrow-shape": "none",
          "curve-style": "bezier"
        } },
      { selector: "edge[?dashed]", style: { "line-style": "dashed" } }
    ],
    layout: hasDagre
      ? { name: "dagre", rankDir: "LR", nodeSep: 18, rankSep: 70, edgeSep: 12, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20, spacingFactor: 1.1 }
  });
  cy.on("tap", "node", e => navigate({ screen: "module", id: e.target.id() }));
  // A click on an arrow centres the dependent module's row in the Dependency Table and flashes the dependency it names.
  cy.edges().style("cursor", "pointer");
  cy.on("tap", "edge", e => {
    const row = document.getElementById("dep-row-" + e.target.data("target"));
    if (!row) return;
    row.scrollIntoView({ behavior: "smooth", block: "center" });
    const badge = [...row.cells[1].querySelectorAll(".module-badge")].find(b => b.textContent === e.target.data("source"));
    [row, badge].filter(Boolean).forEach(el => { el.classList.remove("row-flash"); void el.offsetWidth; el.classList.add("row-flash"); });
  });
  cy.on("zoom", () => {
    const label = document.getElementById(zoomLabelId);
    if (label) label.textContent = Math.round(cy.zoom() * 100) + "%";
  });
  assignCy(cy);
}

function renderModuleDependencyGraph() {
  const testNode = moduleNodes.find(n => n.id === "integration-tests");
  const testIds = new Set(["integration-tests", ...((testNode && testNode.edges.DEPENDS_ON_COMPILE) || [])]);
  const mainNodes = moduleNodes.filter(n => n.id !== "integration-tests");
  const testNodes = moduleNodes.filter(n => testIds.has(n.id));
  renderOneModuleDependencyGraph("diagram-cy", "zoom-label", mainNodes, cy => { diagramCy = cy; });
  renderOneModuleDependencyGraph("diagram-cy-2", "zoom-label-2", testNodes, cy => { diagramCy2 = cy; });
}

function zoomDiagram2(delta) {
  if (!diagramCy2) return;
  if (delta === 0) { diagramCy2.fit(undefined, 20); return; }
  diagramCy2.zoom({ level: diagramCy2.zoom() + delta, renderedPosition: { x: diagramCy2.width()/2, y: diagramCy2.height()/2 } });
}

// Scope-grouped dependency list for one module -- built from the same edges the graph/module
// pages use, not a second hand-typed table (this replaces the old docs/architecture/
// 01-module-dependencies.md "Dependency Table", see docs/architecture/scripts/DECISIONS.md ADR-005). Returns
// [{label, deps}] groups; moduleDepsScopeText()/moduleDepsScopeHtml() below render this the same
// shape two ways (plain text for the markdown export, linked HTML for the on-page table) instead
// of each re-deriving compile/optional/runtime grouping independently.
function moduleDepsScopeGroups(n) {
  const compile = n.edges.DEPENDS_ON_COMPILE || [];
  const runtime = n.edges.DEPENDS_ON_RUNTIME || [];
  const optional = n.edges.DEPENDS_ON_OPTIONAL || [];
  const compileOnly = compile.filter(d => !optional.includes(d));
  const groups = [];
  if (compileOnly.length) groups.push({ label: "compile", deps: compileOnly });
  if (optional.length) groups.push({ label: "optional", deps: optional });
  if (runtime.length) groups.push({ label: "runtime", deps: runtime });
  return groups;
}

// e.g. "platform-commons, query-lib (compile); audit-spring-boot-starter (optional)" -- plain
// text, for the markdown export (exportModuleDependenciesMarkdown()).
function moduleDepsScopeText(n) {
  const groups = moduleDepsScopeGroups(n);
  return groups.length ? groups.map(g => `${g.deps.join(", ")} (${g.label})`).join("; ") : "(none — foundation)";
}

// Same grouping as moduleDepsScopeText(), but each dependency name links to its own module page --
// for the on-page Dependency Table only (HTML can't survive a markdown export, hence the separate
// plain-text variant above).
function moduleDepsScopeHtml(n) {
  const groups = moduleDepsScopeGroups(n);
  if (!groups.length) return "(none — foundation)";
  return groups.map(g => {
    const badges = g.deps.map(d => moduleBadgeHtml(d)).join(" ");
    return `${badges} <span class="scope-label">(${g.label})</span>`;
  }).join(" ");
}

// Same rounded, domain-colored pill the graph nodes use (domainColor()) -- for module names shown
// in the Dependency Table, so a module reads as "the same thing" whether it's a graph node or a
// table cell (user-requested visual consistency, 2026-08-05).
function moduleBadgeHtml(id) {
  const n = byId[id];
  const color = n ? domainColor(n.domain) : "#718096";
  return `<a class="module-badge" onclick="navigate({screen:'module',id:'${id}'})" style="background:${color}">${esc(id)}</a>`;
}

// Editorial commentary about the module graph as a whole; every module list and count in it is read
// from the model, so adding a module cannot leave it stale.
const MODULE_DEPENDENCY_KEY_OBSERVATIONS = (() => {
  const shortName = id => id.replace(/-spring-boot-starter$/, "");
  const starterIds = moduleNodes.map(n => n.id).filter(id => id.endsWith("-spring-boot-starter"));
  const orchestratorEdges = (byId["marketplace-orchestrator"] || {}).edges || {};
  const compileStarters = (orchestratorEdges.DEPENDS_ON_COMPILE || []).filter(id => starterIds.includes(id)).map(shortName);
  const runtimeStarters = (orchestratorEdges.DEPENDS_ON_RUNTIME || []).filter(id => starterIds.includes(id)).map(shortName);
  return [
    "**Shared Kernel:** platform-commons is the foundation — starters never depend on each other, they cooperate only through platform-commons SPI contracts; the modules that do depend on several starters are marketplace-orchestrator, the adapters that sit on it, and the test-only integration-tests.",
    `**Starter Independence:** Each of the ${starterIds.length} starters (${starterIds.map(shortName).join(", ")}) is self-contained: it depends only on platform-commons and the shared libraries, never on another starter, and can be deployed independently.`,
    `**Optional Starters:** marketplace-orchestrator reaches ${runtimeStarters.join(", ")} only through ComponentFactory<XPort> and declares them at runtime scope, so the application can run without any of them.`,
    "**Query Library:** query-lib is a pure utility library (no Spring Boot autoconfiguration) that provides SQL filtering and sorting helpers.",
    "**No Circular Dependencies:** All edges are acyclic — the dependency graph forms a DAG.",
    `**Orchestrator Pulls Every Starter:** marketplace-orchestrator, not marketplace-app, declares all ${starterIds.length} starter dependencies (${compileStarters.join("/")} at compile scope, ${runtimeStarters.join("/")} at runtime scope) — the one module that assembles the full feature set onto the runtime classpath.`,
    "**Three Adapters:** marketplace-app (Vaadin UI) and marketplace-rest-api (external REST API) each depend on platform-commons + marketplace-orchestrator (marketplace-app also depends on marketplace-rest-api and marketplace-mcp directly, to stay the sole @SpringBootApplication entry point), and neither composes multiple domain Ports itself; marketplace-mcp has no project dependency and reaches the REST API over HTTP with the caller's own API key.",
    "**Test-Only Reactor Member:** integration-tests is the only test-only module that depends on several domain starters at once (marketplace-orchestrator does too, but it is shipped and reaches them through ComponentFactory ports) — a real, compile-scope Maven dependency, not SPI-mediated. This is safe only because the module is never shipped, deployed, or depended upon by anything else (a leaf with zero inbound edges) — see .claude/rules/integration-tests.md for the full rationale and why this doesn't violate \"starters must not depend on each other.\""
  ];
})();

// baseDir: repo-relative directory the source markdown file itself lives in (e.g. ".claude/nav"
// for .claude/nav/README.md, "" for a repo-root file) -- resolves a same-directory link target
// (e.g. "adr-index.md") into a real, clickable sourceLink(). A link target already containing "/"
// (e.g. "docs/architecture/bounded-contexts.md") is treated as already repo-root-relative and used
// as-is, regardless of baseDir -- matches how these READMEs actually write cross-directory links.
function mdInlineToHtml(s, baseDir) {
  let html = esc(s).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/`([^`]+)`/g, "<code>$1</code>");
  return html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, label, url) => {
    if (/^https?:\/\//.test(url)) return `<a href="${url}" target="_blank">${label}</a>`;
    if (url.startsWith("#")) return m;
    const relPath = url.includes("/") ? url : (baseDir ? `${baseDir}/${url}` : url);
    return `<a href="../../${esc(relPath)}" target="_blank">${label}</a>`;
  });
}

// Paragraph/list/table/heading/fenced-code markdown -> HTML for ADR body text (Context/Decision/
// Consequences, amendments, tables) and README.md content (headings, fenced "how to run" bash
// blocks). Not a general markdown parser -- same "only what's needed" scope as
// parseFlowchart(). Reuses mdInlineToHtml() for **bold**/`code` within each non-code block.
function mdBlockToHtml(text, baseDir) {
  if (!text) return "";
  // Blocks break on a blank line OR on a line-type transition (e.g. "**Decision:**" directly
  // followed by "1. ..." with no blank line between -- a real pattern in these ADRs). A plain
  // (non-marker) line while inside a list is a soft-wrapped continuation of the *previous* list
  // item, not a new paragraph -- real ADR list items wrap across multiple source lines.
  const blocks = [];
  let current = null;
  const lines = text.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    const line = raw.trim();
    if (line.startsWith("```")) {
      // Fenced code block -- consumed verbatim (no inline markdown, no wrapping into the
      // surrounding paragraph/list logic) until the matching closing fence or end of text. The
      // language tag (```mermaid) decides how it renders -- see the "code" block-type mapping below.
      const lang = line.slice(3).trim();
      const codeLines = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) { codeLines.push(lines[i]); i++; }
      blocks.push({ type: "code", lang, text: codeLines.join("\n") });
      current = null;
      continue;
    }
    if (line === "") { current = null; continue; }
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      blocks.push({ type: "h", level: heading[1].length, text: heading[2] });
      current = null;
      continue;
    }
    if (/^[-*] /.test(line)) {
      if (!current || current.type !== "ul") { current = { type: "ul", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^[-*] /, ""));
      continue;
    }
    if (/^\d+\. /.test(line)) {
      if (!current || current.type !== "ol") { current = { type: "ol", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^\d+\. /, ""));
      continue;
    }
    if (line.startsWith("|")) {
      if (!current || current.type !== "table") { current = { type: "table", lines: [] }; blocks.push(current); }
      current.lines.push(line);
      continue;
    }
    if (current && (current.type === "ul" || current.type === "ol") && current.items.length) {
      current.items[current.items.length - 1] += " " + line;
      continue;
    }
    if (!current || current.type !== "p") { current = { type: "p", lines: [] }; blocks.push(current); }
    current.lines.push(line);
  }
  return blocks.map(b => {
    if (b.type === "code" && b.lang === "mermaid") return `<div class="diagram-wrap flow-block" style="padding:8px"><pre class="flow-src" style="display:none">${esc(b.text)}</pre><div class="flow-cy" style="width:100%;height:200px"></div></div>`;
    if (b.type === "code") return `<pre class="md-code">${esc(b.text)}</pre>`;
    // +3: a README's top-level "#" title would collide with the surrounding page's own <h2>/<h3>
    // screen chrome -- start one level down (## -> <h4>) same as this tool's other embedded-markdown
    // headings.
    if (b.type === "h") { const lvl = Math.min(b.level + 3, 6); return `<h${lvl}>${mdInlineToHtml(b.text, baseDir)}</h${lvl}>`; }
    if (b.type === "ul") return "<ul>" + b.items.map(l => `<li>${mdInlineToHtml(l, baseDir)}</li>`).join("") + "</ul>";
    if (b.type === "ol") return "<ol>" + b.items.map(l => `<li>${mdInlineToHtml(l, baseDir)}</li>`).join("") + "</ol>";
    if (b.type === "table" && b.lines.length > 1 && /^\|[\s:|-]+\|$/.test(b.lines[1])) {
      const rows = b.lines.filter((_, i) => i !== 1).map(l => l.split("|").slice(1, -1).map(c => c.trim()));
      const [head, ...body] = rows;
      return `<table class="simple"><thead><tr>${head.map(c => `<th>${mdInlineToHtml(c, baseDir)}</th>`).join("")}</tr></thead><tbody>` +
        body.map(r => `<tr>${r.map(c => `<td>${mdInlineToHtml(c, baseDir)}</td>`).join("")}</tr>`).join("") + `</tbody></table>`;
    }
    return `<p>${mdInlineToHtml(b.lines.join(" "), baseDir)}</p>`;
  }).join("");
}

function renderModuleDependencyExtrasHtml() {
  const rows = moduleNodes.map(n =>
    `<tr id="dep-row-${esc(n.id)}"><td>${moduleBadgeHtml(n.id)}</td><td>${moduleDepsScopeHtml(n)}</td></tr>`
  ).join("");
  const observations = MODULE_DEPENDENCY_KEY_OBSERVATIONS.map(o =>
    `<li>${mdInlineToHtml(o).replace(/&lt;optional&gt;true&lt;\/optional&gt;/, "<code>&lt;optional&gt;true&lt;/optional&gt;</code>")}</li>`
  ).join("");
  // Domain colors are assigned by first-appearance order (domainOrder/domainColor() above), not a
  // fixed mapping -- a static legend would go stale the moment a new domain is added. Built live
  // from the exact same function the graph itself uses to color each node, so it can't drift.
  const domainLegendItems = domainOrder.map(d =>
    `<span style="display:inline-flex;align-items:center;margin-right:16px;white-space:nowrap"><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:${domainColor(d)};margin-right:6px"></span>${esc(d)}</span>`
  ).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">Every module dependency is declared in that module's own <code class="path">pom.xml</code> <code class="path">&lt;dependencies&gt;</code> block — this graph is rendered live from those real files, not a separately-maintained diagram. If a dependency changes, edit <code class="path">pom.xml</code>; the graph updates on the next regenerate, nothing else to keep in sync.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a node to open its module page. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">──▶ (solid line)</td><td>Compile-scope dependency — arrow points toward the module being depended on</td></tr>
        <tr><td class="scope-label">┄┄▶ (dashed line)</td><td>Runtime-scope dependency</td></tr>
      </tbody></table>
      <div class="empty-hint" style="margin-top:8px">Node color = domain (same color used consistently across this diagram):</div>
      <div style="display:flex;flex-wrap:wrap;margin-top:6px">${domainLegendItems}</div>
    </section>
    <section class="block"><h3>Dependency Table</h3>
      <table class="simple"><thead><tr><th>Module</th><th>Depends on</th></tr></thead><tbody>${rows}</tbody></table>
    </section>
    <section class="block"><h3>Key Observations</h3><ol class="info-list">${observations}</ol></section>
    <section class="block"><h3>Module Versions</h3>
      <div class="empty-hint">All modules are siblings with the same version: <code>${esc(MODEL.rootVersion)}</code>. Parent POM artifact: <code>${esc(MODEL.rootArtifactId)}</code>.</div>
    </section>`;
}

// Real grep-based checks (cyclic-import safety net beyond the module-level DAG already shown
// above), re-run every generation -- each PASS/FAIL carries the real evidence, not hand-typed text.
function renderArchitectureChecksHtml() {
  const checks = MODEL.couplingChecks || [];
  const rows = checks.map(c =>
    `<tr><td>${c.pass ? "✓ PASS" : "✗ FAIL"}</td><td>${esc(c.name)}</td><td>${esc(c.evidence)}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Architecture Checks (${checks.length})</h3>
    <table class="simple"><thead><tr><th>Result</th><th>Check</th><th>Evidence</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

// Merges consecutive rows sharing the same key into one rowspan-ed cell -- only merges runs
// already adjacent in the data's own natural order, never reorders rows to create bigger runs (a
// real gap between two same-value rows stays two separate cells, on purpose). Returns one number
// per row: 0 means "this row's cell for that column is omitted, an earlier row's rowspan covers
// it"; N (> 0) means "this row starts a run of N, render its own rowspan=N cell here."
function consecutiveRowspan(rows, keyFn) {
  return rows.map((row, i) => {
    if (i > 0 && keyFn(rows[i - 1]) === keyFn(row)) return 0;
    let span = 1;
    while (i + span < rows.length && keyFn(rows[i + span]) === keyFn(row)) span++;
    return span;
  });
}

function renderLargestJavaFilesHtml() {
  const files = MODEL.largestJavaFiles || [];
  if (!files.length) return "";
  const spans = consecutiveRowspan(files, f => f.module);
  const rows = files.map((f, idx) => {
    const moduleCell = spans[idx] > 0 ? `<td rowspan="${spans[idx]}">${moduleLink(f.module)}</td>` : "";
    return `<tr><td>${fileLink(f.path, f.file)}</td><td>${f.lines}</td>${moduleCell}</tr>`;
  }).join("");
  return `<section class="block"><h3>Largest Java Files</h3>
    <div class="empty-hint">Top 10 by line count, across the whole repo -- recomputed every generation.</div>
    <table class="simple rowspan-table"><thead><tr><th>File</th><th>Lines</th><th>Module</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

function renderConstructorInjectionHtml() {
  const items = MODEL.constructorInjection || [];
  if (!items.length) return "";
  const spans = consecutiveRowspan(items, i => i.module);
  const rows = items.map((i, idx) => {
    const moduleCell = spans[idx] > 0 ? `<td rowspan="${spans[idx]}">${moduleLink(i.module)}</td>` : "";
    return `<tr><td>${fileLink(i.file, i.class)}</td>${moduleCell}<td>${i.fieldCount}</td></tr>`;
  }).join("");
  return `<section class="block"><h3>Constructor Injection (${items.length})</h3>
    <div class="empty-hint">Every class with <code class="path">@RequiredArgsConstructor</code> and 4+ injected fields.</div>
    <table class="simple rowspan-table"><thead><tr><th>Class</th><th>Module</th><th>Field count</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

function renderGodPackagesHtml() {
  const items = MODEL.godPackages || [];
  if (!items.length) return "";
  const rows = items.map(p =>
    `<tr><td><code class="path">${esc(p.package)}</code></td><td>${p.fileCount}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Largest Packages (${items.length})</h3>
    <div class="empty-hint">Packages (recursive) with more than 20 .java files.</div>
    <table class="simple"><thead><tr><th>Package</th><th>File count</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

// Same node/edge data renderModuleDependencyGraph() draws with Cytoscape, as plain Mermaid text --
// for the markdown export only (Cytoscape has no "serialize back to Mermaid" of its own). Mirrors
// the retired docs/architecture/01-module-dependencies.md diagram's own `graph LR` shape.
function buildModuleDependencyMermaid() {
  let out = "graph LR\n";
  moduleNodes.forEach(n => { out += `    ${n.id.replace(/-/g,"_")}["${n.id}"]\n`; });
  out += "\n";
  moduleNodes.forEach(n => {
    ["DEPENDS_ON_COMPILE","DEPENDS_ON_RUNTIME","DEPENDS_ON_OPTIONAL"].forEach(et => {
      (n.edges[et] || []).forEach(dep => {
        out += `    ${n.id.replace(/-/g,"_")} --> ${dep.replace(/-/g,"_")}\n`;
      });
    });
  });
  return out;
}

// Client-side only -- triggers a browser download of generated text. Nothing is written back to
// the repo; this is for pasting into a wiki/issue/chat, not a tracked file, so there is no
// staleness risk to guard against (contrast the retired approach of committing a generated block
// into docs/architecture/01-module-dependencies.md, which needed its own CI freshness gate --
// see docs/architecture/scripts/DECISIONS.md ADR-005). Shared by every "Export as Markdown" button in this tool.
function downloadMarkdown(filename, content) {
  const blob = new Blob([content], { type: "text/markdown" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

function exportModuleDependenciesMarkdown() {
  let md = `# Module Dependencies\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Module Dependencies.\n\n`;
  md += `## Dependency Graph\n\n\`\`\`mermaid\n${buildModuleDependencyMermaid()}\`\`\`\n`;
  md += `\n## Dependency Table\n\n| Module | Depends on |\n|---|---|\n`;
  moduleNodes.forEach(n => { md += `| ${n.id} | ${moduleDepsScopeText(n)} |\n`; });
  md += `\n## Key Observations\n\n`;
  MODULE_DEPENDENCY_KEY_OBSERVATIONS.forEach((o, i) => { md += `${i + 1}. ${o}\n`; });
  md += `\n## Module Versions\n\nAll modules are siblings with the same version: \`${MODEL.rootVersion}\`. Parent POM artifact: \`${MODEL.rootArtifactId}\`.\n`;
  downloadMarkdown("module-dependencies.md", md);
}

// Full snapshot of one module's page. ADRs are listed as title + a real file reference, not
// a copy of the ADR text -- same "resolve to the one source, never restate it" rule the on-page
// list follows.
function exportModuleMarkdown(id) {
  const n = byId[id];
  if (!n) return;
  let md = `# ${n.id}\n\n${n.description || ""}\n\n`;
  if (n.tables && n.tables.length) md += `## Tables owned\n\n${n.tables.map(t => `- ${t}`).join("\n")}\n\n`;
  const namedFileMdLine = it => `- ${it.name}${it.description ? ` — ${it.description}` : ""}`;
  if (n.entities && n.entities.length) md += `## Entities\n\n${n.entities.map(namedFileMdLine).join("\n")}\n\n`;
  if (n.keyServices && n.keyServices.length) md += `## Key services\n\n${n.keyServices.map(namedFileMdLine).join("\n")}\n\n`;
  if (n.contracts && n.contracts.length) md += `## Contracts (Port/Hook)\n\n${n.contracts.map(namedFileMdLine).join("\n")}\n\n`;
  md += `## Depends on (compile)\n\n${(n.edges.DEPENDS_ON_COMPILE||[]).map(d=>`- ${d}`).join("\n") || "None."}\n\n`;
  if ((n.edges.DEPENDS_ON_RUNTIME||[]).length) md += `## Depends on (runtime)\n\n${n.edges.DEPENDS_ON_RUNTIME.map(d=>`- ${d}`).join("\n")}\n\n`;
  if ((n.edges.DEPENDS_ON_OPTIONAL||[]).length) md += `## Depends on (optional)\n\n${n.edges.DEPENDS_ON_OPTIONAL.map(d=>`- ${d}`).join("\n")}\n\n`;
  md += `## Depended on by\n\n${(n.edges.DEPENDED_ON_BY||[]).map(d=>`- ${d}`).join("\n") || "Nothing (leaf module)."}\n\n`;
  md += `## Architectural decisions\n\n`;
  (n.intent || []).forEach(a => { md += `- **${a.id}: ${a.title}** — see \`${a.file}\`\n`; });
  md += `\n---\nEvidence: ${n.evidence[0].file}\n`;
  downloadMarkdown(`${n.id}.md`, md);
}

