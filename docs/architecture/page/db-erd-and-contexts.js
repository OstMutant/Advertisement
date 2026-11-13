// ── Database ERD: the diagram and the table-schema detail both read MODEL.dbErd ──────────────
function simplifyDbType(type) {
  if (!type) return "unknown";
  const t = type.toUpperCase();
  if (t.startsWith("BIGSERIAL") || t.startsWith("BIGINT")) return "bigint";
  if (t.startsWith("SERIAL") || t.startsWith("INT")) return "int";
  if (t.startsWith("VARCHAR") || t.startsWith("TEXT")) return t.includes("[]") ? "text_array" : "varchar";
  if (t.startsWith("TIMESTAMP")) return "timestamp";
  if (t.startsWith("JSONB") || t.startsWith("JSON")) return "jsonb";
  if (t.startsWith("BOOLEAN")) return "boolean";
  return t.toLowerCase().replace(/[^a-z0-9_]/g, "_") || "unknown";
}

function buildDbErdMermaidSource() {
  let out = "erDiagram\n";
  MODEL.dbErd.tables.forEach(t => {
    out += `    ${t.name.toUpperCase()} {\n`;
    t.columns.forEach(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const keys = [];
      if (c.primaryKey || isCompositePk) keys.push("PK");
      if (c.foreignKeyTable) keys.push("FK");
      if (c.unique && !c.primaryKey) keys.push("UK");
      const keyStr = keys.join(",");
      const comment = c.remarks ? ` "${c.remarks.replace(/"/g, "'").slice(0, 60)}"` : "";
      out += `        ${simplifyDbType(c.type)} ${c.name}${keyStr ? " " + keyStr : ""}${comment}\n`;
    });
    out += `    }\n`;
  });
  // Real FKs first (solid line -- "identifying" relationship in Mermaid's erDiagram notation).
  MODEL.dbErd.tables.forEach(t => {
    t.foreignKeys.forEach(fk => {
      out += `    ${fk.refTable.toUpperCase()} ||--o{ ${t.name.toUpperCase()} : "${fk.column}"\n`;
    });
    t.columns.forEach(c => {
      if (c.foreignKeyTable) out += `    ${c.foreignKeyTable.toUpperCase()} ||--o{ ${t.name.toUpperCase()} : "${c.name}"\n`;
    });
  });
  // Conceptual relationships derived from the remarks markers -- no real FK, dotted line ("non-identifying").
  MODEL.dbErd.conceptualRelationships.forEach(r => {
    out += `    ${r.from} ||..o{ ${r.to} : "${r.label.replace(/"/g, "'")}"\n`;
  });
  return out;
}

// Live from MODEL.boundedContexts (real @Table entities/*Service classes/tables/SPI ports per
// domain, real relationships detected from AuditTimelineRowRenderer.LABELED_ENTITY_TYPES,
// AuditActivityEnrichHook entityType() declarations, and other concrete signals -- see
// bounded_contexts_json() for exactly what backs each fact).
//
// Rendered via Cytoscape (renderModuleDependencyGraph()'s own engine), not Mermaid -- ADR-016
// documented a real, structural reason the original Cytoscape+dagre attempt was dropped for this
// diagram: nesting each domain's entities/services/tables/ports as *compound-child* nodes created
// cycles spanning compound boundaries (a domain's own child linking back to a sibling domain),
// which dagre's compound-aware ranking cannot resolve -- it collapsed the whole graph onto one
// column. This version sidesteps that failure mode at the root instead of retrying it: domains are
// flat, non-compound nodes (matching Module Dependencies' own shape) with no item children on the
// canvas at all -- entities/services/tables/ports live only in the "Domain Contents" table below
// and each domain's own module page (one click away), never as canvas nodes. A same-shape 2-node
// cycle still exists in the real relationships today (Orchestrator <-> Audit), but a cycle between
// two flat siblings is the ordinary case dagre handles fine -- it was never the failure mode ADR-016
// diagnosed. Gets the same native pan/zoom/click/drag interaction every other Cytoscape diagram
// already has for free.
// category: one of BC_CATEGORY_ORDER's keys ("orchestration"/"hooks"/"derived"), or
// omitted/falsy for the full, unfiltered graph (used only by the Markdown export -- every live tab
// always passes its own category). Node set is restricted to domains actually touched by the
// filtered edges, not every domain every time -- that's the real decluttering this split exists for.
function buildContextMapGraph(category) {
  const domains = MODEL.boundedContexts.domains.filter(d => d.id !== "Shared");
  const moduleOf = {};
  MODEL.boundedContexts.domains.forEach(d => { moduleOf[d.id] = d.module; });
  const edges = MODEL.boundedContexts.relationships
    .filter(r => r.label !== "decouples" && (!category || r.category === category))
    .map(r => ({
      source: moduleOf[r.from] || r.from, target: moduleOf[r.to] || r.to,
      label: r.label, dashed: r.dashed,
      // Carried through purely so the diagram's edge-tap handler can find this edge's real
      // Relationships-table row -- rowId must match bcRelRowId()'s own id exactly, since the
      // Cytoscape node ids above are module ids, not the domain ids the table is keyed by.
      rowId: bcRelRowId(r.from, r.to, r.label)
    }));
  const involved = new Set(edges.flatMap(e => [e.source, e.target]));
  const nodes = domains
    .filter(d => involved.has(d.module))
    .map(d => {
      const mn = moduleNodes.find(n => n.id === d.module);
      return { id: d.module, label: d.label, domain: mn ? mn.domain : null };
    });
  return { nodes, edges: edges.filter(e => nodes.some(n => n.id === e.source) && nodes.some(n => n.id === e.target)) };
}

function renderContextMapGraph(category) {
  const g = buildContextMapGraph(category);
  const els = [
    ...g.nodes.map(n => ({
      data: { id: n.id, label: n.label, domain: n.domain },
      style: { "background-color": domainColor(n.domain) }
    })),
    ...g.edges.map((e, i) => ({
      data: { id: "e" + i, source: e.source, target: e.target, label: e.label || "", dashed: e.dashed, rowId: e.rowId }
    }))
  ];
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  diagramCy = cytoscape({
    container: document.getElementById("diagram-cy"),
    elements: els,
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 11, "color": "#fff", "text-valign": "center", "text-halign": "center",
          "border-width": 0, "shape": "round-rectangle", "width": "label", "height": "label",
          "padding": "12px", "text-wrap": "wrap", "text-max-width": 130, "font-weight": 600
        } },
      { selector: "edge", style: {
          "width": 1.5, "line-color": "#a0aec0", "target-arrow-color": "#a0aec0",
          "target-arrow-shape": "triangle", "curve-style": "bezier",
          "font-size": 9, "label": "data(label)", "color": "#4a5568",
          "text-background-color": "#f7f8fa", "text-background-opacity": 0.9, "text-background-padding": 2
        } },
      { selector: "edge[?dashed]", style: { "line-style": "dashed" } }
    ],
    layout: hasDagre
      ? { name: "dagre", rankDir: "TB", nodeSep: 30, rankSep: 80, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20 }
  });
  diagramCy.on("zoom", () => {
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = Math.round(diagramCy.zoom() * 100) + "%";
  });
  diagramCy.on("tap", "node", e => navigate({ screen: "module", id: e.target.id() }));
  diagramCy.on("tap", "edge", e => jumpToRelationshipRow(e.target.data("rowId")));
  diagramCy.nodes().style("cursor", "pointer");
  diagramCy.edges().style("cursor", "pointer");
}

// Scrolls to and briefly flashes the Relationships-table row a diagram edge maps to -- the
// evidence (which real method/class the arrow represents) lives in that row, not in a second copy
// on the canvas itself.
function jumpToRelationshipRow(rowId) {
  const row = document.getElementById(rowId);
  if (!row) return;
  row.scrollIntoView({ behavior: "smooth", block: "center" });
  row.classList.add("rel-row-flash");
  setTimeout(() => row.classList.remove("rel-row-flash"), 1500);
}

// Kept only for the Markdown export (a static text document has none of the live interaction
// concerns above) -- flat nodes/edges matching the live Cytoscape graph's own shape, not the old
// compound-subgraph-with-items version.
function buildContextMapMermaidSource() {
  const g = buildContextMapGraph();
  let out = "graph TB\n";
  g.nodes.forEach(n => { out += `    ${n.id}["${esc(n.label)}"]\n`; });
  g.edges.forEach(e => {
    const arrow = e.dashed ? "-.->" : "-->";
    out += `    ${e.source} ${arrow}|${e.label.replace(/\|/g, "/")}| ${e.target}\n`;
  });
  return out;
}

// Stable, collision-safe id for a relationship's <tr> -- shared between the row itself and the
// diagram's edge-tap handler (renderContextMapGraph()) so clicking an arrow can scroll straight to
// its evidence instead of leaving the reader to hunt for the matching row by eye.
function bcRelRowId(from, to, label) {
  return "rel-" + [from, to, label].join("|").replace(/[^a-zA-Z0-9]+/g, "-");
}

// Readable name as the link text, real file underneath -- same "short label, real link" pattern
// fileLink() already uses for SPI Map. Items with no file (Shared's plain count summaries)
// render as plain text, not a broken link.
function bcItemLink(item) {
  return item.file ? `<a class="module-link" href="../../${esc(item.file)}" target="_blank">${esc(item.name)}</a>` : esc(item.name);
}

// Plain-English meaning of each relationship label -- shown as a tooltip on the Label cell, since
// "calls"/"audited via"/"can have" read as cryptic shorthand without it. Keys match
// BC_LABEL_PAYLOAD in the bash generator exactly (both describe the same fixed label set).
const BC_LABEL_MEANING = {
  "calls": "The source domain directly invokes a method on the target domain's own Port interface (a real method call, not just a Maven dependency).",
  "audited via": "The source domain's entities get captured as audit-log snapshots through the Audit domain.",
  "can have": "The source domain's entities may carry attachment/media data, resolved through the Attachment domain at read time.",
  "calls back via Hook implementations": "Reverse direction: the source domain (a starter) calls a Hook interface; the target domain supplies the real implementation."
};

// Category display label for the Markdown export's Category column -- keys match
// BC_CATEGORY_LABEL in the bash generator exactly (both describe the same fixed category set).
const BC_CATEGORY_LABEL_JS = {
  "orchestration": "Service Calls (BFF)",
  "hooks": "Hook Callbacks",
  "derived": "Derived Facts"
};

// Finds a real "<module>/src/main/java/.../ClassName.java[:line]" path inside a free-text evidence
// string and wraps just that substring in a real link to the file -- evidence text is a mix of
// prose and real paths (e.g. "X.java:82:      someCall()" or "N classes import org.ost.orchestrator.*"),
// so this only linkifies the part that's actually a path, leaving the rest as plain text.
function linkifyEvidence(text) {
  return esc(text).replace(/([\w.-]+\/src\/main\/java\/[\w./]+\.java)(:\d+)?/g,
    (m, path, line) => `<a href="../../${path}" target="_blank">${path}${line || ""}</a>`);
}

// activeCategory: the current tab's category key -- Domain Contents/Overview/Legend stay
// unfiltered (same domains/text every tab, same precedent as SPI Map's Overview/Legend
// staying global while only the interface-details table filters per subsystem); only the
// Relationships table filters to the active tab's edges.
function renderBoundedContextsExtrasHtml(activeCategory) {
  const category = (label, items) => items.length
    ? `<div><span class="scope-label">${label}</span> ${items.map(bcItemLink).join(", ")}</div>` : "";
  const relationships = MODEL.boundedContexts.relationships
    .filter(r => r.label !== "decouples" && r.category === activeCategory);
  // Same domain set the diagram itself draws for this tab (buildContextMapGraph's own "involved"
  // set) -- Domain Contents lists only the domains this category's edges actually touch, not every domain
  // every tab, so the two sections never disagree about what's "on screen" for this category.
  const involvedIds = new Set(relationships.flatMap(r => [r.from, r.to]));
  const domains = MODEL.boundedContexts.domains.filter(d => d.id !== "Shared" && involvedIds.has(d.id));
  const domainRows = domains.map(d => {
    const body = [
      category("Entities", d.entities), category("Services", d.services),
      category("Tables", d.tables), category("Ports", d.ports)
    ].filter(Boolean).join("");
    return `<div class="adr-item"><strong>${esc(d.label)}</strong> <span class="scope-label">${moduleLink(d.module)}</span>
      ${body || `<div class="empty-hint">(no directly-owned entities/services/tables)</div>`}
    </div>`;
  }).join("");
  // Grouped by label, one table per label -- same shape as SPI Map's Interface-grouped split
  // Most labels have exactly one meaning for this whole category's tab, so a
  // repeated Label column added nothing; the heading carries the label + its hover-meaning instead.
  const byLabel = new Map();
  relationships.forEach(r => {
    if (!byLabel.has(r.label)) byLabel.set(r.label, []);
    byLabel.get(r.label).push(r);
  });
  const relTables = [...byLabel.entries()].map(([label, rows]) => {
    const meaning = BC_LABEL_MEANING[label] || "";
    const spans = consecutiveRowspan(rows, r => r.payload);
    const body = rows.map((r, idx) => {
      const rowId = bcRelRowId(r.from, r.to, r.label);
      const payloadCell = spans[idx] > 0 ? `<td rowspan="${spans[idx]}">${esc(r.payload)}</td>` : "";
      return `<tr id="${rowId}"><td>${esc(r.from)} → ${esc(r.to)}</td>${payloadCell}<td>${linkifyEvidence(r.evidence)}</td></tr>`;
    }).join("");
    return `<h4 title="${esc(meaning)}" style="cursor:help">${esc(label)} (${rows.length})</h4>
      <table class="simple rowspan-table"><thead><tr><th>Relationship</th><th>What crosses (payload type)</th><th>Evidence</th></tr></thead><tbody>${body}</tbody></table>`;
  }).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">Domain grouping (entities/services/tables/ports per box) is extracted live from real <code class="path">@Table</code> classes, <code class="path">*Service</code> classes, Liquibase tables, and SPI interface <code class="path">implements</code> relationships. Every relationship below is backed by a real, named code signal, all "extracted" (grep/AST match, never guessed or hand-typed) — see the Evidence column, or hover a Label cell for what it actually means. Not shown on the diagram: <code class="path">platform-commons</code> (the "Shared" module) is a compile-time dependency of every domain and Orchestrator — a real fact, but the same one repeated for every domain, so it's stated once here as text instead of drawn as 7 identical edges.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a domain box to open its real module page. Click an arrow to jump to its row in the Relationships table below. Drag empty canvas space to pan. This tab shows only the domains and relationships belonging to the current category — see the diagram-note above for what that category actually means.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Box</td><td>A bounded context / domain — click to open its real module page. Its entities/services/tables/ports are listed in Domain Contents below, not on the canvas</td></tr>
        <tr><td class="scope-label">──▶ (solid line)</td><td>A real business relationship backed by a concrete code signal — click the arrow to jump to its evidence in the Relationships table below</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>Domain Contents (${domains.length})</h3>${domainRows}</section>
    <section class="block"><h3>Relationships (${relationships.length}) — all "extracted" (real code signal, not hand-typed)</h3>
      <div class="empty-hint">Hover a heading for what that label means. Evidence links open the real file where the relationship was found.</div>
      ${relTables}
    </section>`;
}

function exportBoundedContextsMarkdown() {
  let md = `# Bounded Contexts — Context Map\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Bounded Contexts. The live version splits relationships across 3 tabs by category (Service Calls / Hook Callbacks / Derived Facts); this export keeps all of them in one document, with a Category column.\n\n`;
  md += `## Context Map\n\n\`\`\`mermaid\n${buildContextMapMermaidSource()}\`\`\`\n\n`;
  md += `platform-commons ("Shared") is a compile-time dependency of every domain and Orchestrator -- not drawn above (same fact repeated for every domain), stated once here instead.\n\n`;
  md += `## Domain Contents\n\n`;
  MODEL.boundedContexts.domains.forEach(d => {
    md += `### ${d.label} — \`${d.module}\`\n\n`;
    const cat = (label, items) => { if (items.length) md += `**${label}:** ${items.map(i => i.name).join(", ")}\n\n`; };
    cat("Entities", d.entities); cat("Services", d.services); cat("Tables", d.tables); cat("Ports", d.ports);
  });
  md += `## Relationships\n\nAll rows below are "extracted" -- backed by a real grep/AST code signal, never guessed.\n\n| Relationship | Category | Label | What crosses | Evidence |\n|---|---|---|---|---|\n`;
  MODEL.boundedContexts.relationships.filter(r => r.label !== "decouples").forEach(r => {
    md += `| ${r.from} -> ${r.to} | ${BC_CATEGORY_LABEL_JS[r.category] || r.category} | ${r.label} | ${r.payload} | ${r.evidence} |\n`;
  });
  downloadMarkdown("bounded-contexts.md", md);
}

// Tables as nodes named after the real table (with the owning module), a solid edge per real foreign
// key labelled with its column, a dashed edge per relationship the codebase leaves unconstrained;
// a click on a table jumps to its schema section below (id="db-table-<real_name>", set in
// renderDbErdTableSchemas()).
function renderDbErdGraph() {
  const byUpper = {};
  MODEL.dbErd.tables.forEach(t => { byUpper[t.name.toUpperCase()] = t; });
  const moduleOf = t => String(t.module || "unknown").replace(/-spring-boot-starter$/, "");
  const modules = [...new Set(MODEL.dbErd.tables.map(moduleOf))];
  const nodes = [
    ...modules.map(m => ({ id: "module:" + m, label: m, isGroup: true })),
    ...MODEL.dbErd.tables.map(t => ({ id: t.name.toUpperCase(), label: t.name, parent: "module:" + moduleOf(t) }))
  ];
  const seen = new Set();
  const edges = [];
  const addFk = (refTable, table, column) => {
    const key = refTable.toUpperCase() + ">" + table.toUpperCase() + ">" + column;
    if (seen.has(key)) return;
    seen.add(key);
    edges.push({ source: refTable.toUpperCase(), target: table.toUpperCase(), label: column, dashed: false, rowId: "db-col-" + table + "-" + column });
  };
  MODEL.dbErd.tables.forEach(t => {
    t.foreignKeys.forEach(fk => addFk(fk.refTable, t.name, fk.column));
    t.columns.forEach(c => { if (c.foreignKeyTable) addFk(c.foreignKeyTable, t.name, c.name); });
  });
  MODEL.dbErd.conceptualRelationships.forEach(r => {
    // The arrow leads to the column that holds the reference; fall back to the table when that column is not in the model.
    const holder = byUpper[r.to];
    const rowId = holder && holder.columns.some(c => c.name === r.column) ? "db-col-" + holder.name + "-" + r.column : holder && "db-table-" + holder.name;
    edges.push({ source: r.from, target: r.to, label: r.label, dashed: true, deferred: true, rowId });
  });
  const cy = renderCytoscapeFromGraph(nodes, edges.filter(e => byUpper[e.source] && byUpper[e.target]), "LR");
  cy.nodes(":childless").style("cursor", "pointer");
  // Module frames in a grid of three, each keeping its own inner layout; dagre alone stacks them in one column.
  let x = 0, y = 0, rowHeight = 0, col = 0;
  cy.nodes(":parent").forEach(frame => {
    const box = frame.boundingBox();
    const dx = x - box.x1, dy = y - box.y1;
    frame.descendants().forEach(n => n.position({ x: n.position().x + dx, y: n.position().y + dy }));
    x += box.w + 80;
    rowHeight = Math.max(rowHeight, box.h);
    if (++col === 3) { col = 0; x = 0; y += rowHeight + 80; rowHeight = 0; }
  });
  cy.fit(undefined, 30);
  // The label of a dotted (no real foreign key) relationship shows only while its table is hovered.
  cy.style().selector("edge[?dashed]").style({ "label": "" }).selector("edge.hover").style({ "label": "data(label)", "z-index": 10 }).update();
  // A click on an arrow centres the column that holds the reference in the schema section below.
  cy.edges().style("cursor", "pointer");
  cy.on("tap", "edge", e => jumpToDbErdElement(e.target.data("rowId")));
  cy.on("mouseover", "node", e => e.target.connectedEdges().addClass("hover"));
  cy.on("mouseout", "node", e => e.target.connectedEdges().removeClass("hover"));
  cy.on("tap", "node", e => {
    const table = byUpper[e.target.id()];
    const el = table && document.getElementById("db-table-" + table.name);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

// Scrolls to and flashes one element of the Database ERD page, an arrow target or a link in a Links cell.
function jumpToDbErdElement(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  el.classList.remove("row-flash");
  void el.offsetWidth;
  el.classList.add("row-flash");
}

document.addEventListener("click", e => {
  const link = e.target.closest("[data-jump]");
  if (!link) return;
  e.preventDefault();
  jumpToDbErdElement(link.dataset.jump);
});

function dbErdRealName(upper) {
  const t = MODEL.dbErd.tables.find(x => x.name.toUpperCase() === upper);
  return t ? t.name : upper.toLowerCase();
}

// Where a column points to without a real foreign key ("to") and which columns point at it ("from").
function dbErdColumnLinks(table, column) {
  const upper = table.name.toUpperCase();
  const relationships = MODEL.dbErd.conceptualRelationships;
  return [
    ...relationships.filter(r => r.to === upper && r.column === column.name)
      .map(r => ({ dir: "to", table: dbErdRealName(r.from), column: r.refColumn })),
    ...relationships.filter(r => r.from === upper && r.refColumn === column.name)
      .map(r => ({ dir: "from", table: dbErdRealName(r.to), column: r.column }))
  ];
}

function renderDbErdTableSchemas() {
  return MODEL.dbErd.tables.map(t => {
    const rows = t.columns.map(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const constraints = [];
      if (c.primaryKey || isCompositePk) constraints.push("PK");
      if (c.foreignKeyTable) constraints.push(`FK → ${c.foreignKeyTable}(${c.foreignKeyColumn})`);
      if (c.unique) constraints.push("UNIQUE");
      if (!c.nullable) constraints.push("NOT NULL");
      const links = dbErdColumnLinks(t, c)
        .map(l => `${l.dir} <a href="#" data-jump="db-col-${esc(l.table)}-${esc(l.column)}">${esc(l.table)}.${esc(l.column)}</a>`).join("<br>");
      return `<tr id="db-col-${esc(t.name)}-${esc(c.name)}"><td><code class="path">${esc(c.name)}</code></td><td>${esc(c.type)}</td><td class="scope-label">${esc(constraints.join(", "))}</td><td>${esc(c.remarks)}</td><td>${links}</td></tr>`;
    }).join("");
    const fkItems = t.foreignKeys.map(fk =>
      `<li><code class="path">${esc(fk.column)}</code> → <code class="path">${esc(fk.refTable)}(${esc(fk.refColumn)})</code> ON DELETE ${esc(fk.onDelete)}</li>`
    ).join("");
    const idxItems = t.indexes.map(i => {
      if (i.check) return `<li>CHECK <code class="path">${esc(i.name)}</code>: <code class="path">${esc(i.check)}</code></li>`;
      const cols = (i.columns || []).join(", ");
      return `<li><code class="path">${esc(i.name)}</code> (${esc(cols)})${i.unique ? " UNIQUE" : ""}${i.using ? ` USING ${esc(i.using)}` : ""}${i.where ? ` ${esc(i.where)}` : ""}</li>`;
    }).join("");
    return `
      <div class="domain-group" id="db-table-${esc(t.name)}">
        <h3>${esc(t.name)} <span class="scope-label">${esc(t.module)}</span></h3>
        <div class="empty-hint">${sourceLink(t.file)}${t.remarks ? " — " + mdInlineToHtml(t.remarks) : ""}</div>
        <table class="simple"><thead><tr><th>Column</th><th>Type</th><th>Constraints</th><th>Notes</th><th>Links</th></tr></thead><tbody>${rows}</tbody></table>
        ${fkItems ? `<div class="adr-item"><strong>Foreign Keys</strong><ul class="info-list">${fkItems}</ul></div>` : ""}
        ${idxItems ? `<div class="adr-item"><strong>Indexes</strong><ul class="info-list">${idxItems}</ul></div>` : ""}
      </div>`;
  }).join("");
}

function renderDbErdExtrasHtml() {
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">All tables are created via Liquibase migrations. Each starter owns its own changelog under its own <code class="path">db/*-changelog/</code> directory — no shared migrations between modules (the application's own <code class="path">db/changelog</code> master is empty). Column/table descriptions live directly in each changelog's own <code class="path">remarks=</code> attribute (single source of truth, see root CLAUDE.md's "Database Changes" guideline) — not duplicated in a separate markdown file.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a table in the diagram above to jump to its schema below, or an arrow to jump to the column that holds the reference.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:110px">PK</td><td>Primary key</td></tr>
        <tr><td class="scope-label">FK</td><td>Foreign key — real, enforced constraint in the database</td></tr>
        <tr><td class="scope-label">UK</td><td>Unique constraint</td></tr>
        <tr><td class="scope-label">──── (solid line)</td><td>Real foreign key between the two tables</td></tr>
        <tr><td class="scope-label">┄┄┄┄ (dotted line)</td><td>Relationship this codebase deliberately leaves unconstrained at the SQL level — no real FK, enforced at the application layer instead</td></tr>
        <tr><td class="scope-label">‖ at the line's end</td><td>"Exactly one" — the table at that end has exactly one matching row (crow's-foot notation)</td></tr>
        <tr><td class="scope-label">○&lt; at the line's end</td><td>"Zero or many" — the table at that end has zero or more matching rows (crow's-foot notation)</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>Tables (${MODEL.dbErd.tables.length})</h3>${renderDbErdTableSchemas()}</section>`;
}

function exportDbErdMarkdown() {
  let md = `# Database ERD\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Database ERD.\n\n`;
  md += `## Overview\n\nAll tables are created via Liquibase migrations. Each starter owns its own changelog under its own \`db/*-changelog/\` directory -- no shared migrations between modules (the application's own \`db/changelog\` master is empty).\n\n`;
  md += `## Entity Relationship Diagram\n\n\`\`\`mermaid\n${buildDbErdMermaidSource()}\`\`\`\n\n`;
  md += `## Table Schemas\n\n`;
  MODEL.dbErd.tables.forEach(t => {
    md += `### ${t.name}\n\n**Module:** \`${t.module}\`  \n**Changelog:** \`${t.file}\`\n\n`;
    if (t.remarks) md += `${t.remarks}\n\n`;
    md += `| Column | Type | Constraints | Notes | Links |\n|---|---|---|---|---|\n`;
    t.columns.forEach(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const constraints = [];
      if (c.primaryKey || isCompositePk) constraints.push("PK");
      if (c.foreignKeyTable) constraints.push(`FK -> ${c.foreignKeyTable}(${c.foreignKeyColumn})`);
      if (c.unique) constraints.push("UNIQUE");
      if (!c.nullable) constraints.push("NOT NULL");
      const links = dbErdColumnLinks(t, c).map(l => `${l.dir} ${l.table}.${l.column}`).join("<br>");
      md += `| ${c.name} | ${c.type} | ${constraints.join(", ")} | ${c.remarks} | ${links} |\n`;
    });
    md += "\n";
    if (t.foreignKeys.length) {
      md += `**Foreign Keys:**\n\n`;
      t.foreignKeys.forEach(fk => { md += `- ${fk.column} -> ${fk.refTable}(${fk.refColumn}) ON DELETE ${fk.onDelete}\n`; });
      md += "\n";
    }
    if (t.indexes.length) {
      md += `**Indexes:**\n\n`;
      t.indexes.forEach(i => {
        if (i.check) { md += `- CHECK ${i.name}: ${i.check}\n`; return; }
        md += `- ${i.name} (${(i.columns||[]).join(", ")})${i.unique ? " UNIQUE" : ""}${i.using ? ` USING ${i.using}` : ""}${i.where ? ` ${i.where}` : ""}\n`;
      });
      md += "\n";
    }
  });
  downloadMarkdown("database-erd.md", md);
}

function zoomDiagram(delta) {
  if (diagramCy) {
    if (delta === 0) { diagramCy.fit(undefined, 20); return; }
    diagramCy.zoom({ level: diagramCy.zoom() + delta, renderedPosition: { x: diagramCy.width()/2, y: diagramCy.height()/2 } });
    return;
  }
}

