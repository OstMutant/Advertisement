# ── Header ──────────────────────────────────────────────────────────────────
# Description: Generation environment (Node.js + Python 3) for architecture-doc.sh --
#   generate-architecture-model.sh needs both unconditionally (Node for Liquibase-XML-to-JSON
#   parsing and JSON-safe multi-line file reads, Python for script_headers_json()'s header
#   parsing), and neither is assumed present on the host.
# Usage: docker build -f docs/architecture/scripts/Dockerfile -t arch-doc-tools .
# Uses: node:22-bookworm-slim base image.
# Env: LANG/LC_ALL/PYTHONIOENCODING set to UTF-8 -- the base image's default locale is not UTF-8,
#   which breaks Python's own file reads on any non-ASCII byte (em-dashes, etc.) already common in
#   this repo's own prose.
# Input: none (self-contained).
# Outputs: arch-doc-tools image (Node.js + Python 3, UTF-8 locale).
# ────────────────────────────────────────────────────────────────────────────
FROM node:22-bookworm-slim

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONIOENCODING=utf-8

RUN apt-get update && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
