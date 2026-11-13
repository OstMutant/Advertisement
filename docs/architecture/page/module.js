// ── Module screen: full readable detail, drill-down deps, ADRs ──────
function renderDepList(ids, label, emptyText) {
  if (!ids || ids.length === 0) return `<div class="empty-hint">${emptyText}</div>`;
  return `<ul class="dep-list">` + ids.map(id => {
    const n = byId[id];
    return `<li><a onclick="navigate({screen:'module',id:'${id}'})">${esc(id)}</a>${n ? `<span class="dep-tag">${esc(n.domain)}</span>` : ""}</li>`;
  }).join("") + `</ul>`;
}

// Real code-quality/coupling numbers for one module -- SonarQube (ncloc/complexity/code smells/
// duplication/file count, from a running Sonar server) and ArchUnit (Efferent/Afferent Coupling,
// Instability, Abstractness, from the real class-dependency graph via
// ArchitectureMetricsExport.java) now live on their own System-level "Code Quality" screen
// (renderCodeQuality() below), one table per source so it's always clear which numbers came from
// where -- not repeated per module page anymore.
// Green/yellow/red thresholds -- only for *ratios*, never raw counts (Ce/Ca, code smells,
// complexity totals have no universal per-module threshold; only per-file/per-LOC/derived ratios
// do). value/thresholds -> the metric-good/metric-watch/metric-critical class.
function metricClass(value, green, yellow) {
  if (value < green) return "metric-good";
  if (value < yellow) return "metric-watch";
  return "metric-critical";
}
function coloredMetric(value, decimals, green, yellow) {
  return `<span class="${metricClass(value, green, yellow)}">${esc(value.toFixed(decimals))}</span>`;
}

const SONAR_GLOSSARY = [
  { field: "Java files", desc: "Number of .java files under this module's src/main/java. The denominator for the two per-file ratios below — on its own it's just a size indicator, not a quality signal." },
  { field: "Lines of code", desc: "Non-comment lines of code (NCLOC), as counted by SonarQube. A raw size measure, not a complexity measure — a large module can still be simple if it's mostly straightforward, repetitive code (e.g. DTOs, generated mappers)." },
  { field: "Complexity", desc: "Cyclomatic complexity — the number of independent execution paths through the code, counted from branching statements (if/for/while/case/&&/||). Higher means more paths a test suite has to cover to reach full branch coverage, and more ways the code can behave differently at runtime. This is the module's raw total, not yet normalized by size — a big module naturally has a bigger raw total even if each individual method is simple." },
  { field: "Complexity/file", desc: "Complexity divided by Java file count — normalizes away module size so modules of very different sizes can be compared fairly. A high value means the average file is doing a lot of branching, which usually also means it's doing more than one job and is a candidate for splitting. Colored: green < 10, yellow 10-20, red > 20." },
  { field: "Cognitive complexity", desc: "SonarQube's readability metric — unlike cyclomatic complexity, it specifically penalizes nested/tangled control flow (an if inside a loop inside a try inside another if) more heavily than the same number of branches laid out flat, because nesting is what actually makes code hard for a human to hold in their head while reading it." },
  { field: "Cognitive/file", desc: "Cognitive complexity divided by Java file count. A high value is a stronger signal than high raw Complexity/file that a file is genuinely hard to read (not just branch-heavy) — worth a closer look even before it hits a hard SonarQube rule threshold. Colored: green < 10, yellow 10-25, red > 25." },
  { field: "Code smells", desc: "Count of maintainability issues SonarQube's rule engine actually flagged in this module (naming, dead code, duplicated logic, overly long methods, etc.) — a concrete, itemized list (visible in the SonarQube dashboard itself), not a derived estimate like the complexity numbers above." },
  { field: "Code smells/1k LOC", desc: "Code smells normalized per 1000 lines of code, so a small module with 2 smells isn't unfairly compared against a large module with 20 — what matters is smell density, not raw count. Colored: green < 5, yellow 5-15, red > 15." },
];
const COVERAGE_GLOSSARY = [
  { field: "Line coverage", desc: "Share of executable source lines run by the tests. Colored: green above 75%, yellow above 60% up to 75%, red at 60% and below (Google's published guideline: 60% acceptable, 75% commendable)." },
  { field: "Branch coverage", desc: "Share of decision branches (if/else, switch cases, boolean operators) taken by the tests. Same colors and thresholds as line coverage." },
  { field: "Run at", desc: "Time the module's tests ran, read from the JaCoCo session in its coverage report. Rows can come from runs of different ages." },
  { field: "integration-tests-aggregate", desc: "Coverage of the starters as exercised by the integration tests, merged into one report." },
];
const TESTS_GLOSSARY = [
  { field: "Tests", desc: "Number of test cases Surefire ran in the module." },
  { field: "Failed", desc: "Test cases whose assertion failed." },
  { field: "Errors", desc: "Test cases that threw an unexpected exception." },
  { field: "Skipped", desc: "Test cases that were disabled or whose assumptions did not hold." },
  { field: "Run at", desc: "Modification time of the module's newest Surefire report file." },
];
const ARCHUNIT_GLOSSARY = [
  { field: "Efferent coupling (Ce)", desc: "How many classes outside this module the classes inside it depend on — this module's \"outgoing\" dependencies. High Ce means this module is exposed to a lot of external change: if any of those other classes change their API, this module is one of the things that might break." },
  { field: "Afferent coupling (Ca)", desc: "How many classes outside this module depend on classes inside it — this module's \"incoming\" dependencies, i.e. its real-world blast radius. High Ca means many other parts of the codebase would be affected if this module's public API changed, so changes here need extra care." },
  { field: "Instability (I)", desc: "I = Ce/(Ce+Ca), from Robert Martin's stability metrics — 0 means maximally stable (only depended upon, never depends on anything itself — safe to keep unchanged, risky to modify), 1 means maximally unstable (only depends on others, nothing depends on it — safe to change freely, since nothing breaks downstream)." },
  { field: "Abstractness (A)", desc: "The ratio of abstract classes/interfaces to total classes in the module. A module that's mostly concrete implementation classes has low A; a module that's mostly interfaces/abstract contracts (like an SPI layer) has high A." },
  { field: "Distance from Main Sequence", desc: "|A+I-1| — the classic \"is this module positioned sensibly\" check from Martin's stability/abstractness model. 0 is the ideal balance (stable modules should be abstract, unstable modules should be concrete). Far from 0 in the low-A/low-I direction is the \"Zone of Pain\" (concrete AND stable — hard to change safely, because nothing about it is abstracted away, yet lots of things depend on it). Far from 0 in the high-A/high-I direction is the \"Zone of Uselessness\" (abstract AND unstable — an interface layer nothing actually relies on, which usually means it's dead weight). Colored: green < 0.3, yellow 0.3-0.6, red > 0.6." },
];

// The "Overview" block: a Field / What-it-means table of one glossary array.
function glossaryBlock(glossary) {
  return `<section class="block"><h3>Overview</h3>
    <table class="simple"><thead><tr><th>Field</th><th>What it means</th></tr></thead><tbody>` +
    glossary.map(g => `<tr><td class="scope-label">${esc(g.field)}</td><td>${esc(g.desc)}</td></tr>`).join("") +
    `</tbody></table></section>`;
}

function renderCodeQuality() {
  if (!view.section) {
    let html = backButtonHtml();
    html += `<h2 class="screen-title">Code Quality</h2>
      <div class="screen-desc">Real SonarQube + ArchUnit metrics, repo-wide structural findings, and the latest test reports (Playwright, coverage, Surefire, run logs) -- pick where to start.</div>`;
    html += `<div class="card-grid">
      <div class="card" onclick="navigate({screen:'codequality',section:'sonar'})">
        <div class="card-title">Sonar Metrics</div>
        <div class="card-desc">Complexity, cognitive complexity, code smells -- per module</div>
      </div>
      <div class="card" onclick="navigate({screen:'codequality',section:'archunit'})">
        <div class="card-title">ArchUnit</div>
        <div class="card-desc">Efferent/afferent coupling, instability, abstractness -- per module</div>
      </div>
      <div class="card" onclick="navigate({screen:'codequality',section:'findings'})">
        <div class="card-title">🔍 Findings</div>
        <div class="card-desc">Architecture checks, largest files/packages, constructor-injection complexity</div>
      </div>
      <div class="card" onclick="navigate({screen:'codequality',section:'practices'})">
        <div class="card-title">📋 Best Practices</div>
        <div class="card-desc">Project-grounded Java/JUnit/Playwright/Bash/CI checklist, sourced and checked against real code</div>
      </div>
      <a class="card link-card" href="../../playwright/pw-report/index.html" target="_blank" rel="noopener">
        <div class="card-title">Playwright report</div>
        <div class="card-desc">Last e2e run's HTML report. Traces open only via <code>npx playwright show-report</code> (the trace viewer does not work from disk).</div>
      </a>
      <div class="card" onclick="navigate({screen:'codequality',section:'coverage'})">
        <div class="card-title">Coverage (JaCoCo)</div>
        <div class="card-desc">Line and branch coverage per module, with links to each HTML report</div>
      </div>
      <div class="card" onclick="navigate({screen:'codequality',section:'tests'})">
        <div class="card-title">Tests (Surefire)</div>
        <div class="card-desc">Tests, failures and skips per module, failing classes linked</div>
      </div>
      <div class="card" onclick="navigate({screen:'codequality',section:'logs'})">
        <div class="card-title">Run logs</div>
        <div class="card-desc">Console logs of manual runs and of the last CI run, each one a link</div>
      </div>
    </div>`;
    document.getElementById("content").innerHTML = html;
    return;
  }

  let html = backButtonHtml();

  if (view.section === "coverage" || view.section === "tests") {
    const tr = MODEL.testReports;
    const isCov = view.section === "coverage";
    html += isCov
      ? `<h2 class="screen-title">Code Quality — Coverage</h2>
      <div class="screen-desc">JaCoCo line and branch coverage per module, read at generation time from <code>scripts/build-and-test/reports/jacoco/</code> of the last plain <code>build-and-test.sh</code> run, or of the CI unit, integration and Sonar stages, whichever is newer.</div>`
      : `<h2 class="screen-title">Code Quality — Tests</h2>
      <div class="screen-desc">Surefire results per module, read at generation time from <code>scripts/build-and-test/reports/surefire/</code> of the last plain <code>build-and-test.sh</code> run, or of the CI unit, integration and Sonar stages, whichever is newer.</div>`;
    html += `<section class="block"><h3>${isCov ? "Coverage" : "Tests"}</h3>`;
    if (!tr || !(isCov ? tr.coverage : tr.tests).length) {
      const folder = isCov ? "scripts/build-and-test/reports/jacoco/" : "scripts/build-and-test/reports/surefire/";
      html += `<div class="empty-hint">No data -- run <code>bash scripts/build-and-test.sh --unit --integration</code>, then regenerate the map. ${reportLink(folder, isCov ? "Open the coverage folder" : "Open the Surefire folder")}.</div>`;
    } else {
      const bi = tr.buildInfo;
      if (bi) {
        const onOff = v => (String(v) === "true" ? "on" : "off");
        const single = [bi.unitTestArg, bi.integrationTestArg].filter(a => a).join(", ");
        html += `<div class="empty-hint">Last build-and-test run: ${esc(bi.timestamp || "unknown")}; unit ${onOff(bi.runUnit)}, integration ${onOff(bi.runIntegration)}${single ? `; single-test argument: ${esc(single)}` : ""}.</div>`;
      }
      const runAt = r => `<span style="white-space:nowrap">${esc(r.runAt || "-")}</span>`;
      if (isCov) {
        const pct = (c, m) => (c + m === 0 ? "-" : `<span class="${metricClass(100 - (100 * c / (c + m)), 25, 40)}">${esc((100 * c / (c + m)).toFixed(1))}%</span>`);
        html += `<table class="simple"><thead><tr><th>Module</th><th>Line coverage</th><th>Branch coverage</th><th>Run at</th><th>Report</th></tr></thead><tbody>`;
        tr.coverage.forEach(r => {
          const cell = byId[r.id] ? moduleLink(r.id) : esc(r.id);
          html += `<tr><td>${cell}</td><td>${pct(r.lineCovered, r.lineMissed)}</td><td>${pct(r.branchCovered, r.branchMissed)}</td><td>${runAt(r)}</td><td>${reportLink(r.html, "HTML report")}</td></tr>`;
        });
        html += `</tbody></table>
          <div class="empty-hint">marketplace-app's number includes the Vaadin UI package, which the Sonar scan excludes from coverage because the UI is verified by Playwright.</div>`;
      } else {
        const bad = v => (v > 0 ? "metric-critical" : "");
        html += `<table class="simple"><thead><tr><th>Module</th><th>Tests</th><th>Failed</th><th>Errors</th><th>Skipped</th><th>Time (s)</th><th>Run at</th></tr></thead><tbody>`;
        tr.tests.forEach(r => {
          const cell = byId[r.id] ? moduleLink(r.id) : esc(r.id);
          html += `<tr><td>${cell}</td><td>${r.tests}</td><td class="${bad(r.failures)}">${r.failures}</td><td class="${bad(r.errors)}">${r.errors}</td><td>${r.skipped}</td><td>${esc(Number(r.time).toFixed(1))}</td><td>${runAt(r)}</td></tr>`;
        });
        html += `</tbody></table>`;
        const failing = tr.tests.filter(r => r.failing && r.failing.length);
        if (failing.length) {
          html += `<h4>Failing test classes</h4>` + failing.map(r =>
            `<div><strong>${esc(r.id)}</strong><ul>${r.failing.map(f => `<li>${reportLink(f.txt, f.name)}</li>`).join("")}</ul></div>`).join("");
        }
      }
    }
    html += `</section>`;
    html += glossaryBlock(isCov ? COVERAGE_GLOSSARY : TESTS_GLOSSARY);
  } else if (view.section === "logs") {
    html += `<h2 class="screen-title">Code Quality — Run logs</h2>
      <div class="screen-desc">Console logs of the scripts, one table per folder, newest first. A run's ID is the session ID shared by every script one top-level script started; a dash means the run predates session IDs. Links are plain and relative to this page; one opens only when that script has run on this machine.</div>`;
    if (!MODEL.runLogs || MODEL.runLogs.length === 0) {
      html += `<div class="empty-hint">No logs found -- run a script under scripts/ first, then regenerate the map.</div>`;
    } else {
      MODEL.runLogs.forEach(g => {
        const rows = g.runs.map(r => `<tr><td><code>${esc(r.id)}</code></td><td style="white-space:nowrap">${esc(r.startedAt.replace("T", " ").replace(/Z$/, ""))}</td><td>${r.files.map(f => reportLink(f.path, f.path) + (f.note ? ` <span style="opacity:.7">${esc("-- " + f.note)}</span>` : "")).join("<br>")}</td></tr>`).join("");
        html += `<section class="block"><h3>${esc(g.folder)}</h3><table class="simple"><thead><tr><th>Run ID</th><th>Started (UTC)</th><th>Files</th></tr></thead><tbody>${rows}</tbody></table></section>`;
      });
      html += `<section class="block"><h3>Dagu UI</h3><div class="empty-hint"><a href="http://localhost:8082" target="_blank" rel="noopener">http://localhost:8082</a> -- ${esc("live status and run history of the CI runner; opens only while it is up")}</div></section>`;
    }
  } else if (view.section === "sonar") {
    const sonar = MODEL.sonarMetrics;
    html += `<h2 class="screen-title">Code Quality — Sonar Metrics</h2>
      <div class="screen-desc">Real SonarQube metrics per module. Opt-in (<code>--with-sonar</code> on <code>generate-architecture-model.sh</code>), off by default. Raw counts are shown plain; derived ratios are colored (see Overview below for thresholds).</div>`;
    html += `<section class="block"><h3>Sonar Metrics</h3>`;
    if (!sonar) {
      html += `<div class="empty-hint">No data -- regenerate with <code>--with-sonar</code> (requires a reachable SonarQube server).</div>`;
    } else {
      html += `<div class="empty-hint">Source: SonarQube (analysis: ${esc(sonar.analysisDate || "unknown")}).</div>
        <table class="simple"><thead><tr><th>Module</th><th>Java files</th><th>Lines of code</th><th>Complexity</th><th>Complexity/file</th><th>Cognitive complexity</th><th>Cognitive/file</th><th>Code smells</th><th>Code smells/1k LOC</th></tr></thead><tbody>`;
      moduleNodes.forEach(n => {
        const m = sonar.modules && sonar.modules[n.id];
        if (!m) return;
        const perFile = m.javaFileCount > 0 ? m.complexity / m.javaFileCount : 0;
        const cogPerFile = m.javaFileCount > 0 ? m.cognitiveComplexity / m.javaFileCount : 0;
        const smellsPer1k = m.ncloc > 0 ? m.codeSmells / (m.ncloc / 1000) : 0;
        html += `<tr><td>${moduleLink(n.id)}</td><td>${m.javaFileCount}</td><td>${m.ncloc}</td><td>${m.complexity}</td><td>${coloredMetric(perFile, 1, 10, 20)}</td><td>${m.cognitiveComplexity}</td><td>${coloredMetric(cogPerFile, 1, 10, 25)}</td><td>${m.codeSmells}</td><td>${coloredMetric(smellsPer1k, 1, 5, 15)}</td></tr>`;
      });
      html += `</tbody></table>`;
    }
    html += `</section>`;
    html += glossaryBlock(SONAR_GLOSSARY);
  } else if (view.section === "archunit") {
    const arch = MODEL.archUnitMetrics;
    html += `<h2 class="screen-title">Code Quality — ArchUnit</h2>
      <div class="screen-desc">Real ArchUnit metrics per module. Opt-in (<code>--with-archunit</code> on <code>generate-architecture-model.sh</code>), off by default. Raw counts are shown plain; derived ratios are colored (see Overview below for thresholds).</div>`;
    html += `<section class="block"><h3>ArchUnit</h3>`;
    if (!arch) {
      html += `<div class="empty-hint">No data -- regenerate with <code>--with-archunit</code> (requires <code>bash scripts/build-and-test.sh --archunit-metrics</code> to have run at least once).</div>`;
    } else {
      html += `<div class="empty-hint">Source: ArchUnit (from the last <code>bash scripts/build-and-test.sh --archunit-metrics</code> run).</div>
        <table class="simple"><thead><tr><th>Module</th><th>Efferent coupling</th><th>Afferent coupling</th><th>Instability</th><th>Abstractness</th><th>Distance from Main Sequence</th></tr></thead><tbody>`;
      moduleNodes.forEach(n => {
        const m = arch.modules && arch.modules[n.id];
        if (!m) return;
        const distance = Math.abs(m.abstractness + m.instability - 1);
        html += `<tr><td>${moduleLink(n.id)}</td><td>${m.efferentCoupling}</td><td>${m.afferentCoupling}</td><td>${esc(m.instability.toFixed(2))}</td><td>${esc(m.abstractness.toFixed(2))}</td><td>${coloredMetric(distance, 2, 0.3, 0.6)}</td></tr>`;
      });
      html += `</tbody></table>`;
    }
    html += `</section>`;
    html += glossaryBlock(ARCHUNIT_GLOSSARY);
  } else if (view.section === "findings") {
    html += `<h2 class="screen-title">Code Quality — Findings</h2>
      <div class="screen-desc">Repo-wide structural findings, recomputed every generation.</div>`;
    html += renderArchitectureChecksHtml();
    html += renderLargestJavaFilesHtml();
    html += renderConstructorInjectionHtml();
    html += renderGodPackagesHtml();
  } else if (view.section === "practices") {
    html += `<h2 class="screen-title">Code Quality — Best Practices</h2>
      <div class="screen-desc">A project-grounded checklist -- each practice sourced from an authoritative reference and checked against real code in this repo.</div>`;
    html += `<section class="block">${mdBlockToHtml(MODEL.rootBestPractices || "", "")}<div class="empty-hint">Source: ${sourceLink("docs/best-practices.md")}</div></section>`;
  }

  document.getElementById("content").innerHTML = html;
}

// Entities/Key services/Contracts on the Module screen: {name, file?, description?} objects
// (json_named_file_array()) -- real file link when a path was found, a real Javadoc one-liner
// underneath when the class actually carries one (many don't, e.g. a plain @Table entity with no
// class comment -- silently omitted rather than a misleading placeholder).
function renderNamedFileListHtml(items) {
  return `<ul class="info-list">` + items.map(it => {
    const label = it.file ? fileLink(it.file, it.name) : `<code>${esc(it.name)}</code>`;
    const desc = it.description ? `<div class="scope-label" style="margin-top:2px">${esc(it.description)}</div>` : "";
    return `<li>${label}${desc}</li>`;
  }).join("") + `</ul>`;
}

// Opens the Database ERD screen and scrolls to the "db-table-<name>" schema section of that table.
function navigateToTable(tableName) {
  navigate({ screen: "diagrams", groupKey: "04-database-erd", diagramIndex: 0 });
  const el = document.getElementById("db-table-" + tableName);
  if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderModule() {
  const n = byId[view.id];
  if (!n) { navigate({ screen: "system" }); return; }
  let html = `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
      <button onclick="exportModuleMarkdown('${n.id}')">⭳ Export as Markdown</button>
    </div>
    <div class="module-header">
      <h2>${esc(n.id)}</h2>
      <span class="badge ${n.lifecycle}">${n.lifecycle}</span>
      <span class="domain-tag">${esc(n.domain)}</span>
    </div>
    <div class="screen-desc">${esc(n.description || "no one-line description available")}</div>`;

  if (n.tables && n.tables.length) {
    html += `<section class="block"><h3>Tables owned</h3>` + n.tables.map(t => `<span class="table-chip" style="cursor:pointer" onclick="navigateToTable('${esc(t)}')">${esc(t)}</span>`).join("") + `</section>`;
  }
  if (n.entities && n.entities.length) {
    html += `<section class="block"><h3>Entities</h3>${renderNamedFileListHtml(n.entities)}</section>`;
  }
  if (n.keyServices && n.keyServices.length) {
    html += `<section class="block"><h3>Key services</h3>${renderNamedFileListHtml(n.keyServices)}</section>`;
  }
  if (n.contracts && n.contracts.length) {
    html += `<section class="block"><h3>Contracts (Port/Hook)</h3>${renderNamedFileListHtml(n.contracts)}</section>`;
  }

  html += `<section class="block"><h3>Depends on (compile)</h3>${renderDepList(n.edges.DEPENDS_ON_COMPILE, "compile", "No compile-time module dependencies.")}</section>`;
  if ((n.edges.DEPENDS_ON_RUNTIME||[]).length) html += `<section class="block"><h3>Depends on (runtime)</h3>${renderDepList(n.edges.DEPENDS_ON_RUNTIME, "runtime", "")}</section>`;
  if ((n.edges.DEPENDS_ON_OPTIONAL||[]).length) html += `<section class="block"><h3>Depends on (optional)</h3>${renderDepList(n.edges.DEPENDS_ON_OPTIONAL, "optional", "")}</section>`;
  html += `<section class="block"><h3>Depended on by</h3>${renderDepList(n.edges.DEPENDED_ON_BY, "", "Nothing in this repo depends on it directly (leaf module).")}</section>`;

  if (n.readme) {
    html += `<section class="block">${mdBlockToHtml(n.readme, n.id)}<div class="empty-hint">Source: ${sourceLink(`${n.id}/README.md`)}</div></section>`;
  }
  html += `<div class="empty-hint">Evidence: <code class="path">${esc(n.evidence[0].file)}</code></div>`;

  document.getElementById("content").innerHTML = html;
  renderFlowBlocks();
}

