/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Minimal Postman pm object for running collection test scripts in the browser: pm.test,
 *   pm.expect (to.include, to.be.a), pm.response (code, json, to.have.status) and
 *   pm.collectionVariables. Any unsupported pm call throws. runScript evaluates a test script
 *   source against that pm object.
 * Usage: None -- a library module imported by runner.js.
 * Uses: None (JavaScript built-ins only: Function, Proxy).
 * Env: None.
 * Input: A response object with code and bodyText, and a variables object, passed to createPm; a
 *   script source and a pm object passed to runScript.
 * Outputs: Exports createPm and runScript. collectionVariables.set writes into the variables object
 *   passed to createPm. No DOM, network or storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

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
