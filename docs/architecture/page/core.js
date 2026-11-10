
if (typeof cytoscapeDagre !== "undefined") cytoscape.use(cytoscapeDagre);

// ── Data indices ────────────────────────────────────────────────────────────────────────────
const byId = {};
MODEL.nodes.forEach(n => byId[n.id] = n);
const moduleNodes = MODEL.nodes.filter(n => n.type === "MODULE");
const commandNodes = MODEL.nodes.filter(n => n.type === "COMMAND");
const skillNodes = MODEL.nodes.filter(n => n.type === "SKILL");
const agentNodes = MODEL.nodes.filter(n => n.type === "AGENT");
const scriptGroupNodes = MODEL.nodes.filter(n => n.type === "SCRIPT_GROUP");
const backlogNode = MODEL.nodes.find(n => n.type === "BACKLOG_SUMMARY");

// ── Tooling & Pipelines' own card->detail groups -- one card per tool, same drill-down shape as
// Diagrams' groupKey (list view with no view.groupId, detail view once one is picked). "category"
// matches a top-level SCRIPT_GROUP node's own category field (SCRIPT_GROUP_CATEGORY for
// build-architecture-page; each TREE_ROOTS entry's own SCRIPT_TREE_ROOT_CATEGORY for
// ai-tooling/scripts-tree). ai-tooling and scripts-tree both open a real arbitrary-depth
// drill-down (view.path walks n.children) instead of a flat category filter -- see TREE_ROOTS and
// renderScriptTree() below.
const PIPELINE_GROUPS = {
  "ai-tooling": { icon: "🤖", label: "AI Tooling", category: "AI Tooling", desc: `${commandNodes.length} commands, ${skillNodes.length} skills, ${agentNodes.length} agents, and the rest of .claude/ (nav, rules, script headers)` },
  "build-architecture-page": { icon: "🗺️", label: "Build architecture page", category: "Build architecture page", desc: "docs/architecture/scripts — this page's own generator" },
  "scripts-tree": { icon: "📜", label: "Scripts", category: "Scripts", desc: "Every developer script for building, deploying, testing, and running CI lives here: local deploy/infra setup, Docker/Maven builds, SonarQube analysis, and the isolated Dagu-based CI runner. Playwright's end-to-end test suite sits alongside as its own folder. Drill into any folder for its own README and per-file headers." }
};
const PIPELINE_GROUP_ORDER = ["ai-tooling", "build-architecture-page", "scripts-tree"];
// Which top-level SCRIPT_GROUP tree root(s) each tree-shaped pipeline group drills into -- the
// first root's own children render directly at the group's root view (no intermediate card to
// click through); any further roots (only scripts-tree has one: "playwright") are inserted as one
// more sibling folder-card at that same level, same shape scriptTreeNodeAt()/renderScriptTree()
// already used to hardcode for "scripts"+"playwright" specifically.
const TREE_ROOTS = { "scripts-tree": ["scripts", "playwright"], "ai-tooling": [".claude"] };
const totalDiagramCount = MODEL.diagramGroups.reduce((sum, g) => sum + g.diagrams.length, 0);

const domainOrder = [];
moduleNodes.forEach(n => { if (!domainOrder.includes(n.domain)) domainOrder.push(n.domain); });
const domainPalette = ["#2b6cb0","#2f855a","#c05621","#805ad5","#b83280","#2c7a7b","#975a16","#4a5568"];
function domainColor(domain) {
  const i = domainOrder.indexOf(domain);
  return domainPalette[i % domainPalette.length];
}

function esc(s) { return (s || "").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;"); }
function displayName(id) { return id.replace(/^command:|^skill:/, ""); }

// ── Router: System | Module(id) | Pipelines | Backlog | Diagrams(groupKey?, diagramIndex?) ──────
// One navigate() call is the single place that maintains the breadcrumb trail -- every screen
// drills through it, so every screen gets correct back-navigation for free, with no per-screen
// breadcrumb-building code to keep in sync (same "growing stack, only append, never rewrite"
// shape as this project's real BreadcrumbStep pattern in marketplace-app -- see root CLAUDE.md
// "Breadcrumb Pattern"). Add a new screen by teaching crumbLabelFor() its label; navigate()/
// renderBreadcrumb() need no changes.
let view = { screen: "system" };
let crumbStack = []; // steps between the implicit "System" root and the current view; never
                      // rewritten in place -- only pushed (drilling in) or truncated (going back)

function crumbLabelFor(v) {
  if (v.screen === "module") return displayName(v.id);
  if (v.screen === "pipelines") {
    if (v.groupId) return PIPELINE_GROUPS[v.groupId] ? PIPELINE_GROUPS[v.groupId].label : v.groupId;
    return "Tooling & Pipelines";
  }
  if (v.screen === "backlog") return "Backlog";
  if (v.screen === "adrs") return "ADRs";
  if (v.screen === "codequality") {
    if (v.section === "sonar") return "Code Quality — Sonar Metrics";
    if (v.section === "archunit") return "Code Quality — ArchUnit";
    if (v.section === "findings") return "Code Quality — Findings";
    if (v.section === "practices") return "Code Quality — Best Practices";
    if (v.section === "coverage") return "Code Quality — Coverage";
    if (v.section === "tests") return "Code Quality — Tests";
    if (v.section === "logs") return "Code Quality — Run logs";
    return "Code Quality";
  }
  if (v.screen === "diagrams") {
    if (v.groupKey && v.diagramIndex !== undefined) {
      const g = MODEL.diagramGroups.find(x => x.key === v.groupKey);
      return `${g.label} — ${g.diagrams[v.diagramIndex].title}`;
    }
    return "Diagrams";
  }
  return "";
}

function navigate(next) {
  // Drilling deeper within the unified Scripts tree (path segments, same "pipelines"/groupId) is
  // a path change, not a new navigation hop -- crumbStack must not grow on every folder-card
  // click, or the breadcrumb repeats "Scripts" once per depth level. Scoped tightly to
  // screen === "pipelines" with a real groupId (never matches the diagrams screen, which
  // identifies its own views via groupKey/diagramIndex instead -- both undefined on unrelated
  // diagram views would otherwise false-match here).
  const samePlace = next.screen === "pipelines" && view.screen === "pipelines" && next.groupId && next.groupId === view.groupId;
  if (next.screen === "system") {
    crumbStack = [];
  } else if (view.screen !== "system" && !samePlace) {
    crumbStack.push(view);
  }
  view = next;
  render();
  window.scrollTo(0, 0);
}

function navigateToCrumb(index) {
  view = crumbStack[index];
  crumbStack = crumbStack.slice(0, index);
  render();
  window.scrollTo(0, 0);
}

// Generic "one step back" -- same stack, so any button meaning "go back" (not "drill to a named
// place") stays correct automatically instead of hand-coding where it should land. A
// tree-drilling view (the unified Scripts tree) one or more folders deep goes up exactly one
// folder level first -- crumbStack itself never grew for those levels (see navigate()), so
// falling straight through to the crumbStack-based jump below would skip past every intermediate
// folder straight out of the whole tree in one click.
function navigateBack() {
  if (view.screen === "pipelines" && view.groupId && view.path && view.path.length) {
    navigate({ screen: "pipelines", groupId: view.groupId, path: view.path.slice(0, -1) });
    return;
  }
  if (crumbStack.length === 0) { navigate({ screen: "system" }); return; }
  navigateToCrumb(crumbStack.length - 1);
}

// Same toolbar row the Module/diagram-detail screens already use -- every screen reached by
// clicking a System-level card gets the same "← back" affordance, not just the two that happened
// to need it first.
function backButtonHtml() {
  return `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
    </div>`;
}

function renderBreadcrumb() {
  const bc = document.getElementById("breadcrumb");
  let html = `<a onclick="navigate({screen:'system'})">System</a>`;
  crumbStack.forEach((v, i) => {
    html += `<span class="sep">›</span><a onclick="navigateToCrumb(${i})">${esc(crumbLabelFor(v))}</a>`;
  });
  if (view.screen !== "system") {
    // A tree-drilling view (the unified Scripts tree) whose own path is non-empty is "inside" its
    // own root, not at it -- crumbStack itself never grows for a path change within the same tree
    // (see navigate()), so the breadcrumb must show the real depth itself: "Scripts" (always
    // clickable back to path: []) plus one clickable segment per path entry, the last one plain
    // (current, matching every other screen's own last-segment convention).
    if (view.screen === "pipelines" && view.groupId && view.path && view.path.length) {
      html += `<span class="sep">›</span><a onclick="navigate({screen:'pipelines',groupId:'${view.groupId}',path:[]})">${esc(crumbLabelFor(view))}</a>`;
      view.path.forEach((seg, i) => {
        const segPath = JSON.stringify(view.path.slice(0, i + 1));
        if (i === view.path.length - 1) {
          html += `<span class="sep">›</span><span class="current">${esc(seg)}</span>`;
        } else {
          html += `<span class="sep">›</span><a onclick='navigate({screen:"pipelines",groupId:"${view.groupId}",path:${segPath}})'>${esc(seg)}</a>`;
        }
      });
    } else {
      html += `<span class="sep">›</span><span class="current">${esc(crumbLabelFor(view))}</span>`;
    }
  }
  bc.innerHTML = html;
}

