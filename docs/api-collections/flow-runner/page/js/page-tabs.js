/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Section tabs of a runner page: shows exactly one panel at a time and marks the matching tab selected.
 * Usage: None -- a library module imported by rest-page.js and mcp-page.js.
 * Uses: global document.
 * Env: None.
 * Input: The tablist element whose role=tab buttons carry data-panel (the id of the panel they show).
 * Outputs: Toggles the hidden attribute of the panels and aria-selected of the buttons on each click; the first button
 *   with aria-selected=true decides the panel shown at start.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

export function setupSectionTabs(tablist) {
  const tabs = [...tablist.querySelectorAll("button[role='tab'][data-panel]")];
  const select = (selected) => {
    for (const tab of tabs) {
      tab.setAttribute("aria-selected", String(tab === selected));
      document.getElementById(tab.dataset.panel).hidden = tab !== selected;
    }
  };
  for (const tab of tabs) {
    tab.addEventListener("click", () => select(tab));
  }
  select(tabs.find((tab) => tab.getAttribute("aria-selected") === "true") ?? tabs[0]);
}
