package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

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

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link AdvertisementTools}. */
class AdvertisementToolsTest extends AbstractToolTest {

    private AdvertisementTools tools;

    @BeforeEach
    void setUp() {
        tools = new AdvertisementTools(restApi);
    }

    @Test
    void listAdvertisementsWithoutFiltersAsksFirstPage() {
        server.expect(requestTo("http://api.test/api/advertisements?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "1"));

        CallToolResult result = tools.listAdvertisements(null, null, null, null, null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1L).containsEntry("hasMore", false);
        server.verify();
    }

    @Test
    void listAdvertisementsPassesEveryFilterEncoded() {
        server.expect(requestTo("http://api.test/api/advertisements?page=2&title=a%26b%20c&categoryIds=1%2C2&cityTaxonId=5"
                        + "&adKinds=OFFER%2CPRODUCT&sort=title%2Casc&locale=uk"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<x>; rel=\"next\""));

        CallToolResult result = tools.listAdvertisements(2, "a&b c", List.of(1L, 2L), 5L, List.of("OFFER", "PRODUCT"), "title,asc", "uk");

        assertThat(structured(result)).containsEntry("hasMore", true).containsEntry("nextPage", 3);
        server.verify();
    }

    @Test
    void listAdvertisementsUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements?page=0")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.listAdvertisements(null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void getAdvertisementReturnsItemAndEtag() {
        server.expect(requestTo("http://api.test/api/advertisements/7?locale=en"))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.getAdvertisement(7L, "en");

        assertThat(structured(result)).containsEntry("item", Map.of("id", 7)).containsEntry("etag", "\"3\"");
        server.verify();
    }

    @Test
    void getAdvertisementNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/8")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getAdvertisement(8L, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void listAdvertisementMediaReturnsItems() {
        server.expect(requestTo("http://api.test/api/advertisements/7/media"))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.listAdvertisementMedia(7L);

        assertThat(structured(result)).containsEntry("totalCount", 2);
        assertThat((List<?>) structured(result).get("items")).hasSize(2);
    }

    @Test
    void forwardsCallerAuthorization() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/advertisements/7/media"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(tools.listAdvertisementMedia(7L).isError()).isFalse();
        server.verify();
    }

    @Test
    void createAdvertisementPostsAllFields() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"title\":\"Bike\",\"description\":\"Good bike\",\"adKind\":\"OFFER\","
                        + "\"categoryIds\":[1,2],\"cityTaxonId\":5}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":9,\"title\":\"Bike\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/advertisements/9"));

        CallToolResult result = tools.createAdvertisement("Bike", "Good bike", "OFFER", List.of(1L, 2L), 5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("location", "http://api.test/api/advertisements/9")
                .containsEntry("item", Map.of("id", 9, "title", "Bike"));
        server.verify();
    }

    @Test
    void createAdvertisementOmitsNullOptionalFields() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"title\":\"Bike\",\"description\":\"Good bike\",\"adKind\":\"OFFER\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":9,\"title\":\"Bike\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/advertisements/9"));

        CallToolResult result = tools.createAdvertisement("Bike", "Good bike", "OFFER", null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 201);
        server.verify();
    }

    @Test
    void createAdvertisementForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.createAdvertisement("Bike", "Good bike", "OFFER", null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateAdvertisementPutsFieldsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"title\":\"Bike\",\"description\":\"Good bike\",\"adKind\":\"OFFER\","
                        + "\"categoryIds\":[1,2],\"cityTaxonId\":5}"))
                .andRespond(withSuccess("{\"id\":7,\"title\":\"Bike\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.updateAdvertisement(7L, "\"2\"", "Bike", "Good bike", "OFFER", List.of(1L, 2L), 5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("id", 7, "title", "Bike"));
        server.verify();
    }

    @Test
    void updateAdvertisementOmitsNullOptionalFields() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(content().string("{\"title\":\"Bike\",\"description\":\"Good bike\",\"adKind\":\"REQUEST\"}"))
                .andRespond(withSuccess("{\"id\":7,\"title\":\"Bike\"}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateAdvertisement(7L, "\"2\"", "Bike", "Good bike", "REQUEST", null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 200);
        server.verify();
    }

    @Test
    void updateAdvertisementForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateAdvertisement(7L, "\"2\"", "Bike", "Good bike", "OFFER", null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateAdvertisementStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateAdvertisement(7L, "\"1\"", "Bike", "Good bike", "OFFER", null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void patchAdvertisementSendsMergePatchWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/merge-patch+json"))
                .andExpect(content().string("{\"title\":\"New title\"}"))
                .andRespond(withSuccess("{\"id\":7,\"title\":\"New title\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.patchAdvertisement(7L, "\"2\"", Map.of("title", "New title"));

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("id", 7, "title", "New title"));
        server.verify();
    }

    @Test
    void patchAdvertisementForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.patchAdvertisement(7L, "\"2\"", Map.of("title", "New title"));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void patchAdvertisementStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.patchAdvertisement(7L, "\"1\"", Map.of("title", "New title"));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void deleteAdvertisementSendsIfMatchWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteAdvertisement(7L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteAdvertisementForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.deleteAdvertisement(7L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void deleteAdvertisementStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.deleteAdvertisement(7L, "\"1\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void getAdvertisementHistoryReturnsItems() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7/history"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"snapshotId\":11,\"version\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getAdvertisementHistory(7L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        assertThat((List<?>) structured(result).get("items")).hasSize(1);
        server.verify();
    }

    @Test
    void getAdvertisementHistoryNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/9/history")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getAdvertisementHistory(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getAdvertisementHistorySnapshotReturnsRecordedState() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7/history/11"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"snapshot\":{\"title\":\"Bike\"},\"version\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getAdvertisementHistorySnapshot(7L, 11L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("version", 2);
        server.verify();
    }

    @Test
    void getAdvertisementHistorySnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7/history/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getAdvertisementHistorySnapshot(7L, 99L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void restoreAdvertisementFromSnapshotPostsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/7/history/11/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withSuccess("{\"id\":7,\"title\":\"Bike\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.restoreAdvertisementFromSnapshot(7L, 11L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("id", 7, "title", "Bike"));
        server.verify();
    }

    @Test
    void restoreAdvertisementFromSnapshotForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7/history/11/restore")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.restoreAdvertisementFromSnapshot(7L, 11L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
