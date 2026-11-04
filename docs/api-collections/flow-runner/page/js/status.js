/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Maps a response status code to a CSS class name and a human-readable label, used by the
 *   diagram and the step views.
 * Usage: None -- a library module imported by diagram.js and step-view.js.
 * Uses: None.
 * Env: None.
 * Input: A response status code (number, or null when there is no response) passed to codeClass and
 *   codeLabel.
 * Outputs: Exports codeClass and codeLabel. No DOM, network or storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

/**
 * Maps a response status code to a CSS class name.
 * Arguments: code -- HTTP status code number, or null when there is no response.
 * Outputs: None.
 * Returns: CSS class name: code-2xx, code-3xx, code-4xx, code-5xx or code-none.
 */
export function codeClass(code) {
  if (code === null) {
    return "code-none";
  }
  if (code >= 500) {
    return "code-5xx";
  }
  if (code >= 400) {
    return "code-4xx";
  }
  if (code >= 300) {
    return "code-3xx";
  }
  if (code >= 200) {
    return "code-2xx";
  }
  return "code-none";
}

/**
 * Maps a response status code to a readable label such as "404 client error".
 * Arguments: code -- HTTP status code number, or null when there is no response.
 * Outputs: None.
 * Returns: Label string; "no response" for null, the bare number for codes below 200.
 */
export function codeLabel(code) {
  if (code === null) {
    return "no response";
  }
  if (code >= 500) {
    return `${code} server error`;
  }
  if (code >= 400) {
    return `${code} client error`;
  }
  if (code >= 300) {
    return `${code} redirect`;
  }
  if (code >= 200) {
    return `${code} success`;
  }
  return String(code);
}
