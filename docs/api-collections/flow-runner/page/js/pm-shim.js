export function createPm(response, variables) {
  const tests = [];
  const pm = {
    test(name, fn) {
      try {
        fn();
        tests.push({ name, passed: true, error: null });
      } catch (error) {
        tests.push({ name, passed: false, error: error.message });
      }
    },
    expect(actual) {
      return strict(
        {
          to: {
            include(expected) {
              if ((Array.isArray(actual) || typeof actual === "string") && actual.includes(expected)) {
                return;
              }
              throw new Error(`expected ${JSON.stringify(actual)} to include ${JSON.stringify(expected)}`);
            },
            be: {
              a(type) {
                const matches = type === "array" ? Array.isArray(actual) : typeof actual === type;
                if (!matches) {
                  throw new Error(`expected ${JSON.stringify(actual)} to be a ${type}`);
                }
              },
            },
          },
        },
        "pm.expect",
      );
    },
    response: {
      code: response.code,
      json: () => JSON.parse(response.bodyText),
      to: {
        have: {
          status(expected) {
            if (response.code !== expected) {
              throw new Error(`expected status ${expected} but got ${response.code}`);
            }
          },
        },
      },
    },
    collectionVariables: {
      set(name, value) {
        variables[name] = String(value);
      },
      get(name) {
        return variables[name];
      },
    },
  };
  return { pm: strict(pm, "pm"), tests };
}

export function runScript(source, pm) {
  new Function("pm", source)(pm);
}

function strict(target, path) {
  return new Proxy(target, {
    get(obj, prop) {
      if (typeof prop === "symbol" || prop === "then") {
        return undefined;
      }
      if (!Object.hasOwn(obj, prop)) {
        throw new Error(`unsupported pm call: ${path}.${prop}`);
      }
      const value = obj[prop];
      return isPlainObject(value) ? strict(value, `${path}.${prop}`) : value;
    },
  });
}

function isPlainObject(value) {
  return typeof value === "object" && value !== null && Object.getPrototypeOf(value) === Object.prototype;
}
