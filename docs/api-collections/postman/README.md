# postman/

The Postman collections, in Postman collection format v2.1: [`marketplace-rest-api.postman_collection.json`](marketplace-rest-api.postman_collection.json) for the REST API and [`marketplace-mcp.postman_collection.json`](marketplace-mcp.postman_collection.json) for the MCP server. Their folders are flows: each folder holds the requests of one flow, run in
the order they are listed. The flow runner page and the collection test scripts both read these files.

## Using it in Postman

Import: File → Import → `marketplace-rest-api.postman_collection.json`. Send a single request, or
right-click a folder → Run folder to run its whole flow. Requests to `localhost` work from the
Postman desktop app; the web app needs the Postman Agent.

A Postman Flow built on these requests is not stored in this file: a Flow cannot be exported or
imported as a file.

The collection describes the API as it is today. After an API change, re-import the OpenAPI document
at `/v3/api-docs` rather than editing requests blindly.

## Requests and steps

Every request is named by the REST `operationId` of the operation it calls. One operation may appear
in several requests, so the same name can occur more than once in a folder.

A request's description carries the flow-runner metadata as header lines, followed by a blank line and
free text:

```
step: <id>
label: <short name shown in the flow runner, at most 24 characters>
kind: required|optional
when: <variable>=<value>
helper: yes

<free text>
```

- `step` identifies the step within the flow.
- `label` is shown in the flow runner; at most 24 characters.
- `kind` is one of `required` or `optional`.
- `when` is optional; `when: <variable>=<value>` runs the step only when that run variable has that value, otherwise the step is skipped as "condition not met".
- `helper` is optional; `helper: yes` marks a step that only prepares or cleans up data (a taxon, a moderator), so it does not count towards the role of its flow.

A folder's own description is the flow note. Its first line may be `when: <variable>=<value>`; the flow then runs only when that run variable has that value, otherwise every step of the flow is skipped as "flow condition not met".

## Roles and running without an administrator

The role of a flow is the highest role among its steps that are not helpers. The role of a step comes from the key it sends: `{{adminKey}}` is an administrator, `{{moderatorKey}}` a moderator, any other key or none a user.

The step `auth.rights` of the Authentication flow reads the default account with its key (`getMe`) and sets the run variable `adminRights` to `yes` when its role is administrator and to `no` otherwise. The flows that need an administrator start with `when: adminRights=yes`, so with an account that is not an administrator they are skipped, not failed, and the runner prints how to run them: pass the e-mail and password of an existing administrator with `--variable email=<e-mail> --variable password=<password>`.

A request body of mode `formdata` is sent as `multipart/form-data`. A `text` entry uses `value`. A
`file` entry (`type: file`) carries its content in `src` as a data URL, `data:<content type>;base64,<content>`,
with an optional `fileName` (default `file`), because the flow runner has no file system. Variables are
substituted in `value`, `src` and `fileName`.

## Collection variables

Defaults stored in the collection file. Requests read them as `{{name}}`, so no Postman environment
has to be selected.

| Variable | Default |
|---|---|
| `host` | `http://localhost:8081` |
| `email` | `admin.en@example.com` |
| `password` | `password` |
| `adminName` | `Admin EN` |
| `userName` | `Flow User` |
| `userEmailPrefix` | `flow.user` |
| `runId` | `1` (the flow runner generates a fresh value per run) |
| `userPassword` | `password123` |
| `apiKeyLabel` | `flow-runner` |
| `secondKeyLabel` | `flow-runner-second` |
| `profileKind` | `MASTER`, the kind of the provider profile the flows create |
| `profilePhone` | `+380501234567`, the phone number of that profile |
| `adKind` | `OFFER`, the kind of the advertisements the flows create |
| `settingsPageSize` | `30`, the page size the Account flow saves into the caller's settings |
| `pixelPng` | base64 of a 1x1 PNG, used for gallery uploads |
| `videoUrl` | a YouTube link, used for gallery video links |

`userEmailPrefix` and `runId` build the ordinary user's e-mail, so that repeated runs create no
duplicates.

## Variables captured at run time

The test scripts of the requests store values in collection variables for later requests in the same
run:

| Variable | Set by | Holds |
|---|---|---|
| `userId` | `registerUser` (step `auth.admin`) | id of the administrator's account, stored only when the registration answers 201 |
| `userEmail` | `registerUser` (step `auth.user`) | e-mail of the ordinary user registered in this run, used as the basic-auth username of step `auth.userkey` |
| `adminExists` | `issueApiKey` (step `auth.key`) | `yes` when the administrator's login answers 201, `no` when it answers 401; drives the `when` condition of steps `auth.admin` and `auth.adminkey` |
| `adminKey` | `issueApiKey` (steps `auth.key` and `auth.adminkey`) | raw API key of the administrator; `auth.key` stores it only when the login succeeds, `auth.adminkey` after the registration |
| `adminId` | `listUsers` (step `users.admin`) | id of the default administrator |
| `adminVersion` | `listUsers` (step `users.admin`) | version of the default administrator, sent as If-Match by step `users.adminrole` |
| `userKey` | `issueApiKey` (step `auth.userkey`) | raw API key of the ordinary user |
| `secondKey` | `issueApiKey` (step `auth.second`) | raw second API key of the ordinary user |
| `secondKeyId` | `listApiKeys` (step `auth.keys`) | id of the key labelled `secondKeyLabel`, used by the revoke steps |
| `adminRights` | `getMe` (step `auth.rights`) | `yes` when the role of the default account is administrator, `no` otherwise; drives the flow-level `when` of the flows that need an administrator |
| `moderatorId`, `moderatorVersion`, `moderatorKey` | `registerUser`, `getUser`, `issueApiKey` (steps `modreg`, `modget`, `modkey` of the Taxons and Moderation flows) | id, version and raw API key of the moderator the flow registers and promotes |
| `otherId`, `otherKey` | `registerUser`, `issueApiKey` (steps `feedback.other`, `feedback.otherkey`) | id and API key of the second ordinary user the Feedback flow registers |
| `modOtherId`, `modOtherKey` | `registerUser`, `issueApiKey` (steps `moderation.other`, `moderation.otherkey`) | id and API key of the ordinary user the Moderation flow registers to write the review, since the profile owner cannot review their own profile |

The flows after Authentication each create their own category and city (with the administrator's key)
and their own data, use the ordinary user's key for user actions and delete what they created at the
end. They store the ids and versions they need under names that start with the flow:

| Variables | Set by | Hold |
|---|---|---|
| `categoryId`, `categoryVersion`, `cityId`, `cityVersion`, `snapshotId` | Taxons flow | the flow's category and city with their versions, and the id of the first history snapshot of the category |
| `profileCategoryId`, `profileCityId`, `profileId`, `profileVersion`, `profileSnapshotId` (and `...Version` of category and city) | Profile flow | the flow's category and city, the provider profile and its first history snapshot |
| `adCategoryId`, `adCityId`, `advertisementId`, `advertisementVersion`, `adSnapshotId` (and `...Version` of category and city) | Advertisements flow | the flow's category and city, the advertisement and its first history snapshot |
| `settingsVersion`, `settingsSnapshotId` | Account flow | the version of the caller's settings and the first snapshot of their history |
| `mediaCategoryId`, `mediaCityId`, `mediaAdId`, `mediaAdVersion`, `mediaPhotoId`, `mediaVideoId` (and `...Version` of category and city) | Media flow | the flow's category and city, the advertisement and the ids of its uploaded photo and video link |
| `contactCategoryId`, `contactCityId`, `contactProfileId`, `contactAdId` (and the `...Version` of each) | Contacts flow | the flow's category and city, the provider profile and the advertisement whose contacts are revealed |
| `feedbackCategoryId`, `feedbackCityId`, `feedbackProfileId`, `feedbackId`, `feedbackVersion`, `replyVersion` (and `...Version` of category, city and profile) | Feedback flow | the flow's category and city, the profile, the review and the reply versions |
| `moderationCategoryId`, `moderationCityId`, `moderationProfileId`, `moderationFeedbackId`, `moderationReplyId` (and `...Version` of category, city and profile) | Moderation flow | the flow's category and city, the profile, the review and the reply that are flagged and decided |
| `targetUserId`, `targetUserVersion`, `flowUserId`, `targetSnapshotId` | Users flow | the target user the flow creates, the version of that user, the id of the ordinary user the Authentication flow created and the first snapshot of the target's history |

## The MCP collection

[`marketplace-mcp.postman_collection.json`](marketplace-mcp.postman_collection.json) runs the MCP server (`POST {{host}}/mcp`) through plain HTTP requests, so the same runner and the same coverage check cover it. Every tool of the server has a step in its flows; the allow-list file [`mcp-coverage-allowlist.txt`](../scripts/mcp-coverage-allowlist.txt) is empty and exists for a future gap. The flows are Setup, Account, Advertisements, Media, Provider profiles, Contacts, Feedback, Users, API keys, Infrastructure, Taxons, Moderation, Administration and Close. Setup makes sure the default administrator exists (a login that registers the account when it is missing, so the flow user is never the first account of an empty database and therefore a plain user), keeps its key as `adminKey`, registers the flow user, issues its API key, opens a session, lists the tools and then checks the administrator's rights with the step `setup.rights`, which sets `adminRights`. Taxons and Moderation register a moderator themselves (helper steps that promote it with the administrator's key) and start with the condition `when: adminRights=yes` (see [Roles](#roles-and-running-without-an-administrator)); the moderator's key is called `moderatorKey` as in the REST collection, because the role of a flow comes from the name of the key its steps use. The Administration flow does the administrator's work on a target user that the flow registers and deletes, mirroring the REST Users flow, and starts with the same flow condition. The other flows call the tools and delete what they create at the end. Feedback registers a second user and Users a throwaway user, both through `registerUser`, and issue their keys through `issueApiKey` with Basic authentication.

Naming rule: a `tools/call` request is named after the tool, the other MCP requests after their JSON-RPC method (`initialize`, `notifications/initialized`, `tools/list`), the session-closing DELETE `endSession`, and the REST setup requests after their REST `operationId`. Every MCP request carries `Content-Type: application/json` and `Accept: application/json, text/event-stream`; after `initialize` it also carries `Mcp-Session-Id` and `MCP-Protocol-Version`.

The server answers a request either as `application/json` or as `text/event-stream` (`data: {...}` lines), and Postman has no shortcut for event streams. Every test script therefore reads a JSON-RPC answer with the same one line, which works for both and runs unchanged in real Postman:

```
const body = pm.response.text(); const rpc = JSON.parse(body.includes("data:") ? body.slice(body.lastIndexOf("data:") + 5) : body);
```

A version that a step takes from an `etag` field is stored without its quotes, because the next step puts it into a JSON string; a version taken from `item.version` needs no change.

Collection variables:

| Variable | Default |
|---|---|
| `host` | `http://localhost:8081` |
| `runId` | `1` (the flow runner generates a fresh value per run) |
| `mcpUserName` | `MCP Flow User` |
| `mcpEmailPrefix` | `mcp.flow.user`, builds the user's e-mail together with `runId` |
| `mcpPassword` | `password123` |
| `apiKeyLabel` | `mcp-flow-runner` |
| `mcpProtocol` | `2025-11-25`, sent as `MCP-Protocol-Version` |
| `email`, `password`, `adminName` | `admin.en@example.com`, `password` and `Admin EN`, the default administrator, as in the REST collection |
| `pixelPng` | a one-pixel PNG as base64 text, the photo the Media flow uploads |
| `videoUrl` | a YouTube link, the video the Media flow adds |
| `adKind` | `OFFER`, the kind of the advertisement the flow creates |
| `profileKind` | `MASTER`, the kind of the provider profile the flow creates |
| `profilePhone` | `+380501234567`, the phone number of that profile |
| `missingId` | `999999999`, an id no taxon has |

Variables captured at run time:

| Variable | Set by | Holds |
|---|---|---|
| `adminExists` | `issueApiKey` (step `setup.adminlogin`) | `yes` when the default administrator could log in, `no` when the next step has to register it |
| `mcpEmail` | `registerUser` (step `setup.user`) | e-mail of the user registered in this run |
| `mcpKey` | `issueApiKey` (step `setup.key`) | raw API key sent as the bearer token |
| `mcpSession` | `initialize` (step `setup.init`) | the `Mcp-Session-Id` of the session |
| `mcpSettingsVersion` | `getMySettings`, `updateMySettings` (steps `account.settings`, `account.update`) | the version of the flow user's settings |
| `mcpSnapshotId` | the history steps of the Account, Advertisements, Provider profiles and Users flows | the id of the oldest snapshot of the object the flow works with |
| `mcpAdId`, `mcpAdVersion` | `createAdvertisement` and the steps that change it (Advertisements flow) | id and version of the advertisement |
| `mcpMediaAdId`, `mcpMediaAdVersion`, `mcpPhotoId`, `mcpVideoId` | the Media flow | the advertisement of the flow and the ids of its photo and video |
| `mcpProfileId`, `mcpProfileVersion` | `createProviderProfile` and the steps that change it (Provider profiles flow) | id and version of the provider profile |
| `mcpContactProfileId`, `mcpContactProfileVersion`, `mcpContactAdId`, `mcpContactAdVersion` | the Contacts flow | id and version of its provider profile and advertisement |
| `mcpOtherId`, `mcpOtherKey` | `registerUser`, `issueApiKey` (Feedback flow) | id and API key of the second user |
| `mcpModOtherId`, `mcpModOtherKey` | `registerUser`, `issueApiKey` (Moderation flow) | id and API key of the user who writes the review there |
| `mcpFbProfileId`, `mcpFbProfileVersion`, `mcpReviewId`, `mcpReviewVersion`, `mcpReplyVersion` | the Feedback flow | its provider profile, the review and the reply |
| `mcpSelfId`, `mcpSelfKey`, `mcpSelfVersion` | the Users flow | id, API key and version of its throwaway user |
| `mcpSecondKeyId` | `listApiKeys` (step `keys.list`) | the id of the second key of the flow user |
| `taxonExists`, `mcpTaxonId` | `listTaxons` (step `taxons.list`) | `yes` or `no` (drives step `taxons.get`) and the id of the first taxon when there is one |
| `adminKey` | `issueApiKey` (steps `setup.adminlogin`, `setup.adminkey`) | raw API key of the default administrator |
| `adminRights` | `getMe` (step `setup.rights`) | `yes` when the role of the default account is administrator, `no` otherwise |
| `mcpModId`, `mcpModVersion`, `moderatorKey` | the helper steps of the Taxons and Moderation flows | id, version and raw API key of the moderator the flow makes |
| `mcpTaxCategoryId`, `mcpTaxCategoryVersion`, `mcpTaxCityId`, `mcpTaxCityVersion`, `mcpTaxSnapshotId` | the Taxons flow | its category and city, their versions and the first snapshot of the category history |
| `mcpModProfileId`, `mcpModProfileVersion`, `mcpModFeedbackId`, `mcpModReplyId` | the Moderation flow | its provider profile, the review and the reply that are flagged and decided |
| `mcpAdmTargetId`, `mcpAdmTargetVersion`, `mcpAdmSnapshotId`, `mcpAdminId`, `mcpAdminVersion` | the Administration flow | the target user and its version and first snapshot, and the default administrator's id and version |
