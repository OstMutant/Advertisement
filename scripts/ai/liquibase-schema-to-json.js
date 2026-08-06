#!/usr/bin/env node
// Parses real Liquibase changelog XML (createTable/column/constraints, addForeignKeyConstraint,
// createIndex, addPrimaryKey, plus a narrow regex pass over raw <sql> for CHECK constraints and
// non-Liquibase-native indexes) into JSON for generate-architecture-model.sh's live Database ERD.
// Single source of truth: column/table descriptions live in each changelog's own `remarks=`
// attribute (see platform-commons/CLAUDE.md's sibling convention for *.spi Javadoc, and root
// CLAUDE.md's "Database Changes" guideline) -- never duplicated here or in a separate markdown.
//
// Usage: node liquibase-schema-to-json.js <repoRoot> <file1> [file2 ...]
// Prints one JSON object: { "tables": [...] }

const fs = require("fs");
const path = require("path");

function attr(tag, name) {
  const m = new RegExp(name + '="([^"]*)"').exec(tag);
  return m ? m[1].replace(/&gt;/g, ">").replace(/&lt;/g, "<").replace(/&amp;/g, "&") : null;
}

function parseFile(repoRoot, absFile) {
  const relFile = path.relative(repoRoot, absFile);
  const module = relFile.split(path.sep)[0];
  const xml = fs.readFileSync(absFile, "utf8");
  const tables = [];

  // ── createTable blocks (non-greedy up to the matching closing tag) ──────────────────────────
  const tableRe = /<createTable\s+([^>]*)>([\s\S]*?)<\/createTable>/g;
  let tm;
  while ((tm = tableRe.exec(xml))) {
    const openAttrs = tm[1];
    const body = tm[2];
    const name = attr(openAttrs, "tableName");
    const remarks = attr(openAttrs, "remarks") || "";
    const columns = [];

    // Each <column ...>...</column> or self-closing <column .../>
    const colRe = /<column\s+([^>]*?)(\/>|>([\s\S]*?)<\/column>)/g;
    let cm;
    while ((cm = colRe.exec(body))) {
      const colAttrs = cm[1];
      const colBody = cm[3] || "";
      const constraintsTag = /<constraints\s+([^>]*)\/>/.exec(colBody);
      const cAttrs = constraintsTag ? constraintsTag[1] : "";
      columns.push({
        name: attr(colAttrs, "name"),
        type: attr(colAttrs, "type"),
        remarks: attr(colAttrs, "remarks") || "",
        primaryKey: attr(cAttrs, "primaryKey") === "true",
        nullable: attr(cAttrs, "nullable") !== "false",
        unique: attr(cAttrs, "unique") === "true",
        foreignKeyTable: attr(cAttrs, "references") ? attr(cAttrs, "references").replace(/\([^)]*\)/, "") : null,
        foreignKeyColumn: (() => {
          const ref = attr(cAttrs, "references");
          const m2 = ref ? /\(([^)]+)\)/.exec(ref) : null;
          return m2 ? m2[1] : null;
        })()
      });
    }
    tables.push({ name, module, file: relFile, remarks, columns, foreignKeys: [], indexes: [], compositePrimaryKey: null });
  }

  // ── standalone <addForeignKeyConstraint .../> ────────────────────────────────────────────────
  const fkRe = /<addForeignKeyConstraint\s+([\s\S]*?)\/>/g;
  let fm;
  while ((fm = fkRe.exec(xml))) {
    const a = fm[1];
    const baseTable = attr(a, "baseTableName");
    const t = tables.find(x => x.name === baseTable);
    if (t) {
      t.foreignKeys.push({
        column: attr(a, "baseColumnNames"),
        refTable: attr(a, "referencedTableName"),
        refColumn: attr(a, "referencedColumnNames"),
        onDelete: attr(a, "onDelete") || "RESTRICT"
      });
    }
  }

  // ── <createIndex ...>...<column .../></createIndex> ─────────────────────────────────────────
  const idxRe = /<createIndex\s+([^>]*)>([\s\S]*?)<\/createIndex>/g;
  let im;
  while ((im = idxRe.exec(xml))) {
    const a = im[1];
    const body = im[2];
    const tableName = attr(a, "tableName");
    const t = tables.find(x => x.name === tableName);
    if (!t) continue;
    const cols = [];
    const colRe2 = /<column\s+([^>]*)\/>/g;
    let cm2;
    while ((cm2 = colRe2.exec(body))) cols.push(attr(cm2[1], "name"));
    t.indexes.push({ name: attr(a, "indexName"), columns: cols, unique: attr(a, "unique") === "true" });
  }

  // ── <addPrimaryKey tableName=... columnNames=.../> -- composite PKs ────────────────────────
  const pkRe = /<addPrimaryKey\s+([^>]*)\/>/g;
  let pm;
  while ((pm = pkRe.exec(xml))) {
    const a = pm[1];
    const t = tables.find(x => x.name === attr(a, "tableName"));
    if (t) t.compositePrimaryKey = attr(a, "columnNames").split(",").map(s => s.trim());
  }

  // ── raw <sql> blocks -- narrow regex pass for CHECK constraints and non-Liquibase-native
  //    indexes (partial/GIN), the two patterns actually used across these 6 files. ─────────────
  const sqlRe = /<sql>([\s\S]*?)<\/sql>/g;
  let sm;
  while ((sm = sqlRe.exec(xml))) {
    const sql = sm[1];
    const chk = /ALTER TABLE\s+(\w+)[\s\S]*?ADD CONSTRAINT\s+(\w+)[\s\S]*?CHECK\s*\(([\s\S]*?)\)\s*;/.exec(sql);
    if (chk) {
      const t = tables.find(x => x.name === chk[1]);
      if (t) t.indexes.push({ name: chk[2], check: chk[3].trim() });
    }
    const idx = /CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF NOT EXISTS\s+)?(\w+)\s+ON\s+(\w+)\s*(?:USING\s+(\w+)\s*)?\(([^)]+)\)\s*(WHERE[^;]+)?/.exec(sql);
    if (idx) {
      const t = tables.find(x => x.name === idx[3]);
      if (t) {
        t.indexes.push({
          name: idx[2],
          columns: idx[5].split(",").map(s => s.trim()),
          unique: !!idx[1],
          using: idx[4] || null,
          where: idx[6] ? idx[6].trim() : null
        });
      }
    }
  }

  return tables;
}

const [, , repoRoot, ...files] = process.argv;
const allTables = [];
for (const f of files) allTables.push(...parseFile(repoRoot, f));
process.stdout.write(JSON.stringify(allTables));
