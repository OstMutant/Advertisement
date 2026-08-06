#!/usr/bin/env node
// Description: Parses a module's DECISIONS.md into structured ADR data for embedding directly
//   into architecture-model.json -- no separate DECISIONS.json file, no hand-copied ADR prose.
// Uses: Node.js (no external dependencies -- plain string/regex parsing, no markdown library).
// Input: a module's DECISIONS.md file (module name passed as CLI arg).
// Output: `{title, adrs:[{id,title,status,body}], extra:[{heading,body}]}` -- printed to stdout
//   (--stdout mode, consumed by generate-architecture-model.sh) or written to
//   <module>/DECISIONS.json (batch mode).
//
// Parses a module's DECISIONS.md into structured data -- {title, adrs:[{id,title,status,body}],
// extra:[{heading,body}]} -- see scripts/architecture/DECISIONS.md for the design history.
// Replaces an earlier awk-based version that hit two real bugs on real content (label+list with
// no blank line between merging into one paragraph; multi-line list items losing their numbering)
// -- JSON.stringify() gives correct escaping by construction and normal string/regex methods
// handle block parsing far more reliably than a hand-rolled awk state machine.
//
// Usage:
//   node scripts/architecture/md-to-decisions-json.js <module> [<module> ...]        -- writes <module>/DECISIONS.json
//   node scripts/architecture/md-to-decisions-json.js --stdout <module>              -- prints one module's
//     parsed object as a single line of JSON to stdout, for embedding directly into
//     architecture-model.json (no separate <module>/DECISIONS.json file, no runtime file:// load
//     -- see scripts/architecture/DECISIONS.md ADR-008 for why the separate-file/<script src> design was
//     abandoned: it depends on browser-specific file:// security policy, an unacceptable
//     dependency for a tool meant to just work when double-clicked).

const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..", "..");

function parseDecisionsMarkdown(text) {
  const titleMatch = text.match(/^#\s+(.+)$/m);
  const title = titleMatch ? titleMatch[1].trim() : "";

  // Split on every top-level "## " heading, keeping the heading text with its body.
  const sectionRe = /^##\s+(.+)$/gm;
  const sections = [];
  let match;
  const indices = [];
  while ((match = sectionRe.exec(text)) !== null) {
    indices.push({ heading: match[1].trim(), start: match.index, headingEnd: sectionRe.lastIndex });
  }
  for (let i = 0; i < indices.length; i++) {
    const { heading, headingEnd } = indices[i];
    const end = i + 1 < indices.length ? indices[i + 1].start : text.length;
    let body = text.slice(headingEnd, end);
    // Trim a leading "---" horizontal rule left over from the previous section's own trailing
    // separator, then trim leading/trailing blank lines and any trailing "---".
    body = body.replace(/\r\n/g, "\n");
    const lines = body.split("\n");
    while (lines.length && lines[lines.length - 1].trim() === "") lines.pop();
    while (lines.length && lines[lines.length - 1].trim() === "---") {
      lines.pop();
      while (lines.length && lines[lines.length - 1].trim() === "") lines.pop();
    }
    while (lines.length && lines[0].trim() === "") lines.shift();
    body = lines.join("\n");
    sections.push({ heading, body });
  }

  const adrs = [];
  const extra = [];
  const adrHeadingRe = /^(ADR-\d+):\s*(.+)$/;
  for (const { heading, body } of sections) {
    const adrMatch = heading.match(adrHeadingRe);
    if (!adrMatch) {
      extra.push({ heading, body });
      continue;
    }
    const id = adrMatch[1];
    const adrTitle = adrMatch[2].trim();
    let status = "";
    let rest = body;
    const statusMatch = body.match(/^\*\*Status:\*\*\s*(.*)$/m);
    if (statusMatch && body.trimStart().startsWith("**Status:**")) {
      status = statusMatch[1].trim();
      rest = body.slice(body.indexOf(statusMatch[0]) + statusMatch[0].length);
      rest = rest.replace(/^\n+/, "");
    }
    adrs.push({ id, title: adrTitle, status, body: rest });
  }

  return { title, adrs, extra };
}

function convertModule(module) {
  const mdPath = path.join(repoRoot, module, "DECISIONS.md");
  if (!fs.existsSync(mdPath)) {
    console.error(`skip ${module}: no DECISIONS.md`);
    return;
  }
  const text = fs.readFileSync(mdPath, "utf8");
  const data = parseDecisionsMarkdown(text);
  const jsonPath = path.join(repoRoot, module, "DECISIONS.json");
  const out = `window.DECISIONS_DATA = window.DECISIONS_DATA || {};\n` +
    `window.DECISIONS_DATA[${JSON.stringify(module)}] = ${JSON.stringify(data, null, 2)};\n`;
  fs.writeFileSync(jsonPath, out);
  console.error(`wrote ${module}/DECISIONS.json (${data.adrs.length} ADRs, ${data.extra.length} extra sections)`);
}

function printModule(module) {
  const mdPath = path.join(repoRoot, module, "DECISIONS.md");
  if (!fs.existsSync(mdPath)) { process.stdout.write("null"); return; }
  const data = parseDecisionsMarkdown(fs.readFileSync(mdPath, "utf8"));
  process.stdout.write(JSON.stringify(data));
}

const args = process.argv.slice(2);
if (args[0] === "--stdout") {
  const module = args[1];
  if (!module) { console.error("usage: node md-to-decisions-json.js --stdout <module>"); process.exit(1); }
  printModule(module);
} else {
  if (args.length === 0) {
    console.error("usage: node md-to-decisions-json.js <module> [<module> ...]");
    process.exit(1);
  }
  args.forEach(convertModule);
}
