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
