// ── Diagrams screen: all diagrams render live -- no markdown counterpart for any of them. ───────
function renderDiagrams() {
  if (!view.groupKey) {
    let html = backButtonHtml();
    html += `<h2 class="screen-title">Diagrams</h2>
      <div class="screen-desc">${totalDiagramCount} diagrams, all rendered live from pom.xml/real Java source/real Liquibase changelogs -- no separately-maintained markdown source for any of them.</div>`;
    MODEL.diagramGroups.forEach(g => {
      const graphType = GRAPH_TYPE_KEYS.includes(g.key);
      html += `<div class="domain-group"><h3>${esc(g.label)} <code class="path">(${esc(g.file)})</code> ${graphType ? '<span class="badge ACTIVE">draggable</span>' : ""}</h3>
        ${g.description ? `<div class="empty-hint">${esc(g.description)}</div>` : ""}
        <div class="card-grid">`;
      g.diagrams.forEach((d, i) => {
        html += `<div class="card" onclick="navigate({screen:'diagrams',groupKey:'${g.key}',diagramIndex:${i}})">
          <div class="card-title">${esc(d.title || g.label)}</div>
        </div>`;
      });
      html += `</div></div>`;
    });
    html += renderDiagramsOverviewHtml();
    html += renderImplementationRulesHtml();
    document.getElementById("content").innerHTML = html;
    return;
  }

  const g = MODEL.diagramGroups.find(x => x.key === view.groupKey);
  const d = g.diagrams[view.diagramIndex];
  diagramCy = null;
  diagramCy2 = null;
  // Page-level nav (back/export) — belongs to "which screen am I on," sits above the title (same
  // placement as the Module screen's own back/export row). Zoom controls belong to the diagram
  // itself, not the page, so they render separately, right above the diagram box (see
  // zoomControlsHtml below) instead of sharing this row.
  let html = `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
      ${g.key === "01-module-dependencies" ? '<button onclick="exportModuleDependenciesMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "02-spi-map" ? '<button onclick="exportSpiMapMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "04-database-erd" ? '<button onclick="exportDbErdMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "bounded-contexts" ? '<button onclick="exportBoundedContextsMarkdown()">⭳ Export as Markdown</button>' : ""}
    </div>
    <h2 class="screen-title">Diagrams</h2>`;
  const zoomControlsHtml = `<div style="display:flex;justify-content:flex-end;margin-bottom:6px">
      <span class="zoom-controls">
        <button onclick="zoomDiagram(-0.15)">−</button>
        <span id="zoom-label">100%</span>
        <button onclick="zoomDiagram(0.15)">+</button>
        <button onclick="zoomDiagram(0)">reset</button>
      </span>
    </div>`;

  if (g.key === "01-module-dependencies") {
    const zoomControlsHtml2 = `<div style="display:flex;justify-content:flex-end;margin-bottom:6px">
      <span class="zoom-controls">
        <button onclick="zoomDiagram2(-0.15)">−</button>
        <span id="zoom-label-2">100%</span>
        <button onclick="zoomDiagram2(0.15)">+</button>
        <button onclick="zoomDiagram2(0)">reset</button>
      </span>
    </div>`;
    html += `<div class="diagram-note">${esc(g.label)} — Production Modules. An arrow points to the module being depended on (the arrowhead sits at the dependency, so marketplace-app → marketplace-mcp means the app depends on mcp); click an arrow to jump to that dependency in the table below. Rendered from the same module data the whole model is built from (domain-colored, click a node to open its module page) — drag a node to reposition it, drag empty canvas space to pan the view. Excludes <code class="path">integration-tests</code> (test-only, never shipped) -- see the second diagram below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:45vh"></div></div>
      <div class="diagram-note" style="margin-top:16px">${esc(g.label)} — Integration Tests. <code class="path">integration-tests</code> (Testcontainers repository tests, never shipped) and every starter it depends on to test them.</div>
      ${zoomControlsHtml2}
      <div class="diagram-wrap" id="diagram-cy-2-wrap" style="padding:0"><div id="diagram-cy-2" style="width:100%;height:45vh"></div></div>
      ${renderModuleDependencyExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    renderModuleDependencyGraph();
  } else if (g.key === "02-spi-map") {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered from real Java source (this subsystem's platform-commons/*/spi interfaces + their real callers/\`implements\` across starters/marketplace-app/marketplace-orchestrator), click a node to open its real .java file — drag a node to reposition it, drag empty canvas space to pan the view.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderSpiMapExtrasHtml(d.subsystem)}`;
    document.getElementById("content").innerHTML = html;
    renderSpiMapGraph(d.subsystem);
  } else if (g.key === "bounded-contexts") {
    // Rendered via Cytoscape (renderContextMapGraph(), same engine/interaction as Module
    // Dependencies and SPI Map -- see that function's own comment for why the original 2026
    // Cytoscape attempt was dropped (DECISIONS.md ADR-016) and why this flat-node version doesn't
    // hit the same failure mode). Domain contents and relationships are generated live from
    // MODEL.boundedContexts (real code signals -- see the Overview section below) -- no markdown
    // source at all, same "live, not a second copy" pattern as 01/02/04 (see DECISIONS.md
    // ADR-019/ADR-020).
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. ${esc(d.description || "")} Click a domain to open its real module page, click an arrow to jump to its evidence row below. Drag to pan, or use the zoom controls below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderBoundedContextsExtrasHtml(d.category)}`;
    document.getElementById("content").innerHTML = html;
    renderContextMapGraph(d.category);
  } else if (g.key === "04-database-erd") {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered live from the real Liquibase changelogs (table/column/type/constraints/FKs/indexes/remarks all come from the changelog XML itself, not a hand-maintained copy). Tables sit in a frame per module; drag a table to reposition it, drag empty canvas to pan, or use the zoom controls. Click a table to jump to its schema below, or an arrow to jump to the column that holds the reference. Solid lines are real foreign keys; dotted lines are relationships this codebase deliberately leaves unconstrained at the SQL level (enforced at the application layer instead — see the notes below); hover a table to see the labels of its dotted lines.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderDbErdExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    renderDbErdGraph();
  }
}

function render() {
  renderBreadcrumb();
  if (view.screen === "module") renderModule();
  else if (view.screen === "pipelines") renderPipelines();
  else if (view.screen === "backlog") renderBacklog();
  else if (view.screen === "diagrams") renderDiagrams();
  else if (view.screen === "adrs") renderAdrs();
  else if (view.screen === "codequality") renderCodeQuality();
  else renderSystem();
}

