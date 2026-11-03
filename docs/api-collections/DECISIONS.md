# Architecture & Technical Decisions — docs/api-collections

---

## ADR-004: MCP flows are a separate Postman collection run by the shared runner, cover every tool of the server, and make sure the default administrator exists before the flows, and skip the flows that need an administrator when it is missing

**Status:** Accepted
**Verified:** 2026-11-02
**Applied:** 2

**Also affects:** marketplace-mcp

**Context:** The MCP server exposes a tool for every REST operation, reads and writes, and had no catalog in the browser, no flows and no check that a new tool has a flow. The REST collection is the collection of the REST API, its requests are named after REST operation ids and its checker compares them with the OpenAPI document. MCP tool names equal those operation ids, so one shared file would blur both coverage reports, and a failed MCP step would stop the REST flows after it. The MCP endpoint needs a session and answers some requests as JSON and others as an event stream. The first account ever registered on an empty database becomes the administrator, so a flow user registered first is not a plain user and the steps that expect a plain user's 403 fail there. A version read from an `etag` field carries quotes that break the JSON of the next call.

**Decision:**
1. The MCP flows live in their own Postman v2.1 file. Every MCP call is a plain HTTP POST with a JSON-RPC body to the MCP endpoint, named after the tool (`tools/call`) or the JSON-RPC method. Test scripts read an answer with one line that handles both JSON and an event stream. The test-script layer stays a strict subset of Postman; only `pm.response.headers.get` and `pm.response.text` were added, and a check throws instead of using a chained assertion the runner does not know.
2. The MCP flows have their own page with a tool catalog built live from `tools/list` and the flow runner. Both pages start the same runner modules through `startRunnerPage(profile)`; each page's script holds its own settings and no shared module keeps a REST constant.
3. The coverage check is the existing checker in an MCP mode chosen by `"source": "mcp"` in a second config. Its operations are the tool names from the live `tools/list`; every tool needs a `tools/call` step named after it or a line in a separate allow-list file. The allow-list file is empty and holds only a gap to close. With `referenceCollectionFile` in the MCP config the check also requires every tool to be exercised at least at the role at which the REST collection exercises the same operation.
4. The Setup flow first logs in as the default administrator and registers it when the login fails (a step that runs only when the run variable `adminExists` is `no`, as in the REST collection), so on an empty database the first account is the administrator and every flow user is a plain user. It then registers a fresh user, issues that user's key through REST and opens the session. Setup keeps the administrator's key as `adminKey`; after `tools/list` the step `setup.rights` reads the default account through the tool `getMe` and sets `adminRights`, as in the REST collection. The Taxons and Moderation flows start with `when: adminRights=yes`, register a moderator themselves (helper steps, promoted with the administrator's key) and send `moderatorKey`, the same name as in the REST collection because the role of a flow is read from that name; without an administrator they are skipped. The Administration flow does the administrator's own work (roles, other accounts, history, audit) on a target user it registers and deletes and starts with the same flow condition. The other flows use only plain users with their own keys: the flow user and, where a flow needs another actor, a second user the flow registers and deletes. The last flow closes the session; the page opens a new session when the server answers 404 and closes it when the page is hidden.
5. A version a step takes from an `etag` field is stored without its quotes; a version from `item.version` is stored as is.

**Rejected alternatives:** MCP requests inside the REST collection (mixes two coverage reports, a failed MCP step stops later REST flows, breaks the "collection of the REST API" meaning); a second runner with its own code (about 1,200 duplicated lines and two runners that drift apart); Postman's native MCP request type (cannot be saved in the v2.1 format the runner reads); MCP Inspector as the only catalog (a separate process, against serving developer pages from the API's own address behind the switch); creating a taxon with an administrator key for the success path of `getTaxon` (copies the administrator steps); keeping the flows free of any administrator (on an empty database the flow user becomes the administrator and the expected-403 steps fail); making those expectations depend on the role of the flow user (the flows then check different things on an empty and on a used database); a step that registers an e-mail twice to check the conflict (it counts as a failed registration and a few runs in a row lock registration for fifteen minutes; the tool's unit test covers the text).

---

## ADR-003: The flow runner page is served only behind a property, from the REST module's classpath, and is never switched on by a file a real deployment reuses

**Status:** Accepted
**Applied:** 3

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

## ADR-002: API collection flows repeat on an empty or a used database, create what they need, never provoke a limiter, and run by role

**Status:** Accepted
**Verified:** 2026-11-02

**Applied:** 5

**Context:** The flow runner (the developer-only page and the headless scripts) runs the Postman
collection's flows against a live developer app again and again, and the database is never
reset between runs. Registering an account whose e-mail already exists answers 409 and counts as
a failed attempt per client address; after five such attempts registration answers 429 for fifteen
minutes. A failed administrator login (401) counts toward a separate login limiter. The first
account ever registered becomes the administrator, and the API offers no way to create an
administrator once any account exists. Flows that create categories, cities, titles or e-mails
need values that do not collide between runs. The limiters themselves are documented with the
user starter and are not changed here. Some flows act as a moderator or an administrator, so a default account that is not an administrator cannot run them; that is a normal case, not an error.

**Decision:**
1. A flow passes on an empty database and on a used one, and can be run again without a reset.
2. Everything that must be unique is built from a per-run `runId`, generated by the runner and
   overridable by the caller.
3. A flow works only with data it creates itself, except the default administrator account
   (`email`/`password` variables), which it creates when missing.
4. The default administrator is handled by conditional steps: the first step issues an API key for the
   default administrator and accepts 201 or 401; on 401 the registration step runs (`when:
   adminExists=no` in the request description, a step runs only when the named run variable
   equals the value, otherwise it is skipped as "condition not met"), so on an empty database the
   administrator is registered before any other account. The step `auth.rights` then reads the default
   account (`getMe`) and sets the run variable `adminRights` to `yes` only when its role is administrator.
   A flow that needs an administrator starts its folder description with `when: adminRights=yes`; without
   an administrator its steps are skipped as "flow condition not met", never failed, and the runner and
   the page name those flows and say that the e-mail and password of an existing administrator can be
   passed. No REST call finds or makes an administrator, so the collection never repairs the database and
   never wipes it.
5. A flow never provokes a limiter: it registers no account that already exists and uses fresh
   e-mails; the single failed login happens only when the account is missing.
6. What a flow creates it deletes again when the API offers a delete; otherwise the object is
   named with the `runId`.
7. A step whose correct result is a non-2xx status is a real check with its expected status; an
   unexpected 429 is reported with its `Retry-After`, not as a failed assertion.

8. These rules apply to the MCP collection too: its flows pass on an empty or a used database, create their own user and key, delete the advertisement and profile they create, never provoke a limiter, and the last flow closes the MCP session; no step registers an existing e-mail.
9. The role of a flow is the highest role among its steps that are not helpers; the role of a step is read from the key variable it sends (`{{adminKey}}` administrator, `{{moderatorKey}}` moderator, any other key or none user), and these names are declared once in `roles.js`. A step marked `helper: yes` only prepares or removes data (a category, a city, the moderator the flow registers and promotes with the administrator's key) and does not count. `--role` runs the flows of that role and of the roles below it, `--each-role` runs every role in turn, and the page templates Administrator, Moderator and User are the same lists; the collection check reports a step with a role key in a flow that has no flow condition.

**Rejected alternatives:** registering the existing accounts on every run and accepting the 409
(trips the registration limiter); loosening the limiters in the dev profile (hides behaviour the
API keeps in every other environment); keeping the administrator registration off by default and
resetting the database before runs (the flow then cannot run on a used database and depends on an
external reset); a separate probe request before the registration (basic authentication is
accepted only where the key is issued, so the probe is that request itself). A hand-typed list of flows per role in the config and in the page (two copies that drift apart); stopping the run when the default account is not an administrator (an ordinary or moderator account could run nothing); a probe through `listUsers` (a moderator passes it, then the steps that need the administrator fail with 403 instead of being skipped); a step that registers an existing e-mail to check the conflict (it counts as a failed registration and a few runs in a row lock registration for fifteen minutes, the conflict text is covered by the tool's unit test).

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
