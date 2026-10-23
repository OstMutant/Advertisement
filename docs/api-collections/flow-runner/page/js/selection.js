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
