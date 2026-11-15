package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link ProviderProfileTools}. */
class ProviderProfileToolsTest extends AbstractToolTest {

    private static final String FULL_BODY = "{\"kind\":\"MASTER\",\"about\":\"Plumbing services\",\"categoryIds\":[1,2],"
            + "\"cityTaxonId\":5,\"phone\":\"+380501112233\",\"telegram\":\"@plumber\",\"viber\":\"+380501112233\"}";

    private ProviderProfileTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProviderProfileTools(restApi);
    }

    @Test
    void listProviderProfilesWithoutFiltersAsksFirstPage() {
        server.expect(requestTo("http://api.test/api/provider-profiles?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "1"));

        CallToolResult result = tools.listProviderProfiles(null, null, null, null, null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("totalCount", 1L)
                .containsEntry("page", 0)
                .containsEntry("returned", 1)
                .containsEntry("hasMore", false)
                .containsKey("items")
                .containsKey("nextPage");
        server.verify();
    }

    @Test
    void listProviderProfilesPassesEveryFilterEncoded() {
        server.expect(requestTo("http://api.test/api/provider-profiles?page=1&kinds=MASTER%2CSHOP&categoryIds=1%2C2"
                        + "&cityTaxonId=5&promoted=true&sort=updatedAt%2Cdesc&locale=uk"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<x>; rel=\"next\""));

        CallToolResult result = tools.listProviderProfiles(1, List.of("MASTER", "SHOP"), List.of(1L, 2L), 5L, true, "updatedAt,desc", "uk");

        assertThat(structured(result)).containsEntry("hasMore", true).containsEntry("nextPage", 2);
        server.verify();
    }

    @Test
    void listProviderProfilesUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles?page=0")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.listProviderProfiles(null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void getProviderProfileReturnsItemAndEtag() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7?locale=en"))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"4\""));

        CallToolResult result = tools.getProviderProfile(7L, "en");

        assertThat(structured(result)).containsEntry("item", Map.of("id", 7)).containsEntry("etag", "\"4\"");
        server.verify();
    }

    @Test
    void getProviderProfileNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/8")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getProviderProfile(8L, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void forwardsCallerAuthorization() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON));

        assertThat(tools.getProviderProfile(7L, null).isError()).isFalse();
        server.verify();
    }

    @Test
    void createProviderProfilePostsEveryFieldAndReturnsLocation() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string(FULL_BODY))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":9,\"kind\":\"MASTER\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/provider-profiles/9"));

        CallToolResult result = tools.createProviderProfile("MASTER", "Plumbing services", List.of(1L, 2L), 5L,
                "+380501112233", "@plumber", "+380501112233");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("location", "http://api.test/api/provider-profiles/9")
                .containsEntry("item", Map.of("id", 9, "kind", "MASTER"));
        server.verify();
    }

    @Test
    void createProviderProfileForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.createProviderProfile("SUPPORT", null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void createProviderProfileWithOnlyKindOmitsOptionalFields() {
        server.expect(requestTo("http://api.test/api/provider-profiles"))
                .andExpect(content().string("{\"kind\":\"MASTER\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).body("{\"id\":10,\"kind\":\"MASTER\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.createProviderProfile("MASTER", null, null, null, null, null, null);

        assertThat(result.isError()).isFalse();
        server.verify();
    }

    @Test
    void updateProviderProfilePutsEveryFieldWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"4\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string(FULL_BODY))
                .andRespond(withSuccess("{\"id\":7,\"kind\":\"MASTER\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"5\""));

        CallToolResult result = tools.updateProviderProfile(7L, "\"4\"", "MASTER", "Plumbing services", List.of(1L, 2L), 5L,
                "+380501112233", "@plumber", "+380501112233");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"5\"")
                .containsEntry("item", Map.of("id", 7, "kind", "MASTER"));
        server.verify();
    }

    @Test
    void updateProviderProfileForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateProviderProfile(7L, "\"4\"", "MASTER", null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateProviderProfileStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateProviderProfile(7L, "\"1\"", "MASTER", null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void updateProviderProfileWithOnlyKindOmitsOptionalFields() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andExpect(content().string("{\"kind\":\"SHOP\"}"))
                .andRespond(withSuccess("{\"id\":7,\"kind\":\"SHOP\"}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateProviderProfile(7L, "\"4\"", "SHOP", null, null, null, null, null, null);

        assertThat(result.isError()).isFalse();
        server.verify();
    }

    @Test
    void patchProviderProfileSendsMergePatchWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"4\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/merge-patch+json"))
                .andExpect(content().string("{\"about\":\"New about\",\"phone\":null}"))
                .andRespond(withSuccess("{\"id\":7,\"about\":\"New about\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"5\""));

        CallToolResult result = tools.patchProviderProfile(7L, "\"4\"", patch());

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"5\"")
                .containsEntry("item", Map.of("id", 7, "about", "New about"));
        server.verify();
    }

    @Test
    void patchProviderProfileForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.patchProviderProfile(7L, "\"4\"", patch());

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void patchProviderProfileStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.patchProviderProfile(7L, "\"1\"", patch());

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void deleteProviderProfileSendsIfMatchWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"4\""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteProviderProfile(7L, "\"4\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteProviderProfileForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.deleteProviderProfile(7L, "\"4\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void deleteProviderProfileStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.deleteProviderProfile(7L, "\"1\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void getProviderProfileHistoryReturnsItems() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7/history"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"snapshotId\":21,\"version\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getProviderProfileHistory(7L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        assertThat((List<?>) structured(result).get("items")).hasSize(1);
        server.verify();
    }

    @Test
    void getProviderProfileHistoryNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/9/history")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getProviderProfileHistory(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getProviderProfileHistorySnapshotReturnsRecordedState() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7/history/21"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"snapshot\":{\"about\":\"Plumbing services\"},\"version\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getProviderProfileHistorySnapshot(7L, 21L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("version", 2)
                .containsEntry("snapshot", Map.of("about", "Plumbing services"));
        server.verify();
    }

    @Test
    void getProviderProfileHistorySnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/history/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getProviderProfileHistorySnapshot(7L, 99L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void restoreProviderProfileFromSnapshotPostsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7/history/21/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"4\""))
                .andRespond(withSuccess("{\"id\":7,\"kind\":\"MASTER\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"5\""));

        CallToolResult result = tools.restoreProviderProfileFromSnapshot(7L, 21L, "\"4\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"5\"")
                .containsEntry("item", Map.of("id", 7, "kind", "MASTER"));
        server.verify();
    }

    @Test
    void restoreProviderProfileFromSnapshotForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/history/21/restore"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.restoreProviderProfileFromSnapshot(7L, 21L, "\"4\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    private static Map<String, Object> patch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("about", "New about");
        patch.put("phone", null);
        return patch;
    }
}
