// ── ADRs screen: flat, deduplicated list of every ADR across every module's DECISIONS.md, read
// live from .claude/nav/adr-index.md (MODEL.allAdrs) -- no separate hand-maintained summary.
let adrStatusFilter = "All";
function setAdrStatusFilter(f) { adrStatusFilter = f; renderAdrs(); }

function renderAdrs() {
  const adrs = MODEL.allAdrs || [];
  // Status text in this repo is often a long free-form annotation, not a bare word (e.g.
  // "Accepted (done 2026-06-26)") -- bucket by just the first word so the filter cards stay a
  // handful, not dozens of near-duplicate ones; the full original text is still shown per-row in
  // the table below.
  const bucketOf = a => (a.status.match(/^[A-Za-z]+/) || [a.status])[0];
  const counts = {};
  adrs.forEach(a => { const b = bucketOf(a); counts[b] = (counts[b] || 0) + 1; });
  const filtered = adrStatusFilter === "All" ? adrs : adrs.filter(a => bucketOf(a) === adrStatusFilter);

  let html = backButtonHtml();
  html += `<h2 class="screen-title">ADRs</h2>
    <div class="screen-desc">${adrs.length} architectural decisions across every module's own DECISIONS.md — see ${sourceLink(".claude/nav/adr-index.md")} for the generated index this screen reads, and the Overview section at the bottom of this screen for what an ADR is and how it's used. Clicking an ADR opens its full text inline only when the model was generated with <code>--with-adr-details</code> (opt-in, off by default); otherwise it opens the real DECISIONS.md file directly.</div>`;
  html += `<div class="card-grid">
    <div class="card ${adrStatusFilter === "All" ? "card-active" : ""}" onclick="setAdrStatusFilter('All')"><div class="card-title">${adrs.length}</div><div class="card-desc">All</div></div>`;
  html += Object.entries(counts).map(([status, count]) =>
    `<div class="card ${adrStatusFilter === status ? "card-active" : ""}" onclick="setAdrStatusFilter('${esc(status)}')"><div class="card-title">${count}</div><div class="card-desc">${esc(status)}</div></div>`
  ).join("");
  html += `</div>`;

  // Grouped by module (first-appearance order, same order MODEL.allAdrs already carries) instead
  // of one flat table -- the module is now the group heading (with its own DECISIONS.md link
  // right there), so a per-row "Module" column would just repeat what the heading already says.
  const groups = [];
  const groupIndex = {};
  filtered.forEach(a => {
    if (!(a.module in groupIndex)) { groupIndex[a.module] = groups.length; groups.push({ module: a.module, items: [] }); }
    groups[groupIndex[a.module]].items.push(a);
  });
  groups.forEach(grp => {
    // Most-applied first within each module -- surfaces which ADRs are actually
    // load-bearing instead of the file-appearance order adr-index.md happens to carry.
    grp.items.sort((a, b) => (b.applied || 0) - (a.applied || 0));
    // Only a real MODULE node has a Module-detail page to link to -- SCRIPT_GROUP entries
    // (docs/architecture/scripts, scripts/ci, etc.) stay plain text, no dead/wrong-feeling navigation.
    const grpNode = byId[grp.module];
    const grpHeading = grpNode && grpNode.type === "MODULE"
      ? moduleLink(grp.module)
      : esc(grp.module);
    html += `<section class="block"><h3>${grpHeading} (${grp.items.length}) ${sourceLink(grp.module + "/DECISIONS.md")}</h3>
      <table class="simple"><thead><tr><th>ADR</th><th>Status</th><th style="width:70px">Applied</th><th>Title</th></tr></thead><tbody>`;
    grp.items.forEach(a => {
      html += `<tr><td><a onclick="openAdrPopupForAdr('${esc(a.id)}', '${esc(a.module)}', '${esc(a.title)}', '${esc(a.status)}')">${esc(a.id)}</a></td><td>${esc(a.status)}</td><td>${a.applied ?? ""}</td><td>${esc(a.title)}</td></tr>`;
    });
    html += `</tbody></table></section>`;
  });

  html += `<section class="block"><h3>Overview</h3>` + GLOSSARY.map(g => `<div class="glossary-item"><strong>${esc(g.term)}</strong>${g.body}</div>`).join("") + `</section>`;

  document.getElementById("content").innerHTML = html;
}

// Always opens the popup -- title/status come from MODEL.allAdrs (always populated, passed in
// directly from the calling row so there's no second lookup) -- the body is the full embedded
// text only if that module's full ADR content was embedded (see .claude/nav/adr-index.md and
// FULL_DECISIONS_MODULES above, gated behind --with-adr-details); otherwise a real link to the
// source file plus a generic pointer to where that flag is documented, not a copy of the exact
// command here (kept deliberately decoupled from one script's exact CLI shape). Takes the ADR id +
// home module directly since the ADRs screen's list is flat across every module, not scoped to one
// node.
function openAdrPopupForAdr(id, module, title, status) {
  const bareId = id.split(" (")[0];
  const homeNode = byId[module];
  const found = homeNode && homeNode.decisions && homeNode.decisions.adrs.find(x => x.id === bareId);
  document.getElementById("adr-popup-title").textContent = `${id} — ${title}`;
  document.getElementById("adr-popup-status").textContent = status;
  if (found) {
    document.getElementById("adr-popup-body").innerHTML = mdBlockToHtml(found.body, module);
  } else {
    document.getElementById("adr-popup-body").innerHTML =
      `<div class="empty-hint">Full text not included in this build. Read it directly: ${sourceLink(module + "/DECISIONS.md")}</div>
       <div class="empty-hint">For how to include full ADR text inline here, see <a onclick="document.getElementById('adr-popup').close(); navigate({screen:'pipelines'})">Tooling &amp; Pipelines</a>.</div>`;
  }
  document.getElementById("adr-popup").showModal();
}

// ── System screen's "Overview" block: a small glossary of general terms/concepts used across
// this tool and this repo's own conventions -- hand-maintained prose, same "genuinely
// non-mechanical content" exception this tool already uses elsewhere (the SPI Map and Bounded
// Contexts narrative sections) -- a term's meaning isn't something to extract
// from a file, it's explained once, here, at the bottom of the System screen (not a separate
// "Notes" card/screen -- dropped that shape after landing it, kept the content).
const GLOSSARY = [
  { term: "ADR (Architectural Decision Record)", body: `
    <p><strong>What it is:</strong> a single, dated, numbered record of one architectural or
    technical decision — what was decided, the context that led to it, and why. Each module owns
    its own <code class="path">DECISIONS.md</code> file holding its own sequential
    <code>ADR-NNN</code>s.</p>
    <p><strong>How it's used here:</strong> filed via <code>/record-decision &lt;module&gt; —
    &lt;title&gt;</code>. Once written, an ADR is never edited to remove content or deleted — if a
    later decision reverses or replaces it, the old ADR gets <code>Status: Superseded</code> (or an
    amendment note) pointing at the new one, and a new ADR is added instead.
    ${sourceLink(".claude/nav/adr-index.md")} is a generated, searchable flat index of every ADR
    (id/module/status/title) — check it before filing a new one, to avoid re-deciding something
    already settled.</p>
    <p><strong>Boundaries — what an ADR is not:</strong> not a running changelog of "what
    changed" (that's git history); not where code comments explain WHY (those stay a single
    inline line, in the code itself); not a substitute for a backlog issue (which tracks work not
    yet done — an ADR records a decision already made); not renumbered or reorganized once
    written — its number is permanent and load-bearing (other code/docs reference it by number)
    even if its owning file later moves or is renamed.</p>
  ` }
];

