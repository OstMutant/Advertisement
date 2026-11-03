/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Runs a list of cases and prints one PASS or FAIL line per case plus a summary.
 * Usage: None -- a library module imported by the *.test.mjs files in this folder.
 * Uses: None.
 * Env: None.
 * Input: a list of cases {name, ...} and an optional evaluate function returning {ok, detail}.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the detail or the thrown error.
 * Returns: does not return: ends the process, exit code 0 when every case passed, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
export async function runCases(cases, evaluate = async (testCase) => ({ ok: await testCase.check() })) {
  let failures = 0;
  for (const testCase of cases) {
    let ok = false;
    let detail = '';
    try {
      ({ ok, detail = '' } = await evaluate(testCase));
    } catch (error) {
      detail = ` (error=${error.message})`;
    }
    failures += ok ? 0 : 1;
    console.log(`${ok ? 'PASS' : 'FAIL'} ${testCase.name}${ok ? '' : detail}`);
  }
  console.log(`${cases.length - failures}/${cases.length} passed`);
  process.exit(failures === 0 ? 0 : 1);
}
