# Architecture & Technical Decisions — docs/api-collections

---

## ADR-004: MCP flows are a separate Postman collection of plain HTTP JSON-RPC requests, run by the shared runner on their own page, with a coverage check against the live tools/list

**Status:** Accepted
**Applied:** 1

**Also affects:** marketplace-mcp

**Context:** The MCP server has eleven tools but had no catalog in the browser, no flows and no check that a new tool has a flow. The REST collection is the collection of the REST API, its requests are named after REST operation ids and its checker compares them with the OpenAPI document. MCP tool names equal those operation ids, so one shared file would blur both coverage reports, and a failed MCP step would stop the REST flows after it. The MCP endpoint needs a session and answers some requests as JSON and others as an event stream.

**Decision:**
1. The MCP flows live in their own Postman v2.1 file. Every MCP call is a plain HTTP POST with a JSON-RPC body to the MCP endpoint, named after the tool (`tools/call`) or the JSON-RPC method. Test scripts read an answer with one line that handles both JSON and an event stream. The test-script layer stays a strict subset of Postman; only `pm.response.headers.get` and `pm.response.text` were added.
2. The MCP flows have their own page with a tool catalog built live from `tools/list` and the flow runner. Both pages start the same runner modules through `startRunnerPage(profile)`; each page's script holds its own settings and no shared module keeps a REST constant.
3. The coverage check is the existing checker in an MCP mode chosen by `"source": "mcp"` in a second config. Its operations are the tool names from the live `tools/list`; exceptions live in a separate allow-list file.
4. The flows obtain their key themselves: the Setup flow registers a fresh user, issues a key through REST and opens the session, so no administrator is needed. The last flow closes the session; the page opens a new session when the server answers 404 and closes it when the page is hidden.

**Rejected alternatives:** MCP requests inside the REST collection (mixes two coverage reports, a failed MCP step stops later REST flows, breaks the "collection of the REST API" meaning); a second runner with its own code (about 1,200 duplicated lines and two runners that drift apart); Postman's native MCP request type (cannot be saved in the v2.1 format the runner reads); MCP Inspector as the only catalog (a separate process, against serving developer pages from the API's own address behind the switch); creating a taxon with an administrator key for the success path of `getTaxon` (copies the administrator steps; the unknown-id check plus a success call when a taxon exists is enough).

---

## ADR-003: The flow runner page is served only behind a property, from the REST module's classpath, and is never switched on by a file a real deployment reuses

**Status:** Accepted
**Applied:** 2

**Also affects:** marketplace-rest-api, marketplace-mcp

**Context:** The flow runner page runs the Postman collection's flows against a live app and creates users, API keys and data while doing so, so it must not be reachable in a real deployment. The page calls the API from the browser, and the API sends no CORS headers, so the page has to come from the same origin as the API. The application is one deployable jar. The local Docker stack starts that jar with the `prod` profile, so a setting that only the `dev` profile switches on would leave the page unreachable there.

**Decision:**
1. The page and the Postman collection are served by `DevToolsConfig` in `marketplace-rest-api` under `/dev-tools/api-flows/`, and only when `marketplace.dev-tools.enabled` is `true`. With the property off the path is not registered at all and answers 404; it is not merely closed by authorization.
2. The property is off by default. The `dev` profile turns it on. For the local Docker stack `deploy-and-run.sh --dev-tools` passes `MARKETPLACE_DEV_TOOLS_ENABLED=true` to the container; the variable is never written into a compose file, an image or the `prod` profile that a real deployment reuses.
3. The files are not copied by hand: the module's build packs `docs/api-collections/flow-runner/page` and the Postman collection into the classpath, so `docs/api-collections` stays the single source and no generated copy exists.
4. The page and the headless Node scripts share the same runner modules, so the two cannot drift apart.
5. The MCP page and its Postman collection are served by the same `DevToolsConfig` from the same path, behind the same property and from the same packed folders; there is no new property.

**Rejected alternatives:** a separate static server for the page (needs CORS on the API and one more process to start); serving the page through a Vaadin route (puts a Vaadin dependency into the REST adapter and mixes it into the UI security chain); switching it on by profile alone (the local Docker stack runs `prod`); hiding the page behind authorization instead of not registering the path (the path would exist in every deployment).

---

## ADR-002: API collection flows repeat on an empty or a used database, create what they need, never provoke a limiter

**Status:** Accepted

**Applied:** 4

**Context:** The flow runner (the developer-only page and the headless scripts) runs the Postman
collection's flows against a live developer app again and again, and the database is never
reset between runs. Registering an account whose e-mail already exists answers 409 and counts as
a failed attempt per client address; after five such attempts registration answers 429 for fifteen
minutes. A failed administrator login (401) counts toward a separate login limiter. The first
account ever registered becomes the administrator, and the API offers no way to create an
administrator once any account exists. Flows that create categories, cities, titles or e-mails
need values that do not collide between runs. The limiters themselves are documented with the
user starter and are not changed here.

**Decision:**
1. A flow passes on an empty database and on a used one, and can be run again without a reset.
2. Everything that must be unique is built from a per-run `runId`, generated by the runner and
   overridable by the caller.
3. A flow works only with data it creates itself, except the default administrator account
   (`email`/`password` variables), which it creates when missing.
4. The administrator is handled by a conditional step: the first step issues an API key for the
   default administrator and accepts 201 or 401; on 401 the registration step runs (`when:
   adminExists=no` in the request description, a step runs only when the named run variable
   equals the value, otherwise it is skipped as "condition not met"), so on an empty database the
   administrator is registered before any other account. The first step of the Taxons flow
   (`taxons.probe`) lists users with the administrator's key and stops the run with a message that
   names the account and asks for the e-mail and password of an existing administrator (the variables
   `email` and `password`) when that fails. No REST call finds or makes an administrator, so the
   collection never repairs the database and never wipes it. A run by role (`--role user`, defined in
   the config) covers only the flows that need no administrator: Authentication, Infrastructure and
   Account.
5. A flow never provokes a limiter: it registers no account that already exists and uses fresh
   e-mails; the single failed login of the probe happens only when the account is missing.
6. What a flow creates it deletes again when the API offers a delete; otherwise the object is
   named with the `runId`.
7. A step whose correct result is a non-2xx status is a real check with its expected status; an
   unexpected 429 is reported with its `Retry-After`, not as a failed assertion.

8. These rules apply to the MCP collection too: its flows pass on an empty or a used database, create their own user and key, delete the advertisement and profile they create, never provoke a limiter, and the last flow closes the MCP session.

**Rejected alternatives:** registering the existing accounts on every run and accepting the 409
(trips the registration limiter); loosening the limiters in the dev profile (hides behaviour the
API keeps in every other environment); keeping the administrator registration off by default and
resetting the database before runs (the flow then cannot run on a used database and depends on an
external reset); a separate probe request before the registration (basic authentication is
accepted only where the key is issued, so the probe is that request itself).

---

## ADR-001: API collection flows repeat without a reset, build unique values per run and never provoke a limiter

**Status:** Superseded by ADR-002
**Applied:** 1

**Context:** The flow runner (the developer-only page and the headless scripts) runs the Postman
collection's flows against a live developer app again and again, without resetting the database.
The first Authentication flow registered the administrator and a user on every run; the app counts
each registration with an already-registered e-mail (409) as a failed attempt per client address,
so after a few repeats registration answered 429 for fifteen minutes and the flow failed at its
first step. The same class of problem applies to every value that must be unique (e-mails,
category and city names, titles). The limiter itself is documented with the user starter and is
not changed here.

**Decision:**
1. A flow can be run again without a database reset and still passes.
2. Everything that must be unique is built from a per-run `runId`, generated by the runner and
   overridable by the caller.
3. A flow never provokes a limiter: it registers no account that already exists and uses fresh
   e-mails.
4. The administrator is the environment's existing one; its registration step carries
   `default: off` in the request description and runs only when explicitly included.
5. What a flow creates it deletes again when the API offers a delete; otherwise the object is
   named with the `runId`.
6. A step whose correct result is a non-2xx status is a real check with its expected status; an
   unexpected 429 is reported with its `Retry-After`, not as a failed assertion.

**Rejected alternatives:** registering the existing accounts on every run and accepting the 409
(the first version, it trips the limiter); loosening the limiter in the dev profile (hides
behaviour the API keeps in every other environment); probing first and registering only when
missing (needs conditional steps in the runner, which has none).
