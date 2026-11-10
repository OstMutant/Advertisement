// ── Full-text search ─────────────────────────────────────────────────────────────────────────
// Maps a script-tree node id to the navigate() target of its folder page (see scriptTreeNodeAt).
function treeLocation(nodeId) {
  const segs = nodeId.split("/");
  if (segs[0] === ".claude") return { groupId: "ai-tooling", path: segs.slice(1) };
  if (segs[0] === "playwright") return { groupId: "scripts-tree", path: segs };
  return { groupId: "scripts-tree", path: segs.slice(1) };
}

let searchDocs = [];
let searchHits = [];
let searchIndex = null;

// Module pages rank above the ADRs, tasks and docs that merely mention the module.
const SEARCH_KIND_BOOST = { module: 3 };
const SEARCH_EXACT_TITLE_BOOST = 2;

const SEARCH_KIND_LABELS = { module: "Module", adr: "ADR", task: "Task", table: "Table", doc: "Doc", folder: "Folder" };

function buildSearchIndex() {
  if (typeof MiniSearch === "undefined") {
    document.getElementById("search-wrap").hidden = true;
    return;
  }
  const docs = [];
  const add = (kind, title, parts, open) => docs.push({ id: docs.length, kind, title, text: parts.filter(Boolean).join(" "), open });
  const names = items => (items || []).map(it => `${it.name} ${it.description || ""}`);

  moduleNodes.forEach(n => add("module", n.id,
    [n.description, n.domain, (n.tables || []).join(" "), ...names(n.entities), ...names(n.keyServices), ...names(n.contracts), n.readme],
    () => navigate({ screen: "module", id: n.id })));

  MODEL.allAdrs.forEach(a => {
    const homeNode = byId[a.module];
    const found = homeNode && homeNode.decisions && homeNode.decisions.adrs.find(x => x.id === a.id.split(" (")[0]);
    add("adr", a.id + " — " + a.title, [a.module, a.status, found && found.body],
      () => openAdrPopupForAdr(a.id, a.module, a.title, a.status));
  });

  [["open", backlogNode.openIssues], ["completed", backlogNode.completedIssues]].forEach(([kind, items]) => {
    (items || []).forEach((it, i) => add("task", it.title, [it.description, it.priority, it.file], () => openIssuePopup(kind, i)));
  });

  MODEL.dbErd.tables.forEach(t => add("table", t.name,
    [t.module, t.remarks, ...t.columns.map(c => `${c.name} ${c.remarks || ""}`)],
    () => navigateToTable(t.name)));

  add("doc", "README.md", [MODEL.rootReadme], () => navigate({ screen: "system" }));
  add("doc", "INFRASTRUCTURE.md", [MODEL.rootInfrastructure], () => navigate({ screen: "pipelines" }));
  add("doc", "Best practices", [MODEL.rootBestPractices], () => navigate({ screen: "codequality", section: "practices" }));

  const treeNodes = [];
  const collect = n => { treeNodes.push(n); (n.children || []).forEach(collect); };
  TREE_ROOTS["scripts-tree"].concat(TREE_ROOTS["ai-tooling"]).forEach(r => {
    const root = scriptGroupNodes.find(n => n.id === r);
    if (root) collect(root);
  });
  treeNodes.forEach(n => add("folder", n.id,
    [n.readme, (n.files || []).join(" "), ...(n.headers || []).map(h => h.description)],
    () => { const loc = treeLocation(n.id); navigate({ screen: "pipelines", groupId: loc.groupId, path: loc.path }); }));

  searchDocs = docs;
  searchIndex = new MiniSearch({
    fields: ["title", "text"],
    storeFields: ["kind", "title"],
    searchOptions: {
      prefix: true, fuzzy: 0.2, boost: { title: 3 }, combineWith: "AND",
      boostDocument: (id, term, stored) => SEARCH_KIND_BOOST[stored.kind] || 1
    }
  });
  searchIndex.addAll(docs);

  // A title equal to the whole query outranks partial matches; the term-level boosts cannot see the whole query.
  const searchRanked = q => searchIndex.search(q)
    .map(h => ({ ...h, score: h.score * (h.title.toLowerCase() === q.toLowerCase() ? SEARCH_EXACT_TITLE_BOOST : 1) }))
    .sort((a, b) => b.score - a.score);

  const box = document.getElementById("search-box");
  const results = document.getElementById("search-results");
  const closeResults = () => { results.hidden = true; };
  const openHit = k => { const hit = searchHits[k]; if (hit) { closeResults(); searchDocs[hit.id].open(); } };

  box.addEventListener("input", () => {
    const q = box.value.trim();
    if (!q) { closeResults(); return; }
    searchHits = searchRanked(q).slice(0, 20);
    results.innerHTML = searchHits.length
      ? searchHits.map((h, k) => `<div class="search-hit${k === 0 ? " first" : ""}" data-hit="${k}"><span class="search-kind">${esc(SEARCH_KIND_LABELS[h.kind] || h.kind)}</span>${esc(h.title)}</div>`).join("")
      : `<div class="search-empty">No results.</div>`;
    results.hidden = false;
  });
  box.addEventListener("keydown", e => {
    if (e.key === "Enter") openHit(0);
    else if (e.key === "Escape") closeResults();
  });
  results.addEventListener("click", e => {
    const el = e.target.closest("[data-hit]");
    if (el) openHit(Number(el.dataset.hit));
  });
  document.addEventListener("click", e => {
    if (!document.getElementById("search-wrap").contains(e.target)) closeResults();
  });
}

