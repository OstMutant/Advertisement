# docs/api-collections

Runnable API request collections for manual and visual testing of the REST API (`marketplace-rest-api`). Each subfolder is one tool's format.

## postman/

A [Postman](https://www.postman.com/downloads/) collection (v2.1 JSON): eight requests that form one chain — register a test user, issue an API key, create an advertisement, then read advertisements, taxons, the caller's settings and API keys. Each response value is stored in a collection variable by the test script (`apiKey`, `advertisementId`, `userId`) and used by the following requests.

Import: File → Import → `postman/marketplace-rest-api.postman_collection.json`. Host (`http://localhost:8081`), test user (`bruno.tester@example.com`) and password (`password123`) are collection variables, so no environment has to be selected.

Run: Send on a single request, or right-click the collection → Run collection for the whole chain. Requests to `localhost` work from the Postman desktop app; the web app needs the Postman Agent.

Flow canvas: Flows → add HTTP Request blocks → assign the saved requests in order → connect them. A Flow cannot be exported or imported as a file, so it is built inside Postman and not stored here.

The collection describes the API as it is today; after an API change, re-import the OpenAPI document at `/v3/api-docs` rather than editing requests blindly.

Request names in the Postman collection are the `operationId` of the operation they call. `PostmanCollectionTest` in `integration-tests` fails the build when a request names an unknown operation, or when its method or path no longer matches that operation.
