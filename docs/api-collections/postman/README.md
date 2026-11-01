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

<free text>
```

- `step` identifies the step within the flow.
- `label` is shown in the flow runner; at most 24 characters.
- `kind` is one of `required` or `optional`.
- `when` is optional; `when: <variable>=<value>` runs the step only when that run variable has that value, otherwise the step is skipped as "condition not met".

A folder's own description is the flow note.

## Running without an administrator

The first step of the Taxons flow, `taxons.probe`, lists the users and stops the run with a clear message when the default account is not an administrator. The flows that need no administrator are Authentication, Infrastructure and Account; `--role user` of [`api-collection-tests.sh`](../api-collection-tests.sh) runs only those.

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
| `secondKey` | `issueApiKey` (step `auth.second`) | raw second API key of the administrator |
| `secondKeyId` | `listApiKeys` (step `auth.keys`) | id of the key labelled `secondKeyLabel`, used by the revoke steps |

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

[`marketplace-mcp.postman_collection.json`](marketplace-mcp.postman_collection.json) runs the MCP server (`POST {{host}}/mcp`) through plain HTTP requests, so the same runner and the same coverage check cover it. It has six flows: Setup, Account, Advertisements, Provider profiles, Taxons and Close. No administrator is used. Setup registers a user, issues an API key, opens a session and lists the tools; Advertisements and Provider profiles create their data over REST and delete it at the end.

Naming rule: a `tools/call` request is named after the tool, the other MCP requests after their JSON-RPC method (`initialize`, `notifications/initialized`, `tools/list`), the session-closing DELETE `endSession`, and the REST setup requests after their REST `operationId`. Every MCP request carries `Content-Type: application/json` and `Accept: application/json, text/event-stream`; after `initialize` it also carries `Mcp-Session-Id` and `MCP-Protocol-Version`.

The server answers a request either as `application/json` or as `text/event-stream` (`data: {...}` lines), and Postman has no shortcut for event streams. Every test script therefore reads a JSON-RPC answer with the same one line, which works for both and runs unchanged in real Postman:

```
const body = pm.response.text(); const rpc = JSON.parse(body.includes("data:") ? body.slice(body.lastIndexOf("data:") + 5) : body);
```

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
| `adKind` | `OFFER`, the kind of the advertisement the flow creates |
| `profileKind` | `MASTER`, the kind of the provider profile the flow creates |
| `profilePhone` | `+380501234567`, the phone number of that profile |
| `missingId` | `999999999`, an id no taxon has |

Variables captured at run time:

| Variable | Set by | Holds |
|---|---|---|
| `mcpEmail` | `registerUser` (step `setup.user`) | e-mail of the user registered in this run |
| `mcpKey` | `issueApiKey` (step `setup.key`) | raw API key sent as the bearer token |
| `mcpSession` | `initialize` (step `setup.init`) | the `Mcp-Session-Id` of the session |
| `mcpAdId`, `mcpAdVersion` | `createAdvertisement` (step `ads.create`) | id and version of the advertisement |
| `mcpProfileId`, `mcpProfileVersion` | `createProviderProfile` (step `profiles.create`) | id and version of the provider profile |
| `taxonExists`, `mcpTaxonId` | `listTaxons` (step `taxons.list`) | `yes` or `no` (drives step `taxons.get`) and the id of the first taxon when there is one |
