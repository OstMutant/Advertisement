function spiMapNodesForSubsystem(subsystem) {
  const ifaceIds = new Set(MODEL.spiMap.details.filter(d => d.subsystem === subsystem).map(d => d.interface));
  const edges = MODEL.spiMap.edges.filter(e => ifaceIds.has(e.source) || ifaceIds.has(e.target));
  const nodeIds = new Set(ifaceIds);
  edges.forEach(e => { nodeIds.add(e.source); nodeIds.add(e.target); });
  const leafNodes = MODEL.spiMap.nodes.filter(n => !n.isGroup && nodeIds.has(n.id));
  const usedParents = new Set(leafNodes.map(n => n.parent));
  const groupNodes = MODEL.spiMap.nodes.filter(n => n.isGroup && usedParents.has(n.id));
  return { nodes: [...groupNodes, ...leafNodes], edges };
}

function renderSpiMapGraph(subsystem) {
  const filtered = spiMapNodesForSubsystem(subsystem);
  renderCytoscapeFromGraph(filtered.nodes, filtered.edges, "LR");
}

// Real link, readable class name as the visible text -- same "open the actual source, short
// label" pattern exportModuleMarkdown() already uses for ADRs, not a raw path dump.
function fileLink(file, label) {
  return `<a href="../../${esc(file)}" target="_blank">${esc(label)}</a>`;
}

// One pair of tables per subsystem (package prefix shown once per heading, same as the retired
// .md did) -- plus each subsystem's own editorial note (SPI_SUBSYSTEM_NOTE) when present. With
// the SPI Map diagram now split per subsystem, a specific subsystem restricts this to just its
// own tables, matching whichever diagram tab is on screen; omitted only by the (now unused)
// caller with no subsystem context. Split into two tables (Calls / Implemented By), one row per
// real edge rather than one row per interface with all edges stacked in a cell -- each row's
// `id` matches the diagram edge it represents, so clicking that edge (renderCytoscapeFromGraph's
// edge-tap handler) can scroll straight to it.
function spiRowId(kind, interfaceName, className) {
  return `spi-${kind}-${esc(interfaceName)}__${esc(className)}`;
}

// Same "real link, not plain text" treatment Code Quality's module column already uses --
// reused here since these tables repeat the same Interface/Purpose text once per caller/
// implementation otherwise (grouped away below via rowspan instead).
function moduleLink(moduleId) {
  return `<a class="module-link" onclick="navigate({screen:'module', id:'${esc(moduleId)}'})">${esc(moduleId)}</a>`;
}

// "(N/M methods used)" plus the full method list, unused ones dimmed and marked -- a real
// dead-code-in-the-contract signal, not just a count. N = distinct interface methods actually
// called by any real caller (ArchitectureMetricsExport.java's spiEdges), M = total methods
// declared on the interface. Everything null when no ArchUnit data was available for this
// interface (old regex fallback path) -- rendered as nothing rather than a misleading "0/0".
function spiMethodCountLine(d) {
  if (d.methodCount == null || d.usedCount == null) return "";
  const list = (d.allMethods || []).map(m => m.used
    ? `<div>${esc(m.name)}</div>`
    : `<div class="spi-method-unused">${esc(m.name)} (unused)</div>`
  ).join("");
  return `<div class="spi-methodcount">(${d.usedCount}/${d.methodCount} methods used)</div><div class="spi-method-list">${list}</div>`;
}

// Real (callerMethod, interfaceMethod) call-site pairs, one per line -- undefined/empty for the
// old regex fallback path (no method-level data at all) or a caller found via DI wiring only with
// no actual method call (doesn't happen with real ArchUnit data, but stays defensive).
function renderSpiCalls(calls) {
  if (!calls || !calls.length) return "";
  return `<div class="spi-calls">${calls.map(c => `<code>${esc(c.from)}() &rarr; #${esc(c.to)}()</code>`).join("<br>")}</div>`;
}

function renderSpiCallsRows(rows) {
  return rows.map(d => {
    const span = d.callers && d.callers.length ? d.callers.length : 1;
    const ifaceCell = `<td rowspan="${span}">${fileLink(d.file, d.interface)}${spiMethodCountLine(d)}</td>`;
    const purposeCell = `<td rowspan="${span}">${esc(d.purpose)}</td>`;
    if (!d.callers || !d.callers.length) {
      return `<tr><td><span class="empty-hint">no caller found</span></td>${ifaceCell}${purposeCell}</tr>`;
    }
    return d.callers.map((c, i) => {
      const callerCell = `<td>${fileLink(c.file, c.class)} (${moduleLink(c.module)})${renderSpiCalls(c.calls)}</td>`;
      const row = i === 0 ? `${callerCell}${ifaceCell}${purposeCell}` : callerCell;
      return `<tr id="${spiRowId("call", d.interface, c.class)}">${row}</tr>`;
    }).join("");
  }).join("");
}

function renderSpiImplementedByRows(rows) {
  return rows.map(d => {
    const span = d.implementations.length || 1;
    const ifaceCell = `<td rowspan="${span}">${fileLink(d.file, d.interface)}${spiMethodCountLine(d)}</td>`;
    const purposeCell = `<td rowspan="${span}">${esc(d.purpose)}</td>`;
    if (!d.implementations.length) {
      return `<tr>${ifaceCell}<td><span class="empty-hint">no implementation found</span></td>${purposeCell}</tr>`;
    }
    return d.implementations.map((impl, i) => {
      const implCell = `<td>${fileLink(impl.file, impl.class)} (${moduleLink(impl.module)})</td>`;
      const row = i === 0 ? `${ifaceCell}${implCell}${purposeCell}` : implCell;
      return `<tr id="${spiRowId("impl", d.interface, impl.class)}">${row}</tr>`;
    }).join("");
  }).join("");
}

function renderSpiSubsystemTables(subsystem) {
  const order = subsystem ? [subsystem] : MODEL.spiMap.subsystemOrder;
  return order.map(s => {
    const rows = MODEL.spiMap.details.filter(d => d.subsystem === s);
    if (!rows.length) return "";
    const note = MODEL.spiMap.subsystemNotes[s];
    const callCount = rows.reduce((n, d) => n + (d.callers ? d.callers.length : 0), 0);
    const implCount = rows.reduce((n, d) => n + d.implementations.length, 0);
    return `
      <div class="domain-group">
        <h3>${esc(MODEL.spiMap.subsystemLabels[s])} <code class="path">${esc(rows[0].package)}</code></h3>
        <h4>Calls (${callCount})</h4>
        <table class="simple rowspan-table"><thead><tr>
          <th title="Real caller class -- who actually calls the interface's method, from bytecode analysis, not who's merely allowed to" style="cursor:help">Caller</th>
          <th>Interface</th>
          <th>Purpose</th>
        </tr></thead><tbody>${renderSpiCallsRows(rows)}</tbody></table>
        <h4>Implemented By (${implCount})</h4>
        <table class="simple rowspan-table"><thead><tr>
          <th>Interface</th>
          <th title="Real class that implements this interface" style="cursor:help">Implementation</th>
          <th>Purpose</th>
        </tr></thead><tbody>${renderSpiImplementedByRows(rows)}</tbody></table>
        ${note ? `<div class="empty-hint" style="margin-top:8px">${mdInlineToHtml(note)}</div>` : ""}
      </div>`;
  }).join("");
}

function renderSpiMapExtrasHtml(subsystem) {
  const rows = MODEL.spiMap.details.filter(d => !subsystem || d.subsystem === subsystem);
  const callCount = rows.reduce((n, d) => n + (d.callers ? d.callers.length : 0), 0);
  const implCount = rows.reduce((n, d) => n + d.implementations.length, 0);
  return `
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a node to open its real <code class="path">.java</code> file. Click an edge to jump to its row below. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Dashed gray box</td><td>A module boundary (<code class="path">platform-commons</code>, a starter, <code class="path">marketplace-app</code>, <code class="path">marketplace-orchestrator</code> or <code class="path">marketplace-rest-api</code>)</td></tr>
        <tr><td class="scope-label">Blue rounded box</td><td>A Java interface, an implementation class, or a real caller class</td></tr>
        <tr><td class="scope-label">──calls──▶</td><td>A real caller class injects/calls the interface — arrow points from the caller to the interface</td></tr>
        <tr><td class="scope-label">──implemented by──▶</td><td>Arrow points from the interface to its implementation class</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>SPI Interface Details (${callCount} calls, ${implCount} implementations)</h3>${renderSpiSubsystemTables(subsystem)}</section>`;
}

// Parses one "**Heading:** location sentence. Example: example sentence." paragraph from the
// .claude/rules/platform-commons.md#spi-implementation-rules arch-embed into the pieces
// renderImplementationRulesHtml()/exportSpiMapMarkdown() both render as a Location/Example pair
// -- generic over the paragraph's own content, so a third implementation-kind paragraph added to
// that CLAUDE.md section later needs no code change here. Returns raw markdown (backticks intact,
// not HTML-escaped) -- each caller does its own target-format conversion.
function parseImplementationRuleParagraphs() {
  const raw = (MODEL.archEmbeds && MODEL.archEmbeds["spi-implementation-rules"]) || "";
  const stripPeriod = t => t.trim().replace(/\.$/, "");
  return raw.split(/\n\s*\n/).map(p => p.trim()).filter(Boolean).map(p => {
    const m = p.match(/^\*\*(.+?):\*\*\s*([\s\S]*)$/);
    if (!m) return null;
    const heading = m[1];
    const rest = m[2];
    const exIdx = rest.indexOf("Example:");
    const location = stripPeriod(exIdx >= 0 ? rest.slice(0, exIdx) : rest);
    const example = exIdx >= 0 ? stripPeriod(rest.slice(exIdx + "Example:".length)) : "";
    return { heading, location, example };
  }).filter(Boolean);
}

// Glossary content (SPI/Port/Hook) -- subsystem-independent, so rendered once at the bottom of
// the top-level Diagrams listing screen (next to Implementation Rules), not once per SPI Map
// subsystem tab -- renderSpiMapExtrasHtml() used to repeat the SPI paragraph on every tab before
// this. All three paragraphs are live-read .claude/rules/platform-commons.md arch-embeds (MODEL.archEmbeds).
// Parses one "**Heading** body text" glossary paragraph -- same bold-lead-in shape as
// parseImplementationRuleParagraphs() but without the required ":" before the closing "**"
// (a glossary heading is a short phrase/question, not a label).
function parseGlossaryEntry(raw) {
  const p = (raw || "").trim().replace(/\s+/g, " ");
  const m = p.match(/^\*\*(.+?)\*\*\s*(.*)$/);
  return m ? { heading: m[1], body: m[2].trim() } : null;
}

// Same adr-item visual style as renderImplementationRulesHtml() below -- one <strong> heading per
// glossary term, not a plain markdown paragraph.
function renderDiagramsOverviewHtml() {
  const toPathCode = t => esc(t).replace(/`([^`]+)`/g, '<code class="path">$1</code>');
  const items = ["spi-glossary", "port-glossary", "hook-glossary", "why-port-hook-glossary"]
    .map(k => MODEL.archEmbeds && MODEL.archEmbeds[k] ? parseGlossaryEntry(MODEL.archEmbeds[k]) : null)
    .filter(Boolean)
    .map(g => `<div class="adr-item"><strong>${esc(g.heading)}</strong>
        <div class="empty-hint">${toPathCode(g.body)}</div>
      </div>`).join("");
  return `
    <section class="block"><h3>Overview</h3>
      ${items}
      <div class="empty-hint">All cross-module extension points (Ports and Hooks) live in <code class="path">platform-commons</code> to decouple starters from marketplace-app — see ${sourceLink(".claude/rules/platform-commons.md")}'s "SPI Interface Naming" table for the authoritative direction/role definition of each suffix.</div>
    </section>`;
}

// Static, subsystem-independent content -- rendered once at the bottom of the top-level Diagrams
// listing screen instead of once per SPI Map subsystem tab (renderSpiMapExtrasHtml used to repeat
// this identical block on every tab). Text itself is read live from the
// .claude/rules/platform-commons.md#spi-implementation-rules arch-embed (MODEL.archEmbeds), not
// hand-copied here, so the two can't drift apart the way the old hardcoded version already had
// once (a stale CurrentActorHookImpl package path, fixed earlier this session).
function renderImplementationRulesHtml() {
  const toPathCode = t => esc(t).replace(/`([^`]+)`/g, '<code class="path">$1</code>');
  const items = parseImplementationRuleParagraphs()
    .map(it => `<div class="adr-item"><strong>${esc(it.heading.replace(/`/g, ""))}</strong>
        <ul class="info-list">
          <li>Location: ${toPathCode(it.location)}</li>
          ${it.example ? `<li>Example: ${toPathCode(it.example)}</li>` : ""}
        </ul>
      </div>`).join("");
  return `
    <section class="block"><h3>Implementation Rules</h3>
      ${items}
      <div class="empty-hint">All implementations follow these patterns — the core "pure delegation" rule is stated once, canonically, in ${sourceLink(".claude/rules/platform-commons.md")}'s "Hook and Port Implementation Rules" section, not restated here.</div>
    </section>`;
}

function buildSpiMapMermaid() {
  let out = "graph LR\n";
  const groups = {};
  MODEL.spiMap.nodes.forEach(n => {
    if (n.isGroup) return;
    (groups[n.parent] = groups[n.parent] || []).push(n);
  });
  Object.entries(groups).forEach(([g, ns]) => {
    out += `    subgraph ${g.replace(/-/g,"_")}["${g}"]\n`;
    ns.forEach(n => { out += `        ${n.id}["${n.label}"]\n`; });
    out += `    end\n`;
  });
  MODEL.spiMap.edges.forEach(e => { out += `    ${e.source} -->|${e.label || "implemented by"}| ${e.target}\n`; });
  return out;
}

function exportSpiMapMarkdown() {
  let md = `# SPI Map\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › SPI Map.\n\n`;
  md += `## Overview\n\nAll cross-module extension points (Ports and Hooks) live in \`platform-commons\` to decouple starters from marketplace-app. Suffixes encode call direction: \`*Port\` = marketplace -> starter; \`*Hook\` = starter -> marketplace. See .claude/rules/platform-commons.md's "SPI Interface Naming" table for the authoritative direction/role definition of each suffix.\n\n`;
  md += `## SPI Dependency Graph\n\n\`\`\`mermaid\n${buildSpiMapMermaid()}\`\`\`\n`;
  md += `\n## SPI Interface Details\n\n`;
  MODEL.spiMap.subsystemOrder.forEach(s => {
    const rows = MODEL.spiMap.details.filter(d => d.subsystem === s);
    if (!rows.length) return;
    md += `### ${MODEL.spiMap.subsystemLabels[s]} — \`${rows[0].package}\`\n\n`;
    md += `| Caller(s) | Interface | Direction | Implementation(s) | Purpose |\n|---|---|---|---|---|\n`;
    rows.forEach(d => {
      const callers = d.callers && d.callers.length ? d.callers.map(i => `${i.class} (${i.module})`).join("; ") : "no caller found";
      const impls = d.implementations.length ? d.implementations.map(i => `${i.class} (${i.module})`).join("; ") : "no implementation found";
      md += `| ${callers} | ${d.interface} | ${d.kind} | ${impls} | ${d.purpose} |\n`;
    });
    md += "\n";
    if (MODEL.spiMap.subsystemNotes[s]) md += `${MODEL.spiMap.subsystemNotes[s]}\n\n`;
  });
  md += `## Implementation Rules\n\nAll implementations follow these patterns (core "pure delegation" rule stated once, canonically, in .claude/rules/platform-commons.md's "Hook and Port Implementation Rules" section):\n\n`;
  parseImplementationRuleParagraphs().forEach(it => {
    md += `### ${it.heading}\n- Location: ${it.location}\n${it.example ? `- Example: ${it.example}\n` : ""}\n`;
  });
  downloadMarkdown("spi-map.md", md);
}

