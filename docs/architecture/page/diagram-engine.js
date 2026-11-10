const GRAPH_TYPE_KEYS = ["01-module-dependencies", "02-spi-map", "bounded-contexts"];

// Parses the Mermaid flowchart dialect this repository uses -- graph/flowchart header, subgraph,
// rectangle / decision {..} / cylinder [(..)] nodes, plain, labelled and dashed edges, node chains
// -- into the node/edge shape renderCytoscapeFromGraph() draws.
function parseFlowchart(source) {
  const nodes = new Map();
  const edges = [];
  const stack = [];
  let direction = "TB";
  const clean = s => s.replace(/<br\s*\/?>/gi, " ").trim();
  const ensure = id => {
    if (!nodes.has(id)) nodes.set(id, { id, label: id, parent: stack[stack.length - 1] || null });
    return nodes.get(id);
  };
  const shapes = [["[(", ")]", "barrel"], ["[", "]", "rectangle"], ["{", "}", "diamond"]];
  // Reads one node reference (an id, optionally followed by its label in one of the shapes) at pos.
  const readNode = (line, pos) => {
    const idm = /^\w+/.exec(line.slice(pos));
    if (!idm) return null;
    const node = ensure(idm[0]);
    let p = pos + idm[0].length;
    for (const [open, close, shape] of shapes) {
      if (!line.startsWith(open, p)) continue;
      const start = p + open.length;
      const end = line.indexOf(close, start);
      if (end === -1) break;
      let label = line.slice(start, end);
      if (label.length > 1 && label.startsWith('"') && label.endsWith('"')) label = label.slice(1, -1);
      node.label = clean(label);
      node.shape = shape;
      p = end + close.length;
      break;
    }
    return { id: node.id, next: p };
  };
  const edgeRe = /^\s*(?:(-\.->|-->)(?:\|([^|]*)\|)?|--\s+(.+?)\s+-->)\s*/;
  source.split("\n").map(l => l.trim()).filter(Boolean).forEach(line => {
    let m;
    if ((m = /^(?:graph|flowchart)\b\s*(TB|TD|BT|LR|RL)?/.exec(line))) {
      if (m[1]) direction = m[1] === "TD" ? "TB" : m[1];
      return;
    }
    if (/^(%%|direction\b|style\b|linkStyle\b|classDef\b|class\b)/.test(line)) return;
    if ((m = /^subgraph\s+(\w+)(?:\[(.*)\])?\s*$/.exec(line))) {
      let label = m[2] || m[1];
      if (label.length > 1 && label.startsWith('"') && label.endsWith('"')) label = label.slice(1, -1);
      nodes.set(m[1], { id: m[1], label: clean(label), parent: stack[stack.length - 1] || null, isGroup: true });
      stack.push(m[1]);
      return;
    }
    if (line === "end") { stack.pop(); return; }
    let cur = readNode(line, 0);
    while (cur) {
      const em = edgeRe.exec(line.slice(cur.next));
      if (!em) break;
      const nxt = readNode(line, cur.next + em[0].length);
      if (!nxt) break;
      edges.push({
        source: cur.id, target: nxt.id,
        label: clean(em[2] !== undefined ? em[2] : (em[3] || "")),
        dashed: em[1] === "-.->"
      });
      cur = nxt;
    }
  });
  return { nodes: [...nodes.values()], edges, direction };
}

let diagramCy = null;
let diagramCy2 = null; // Module Dependencies' second, own diagram (Integration Tests) -- see renderModuleDependencyGraph()

// Shared by every compound-node (group/parent) Cytoscape render -- Module Dependencies has its
// own simpler flat renderer (renderModuleDependencyGraph()); this one is for graphs with subgroups
// (Mermaid-parsed diagrams, and SPI Map's own live-generated node/edge data -- same shape).
// With a container, draws a standalone read-only graph there (README flow blocks) and returns it;
// without one, draws into the page's single #diagram-cy and keeps it in diagramCy (zoom controls).
function renderCytoscapeFromGraph(nodes, edges, rankDir, container) {
  const parentOf = {};
  nodes.forEach(n => { parentOf[n.id] = n.parent || null; });
  // An edge directly between a compound parent and its own descendant forms a 2-cycle dagre's
  // compound-aware ranking can't resolve -- it collapses the whole graph onto one rank. Nesting
  // already shows the containment relationship visually, so these edges are redundant to draw.
  const layoutEdges = edges.filter(e => parentOf[e.target] !== e.source && parentOf[e.source] !== e.target);
  const els = [
    ...nodes.map(n => ({
      data: { id: n.id, label: n.label, parent: n.parent || undefined, file: n.file || undefined, shape: n.shape || "rectangle" },
      classes: n.isGroup ? "group-node" : ""
    })),
    ...layoutEdges.filter(e => !e.deferred).map(e => ({
      data: { id: "e" + edges.indexOf(e), source: e.source, target: e.target, label: e.label || "", dashed: e.dashed, rowId: e.rowId }
    }))
  ];
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  const cy = cytoscape({
    container: container || document.getElementById("diagram-cy"),
    elements: els,
    ...(container ? { userZoomingEnabled: false, userPanningEnabled: false, boxSelectionEnabled: false, autoungrabify: true } : {}),
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 10, "color": "#1a202c", "text-valign": "center", "text-halign": "center",
          "background-color": "#ebf4ff", "border-width": 1.5, "border-color": "#2b6cb0",
          "shape": "round-rectangle", "width": "label", "height": "label", "padding": "10px",
          "text-wrap": "wrap", "text-max-width": 110
        } },
      { selector: 'node[shape = "diamond"]', style: {
          "shape": "diamond", "padding": "26px", "background-color": "#fffaf0", "border-color": "#c05621", "text-max-width": 90
        } },
      { selector: 'node[shape = "barrel"]', style: {
          "shape": "barrel", "background-color": "#f0fff4", "border-color": "#2f855a"
        } },
      { selector: "node.group-node", style: {
          "background-color": "#f7f8fa", "background-opacity": 0.6, "border-color": "#a0aec0",
          "border-style": "dashed", "text-valign": "top", "font-weight": "bold", "font-size": 11,
          "padding": "16px"
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
      ? { name: "dagre", rankDir: rankDir || "TB", nodeSep: 18, rankSep: 70, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20 }
  });
  if (!container) {
    diagramCy = cy;
    cy.on("zoom", () => {
      const label = document.getElementById("zoom-label");
      if (label) label.textContent = Math.round(cy.zoom() * 100) + "%";
    });
  }
  // Any node carrying a real "file" (SPI Map's interfaces/impl classes) opens it directly --
  // group/parent nodes and Mermaid-parsed diagrams with no file association just have no data to
  // open, so nothing happens on click there, same as before.
  cy.on("tap", "node[file]", e => window.open("../../" + e.target.data("file"), "_blank"));
  cy.nodes("[file]").style("cursor", "pointer");
  // SPI Map's "calls"/"implemented by" edges have a matching table row below (spiRowId()) --
  // jump to it on click. Any other diagram's edges (Module Dependencies, Bounded Contexts) just
  // find no matching id and no-op, so this is safe to wire in generically here.
  // Edges flagged `deferred` are added after the layout, so they never position the nodes.
  cy.add(layoutEdges.filter(e => e.deferred).map(e => ({
    data: { id: "e" + edges.indexOf(e), source: e.source, target: e.target, label: e.label || "", dashed: e.dashed, rowId: e.rowId }
  })));
  cy.on("tap", "edge", e => {
    const d = e.target.data();
    const rowId = d.label === "calls" ? spiRowId("call", d.target, d.source.replace(/^call_/, ""))
      : d.label === "implemented by" ? spiRowId("impl", d.source, d.target) : null;
    const row = rowId && document.getElementById(rowId);
    if (!row) return;
    row.scrollIntoView({ behavior: "smooth", block: "center" });
    row.classList.remove("row-flash");
    void row.offsetWidth;
    row.classList.add("row-flash");
  });
  return cy;
}

// Draws every README flowchart fence (mdBlockToHtml() emits it as a .flow-block holding its source)
// as its own read-only Cytoscape graph, then sizes the block to the drawn graph.
function renderFlowBlocks() {
  document.querySelectorAll(".flow-block:not([data-drawn])").forEach(box => {
    const holder = box.querySelector(".flow-cy");
    if (!holder.clientWidth) return;
    box.setAttribute("data-drawn", "1");
    const parsed = parseFlowchart(box.querySelector(".flow-src").textContent);
    const cy = renderCytoscapeFromGraph(parsed.nodes, parsed.edges, parsed.direction, holder);
    const bb = cy.elements().boundingBox();
    const scale = Math.min(1, Math.max(200, holder.clientWidth - 32) / bb.w);
    holder.style.height = Math.ceil(bb.h * scale + 32) + "px";
    cy.resize();
    cy.zoom(scale);
    cy.pan({ x: (holder.clientWidth - bb.w * scale) / 2 - bb.x1 * scale, y: 16 - bb.y1 * scale });
  });
}

// Live from real Java source (platform-commons/*/spi interfaces + real `implements` across
// starters/marketplace-app) -- same "live, not a separately-maintained .md" pattern as Module
// Dependencies. Left-to-right layout (interfaces on the left, implementations on the right) reads
// better for this 2-level interface->impl shape than top-to-bottom.
// One diagram per subsystem instead of one 70+-node canvas -- the combined graph (every SPI
// interface's callers + implementations on one page) was too dense to read. An interface belongs
// to a subsystem per MODEL.spiMap.details; edges/nodes pulled in are whatever actually touches
// that subsystem's own interfaces, same node/edge shape renderCytoscapeFromGraph already expects.
