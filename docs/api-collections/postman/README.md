# postman/

The Postman collection of the REST API, `marketplace-rest-api.postman_collection.json` (Postman
collection format v2.1). Its folders are flows: each folder holds the requests of one flow, run in
the order they are listed. The flow runner page and the collection test scripts both read this file.

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
kind: required|optional|pick
default: off
when: <variable>=<value>

<free text>
```

- `step` identifies the step within the flow.
- `label` is shown in the flow runner; at most 24 characters.
- `kind` is one of `required`, `optional` or `pick`.
- `default` is optional; `off` marks a step that is skipped unless it is explicitly included.
- `when` is optional; `when: <variable>=<value>` runs the step only when that run variable has that value, otherwise the step is skipped as "condition not met".

A folder's own description is the flow note.

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
| `userKey` | `issueApiKey` (step `auth.userkey`) | raw API key of the ordinary user |
| `secondKey` | `issueApiKey` (step `auth.second`) | raw second API key of the administrator |
| `secondKeyId` | `listApiKeys` (step `auth.keys`) | id of the key labelled `secondKeyLabel`, used by the revoke steps |
