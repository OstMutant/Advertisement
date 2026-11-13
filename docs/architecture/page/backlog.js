// ── Backlog screen ───────────────────────────────────────────────────────────────────────────
// Priority text is free-form prose ("medium-high (UX)", "🔵 low now, rising with data volume"),
// not a clean enum -- ranked by keyword match, compound levels (medium-high, low-medium) checked
// before their plain counterparts so "medium-high" doesn't misrank as plain "medium".
function priorityRank(p) {
  const s = (p || "").toLowerCase();
  if (/critical/.test(s)) return 0;
  if (/\btop\b/.test(s)) return 1;
  if (/highest/.test(s)) return 2;
  if (/medium-high|high\/medium|high for/.test(s)) return 3;
  if (/\bhigh\b/.test(s)) return 4;
  if (/\bmedium\b/.test(s)) return 5;
  if (/low-medium/.test(s)) return 6;
  if (/\blow\b|cheap/.test(s)) return 7;
  if (/lowest/.test(s)) return 8;
  return 9;
}

// backlog/BACKLOG.md's "At a glance" table already has the real, curated execution order (e.g.
// 138 before 136 before 135, even though all three are "Top" tier) -- re-deriving order from each
// issue's own free-form Priority prose only recovers coarse tiers, losing that fine-grained
// sequence. Issues listed in the table sort by their real position there; anything not yet
// triaged into that compact view falls back to its own Priority-text tier, after every listed one.
function backlogOrderRank(file) {
  const m = file.match(/improvement-(\d+)/);
  if (!m) return -1;
  return MODEL.backlogPriorityOrder.indexOf(m[1]);
}
function issueSortKey(it) {
  const rank = backlogOrderRank(it.file);
  return rank >= 0 ? [0, rank] : [1, priorityRank(it.priority)];
}

// Same click-to-popup pattern as ADRs: title in the list, click shows the short description,
// the popup body itself carries a real link to open the full issue file. Indices passed to
// openIssuePopup() stay the *original* array positions, since that's what it looks up by.
function renderIssueList(kind, items) {
  if (!items || !items.length) return `<div class="empty-hint">None.</div>`;
  const order = items.map((_, i) => i).sort((a, b) => {
    const ka = issueSortKey(items[a]), kb = issueSortKey(items[b]);
    return ka[0] - kb[0] || ka[1] - kb[1];
  });
  return order.map(i => {
    const it = items[i];
    return `
    <div class="adr-item">
      <a onclick="openIssuePopup('${kind}', ${i})">${esc(it.title)}</a>
      ${it.priority ? `<span class="adr-status">${esc(it.priority)}</span>` : ""}
      <span class="adr-file">${esc(it.file)}</span>
    </div>
  `;
  }).join("");
}

function openIssuePopup(kind, index) {
  const item = backlogNode[kind === "open" ? "openIssues" : "completedIssues"][index];
  if (!item) return;
  document.getElementById("adr-popup-title").textContent = item.title;
  document.getElementById("adr-popup-status").textContent = item.priority || "";
  document.getElementById("adr-popup-body").innerHTML =
    `<p>${mdInlineToHtml(item.description)}</p><p>${sourceLink(item.file)}</p>`;
  document.getElementById("adr-popup").showModal();
}

let backlogFilter = "open";
function setBacklogFilter(f) { backlogFilter = f; renderBacklog(); }

function renderBacklog() {
  const items = backlogFilter === "open" ? backlogNode.openIssues : backlogNode.completedIssues;
  const label = backlogFilter === "open" ? "Open issues" : "Completed issues";
  let html = backButtonHtml();
  html += `<h2 class="screen-title">Backlog</h2>
    <div class="screen-desc">Per-issue titles in Track A — see ${sourceLink("backlog/BACKLOG.md")} for the ranked, priority view.</div>
    <div class="card-grid">
      <div class="card ${backlogFilter === "open" ? "card-active" : ""}" onclick="setBacklogFilter('open')"><div class="card-title">${backlogNode.open_issues}</div><div class="card-desc">open issues (backlog/tasks/)</div></div>
      <div class="card ${backlogFilter === "completed" ? "card-active" : ""}" onclick="setBacklogFilter('completed')"><div class="card-title">${backlogNode.completed_issues}</div><div class="card-desc">completed issues (backlog/completed/tasks/)</div></div>
    </div>`;
  html += `<section class="block"><h3>${label} (${items.length})</h3>${renderIssueList(backlogFilter, items)}</section>`;
  document.getElementById("content").innerHTML = html;
}

// Diagram groups drawn as draggable Cytoscape graphs from a node/edge source (the Database ERD has its own renderer).
