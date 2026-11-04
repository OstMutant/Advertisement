/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Holds the run selection: which flows and steps are switched off, and the variable overrides.
 *   Notifies subscribers on every change and supports loading and reading the whole state.
 * Usage: None -- a library module imported by main.js.
 * Uses: None.
 * Env: None.
 * Input: Flow names, step ids, override values and state objects passed to the returned methods.
 * Outputs: Exports createSelection. Changes the in-memory state and calls every subscribed listener
 *   synchronously. No DOM or storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

export function createSelection() {
  const flowsOff = new Set();
  const stepsOff = new Set();
  const overrides = {};
  const listeners = [];

  function notify() {
    listeners.forEach((listener) => listener());
  }

  function toggle(set, key, on) {
    if (on) {
      set.delete(key);
    } else {
      set.add(key);
    }
    notify();
  }

  return {
    overrides,
    flowOn: (name) => !flowsOff.has(name),
    stepOn: (id) => !stepsOff.has(id),
    setFlow: (name, on) => toggle(flowsOff, name, on),
    setStep: (id, on) => toggle(stepsOff, id, on),
    excludedSteps: () => [...stepsOff],
    state: () => ({ flowsOff: [...flowsOff], stepsOff: [...stepsOff], overrides: { ...overrides } }),
    load(next) {
      flowsOff.clear();
      next.flowsOff.forEach((name) => flowsOff.add(name));
      stepsOff.clear();
      next.stepsOff.forEach((id) => stepsOff.add(id));
      Object.keys(overrides).forEach((key) => delete overrides[key]);
      Object.assign(overrides, next.overrides);
      notify();
    },
    setValues(values) {
      Object.assign(overrides, values);
      notify();
    },
    clearValues(keys) {
      keys.forEach((key) => delete overrides[key]);
      notify();
    },
    subscribe(listener) {
      listeners.push(listener);
    },
  };
}
