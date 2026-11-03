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

/** Covers the URLs, result shapes and error mapping of {@link TaxonTools}. */
class TaxonToolsTest extends AbstractToolTest {

    private static final List<TaxonTools.Translation> TRANSLATIONS = List.of(
            new TaxonTools.Translation("en", "Plumbing", "Plumbing services"),
            new TaxonTools.Translation("uk", "Сантехніка", "Сантехнічні послуги"));
    private static final String TRANSLATIONS_JSON = "[{\"locale\":\"en\",\"name\":\"Plumbing\",\"description\":\"Plumbing services\"},"
            + "{\"locale\":\"uk\",\"name\":\"Сантехніка\",\"description\":\"Сантехнічні послуги\"}]";

    private TaxonTools tools;

    @BeforeEach
    void setUp() {
        tools = new TaxonTools(restApi);
    }

    @Test
    void listTaxonsWithoutFiltersAsksAll() {
        server.expect(requestTo("http://api.test/api/taxons"))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.listTaxons(null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 2);
        assertThat((List<?>) structured(result).get("items")).hasSize(2);
        server.verify();
    }

    @Test
    void listTaxonsPassesTypeAndLocale() {
        server.expect(requestTo("http://api.test/api/taxons?type=CITY&locale=uk"))
                .andRespond(withSuccess("[{\"id\":5}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "1"));

        CallToolResult result = tools.listTaxons("CITY", "uk");

        assertThat(structured(result)).containsEntry("totalCount", 1L);
        server.verify();
    }

    @Test
    void listTaxonsUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.listTaxons(null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void getTaxonReturnsItemAndEtag() {
        server.expect(requestTo("http://api.test/api/taxons/3?locale=en"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"2\""));

        CallToolResult result = tools.getTaxon(3L, "en");

        assertThat(structured(result)).containsEntry("item", Map.of("id", 3)).containsEntry("etag", "\"2\"");
        server.verify();
    }

    @Test
    void getTaxonNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getTaxon(9L, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void forwardsCallerAuthorization() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/taxons?type=CATEGORY"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(tools.listTaxons("CATEGORY", null).isError()).isFalse();
        server.verify();
    }

    @Test
    void getTaxonHistoryReturnsItems() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3/history"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"snapshotId\":11,\"version\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getTaxonHistory(3L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        assertThat((List<?>) structured(result).get("items")).hasSize(1);
        server.verify();
    }

    @Test
    void getTaxonHistoryNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/9/history")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getTaxonHistory(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getTaxonHistorySnapshotReturnsRecordedState() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3/history/11"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"snapshot\":{\"name\":\"Plumbing\"},\"version\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getTaxonHistorySnapshot(3L, 11L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("version", 2);
        server.verify();
    }

    @Test
    void getTaxonHistorySnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3/history/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getTaxonHistorySnapshot(3L, 99L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void createTaxonPostsTypeAndTranslations() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"type\":\"CATEGORY\",\"translations\":" + TRANSLATIONS_JSON + "}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":7,\"type\":\"CATEGORY\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/taxons/7"));

        CallToolResult result = tools.createTaxon("CATEGORY", TRANSLATIONS);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("location", "http://api.test/api/taxons/7")
                .containsEntry("item", Map.of("id", 7, "type", "CATEGORY"));
        server.verify();
    }

    @Test
    void createTaxonForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.createTaxon("CATEGORY", TRANSLATIONS);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateTaxonPutsTranslationsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"translations\":" + TRANSLATIONS_JSON + "}"))
                .andRespond(withSuccess("{\"id\":3,\"type\":\"CATEGORY\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.updateTaxon(3L, "\"2\"", TRANSLATIONS);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("id", 3, "type", "CATEGORY"));
        server.verify();
    }

    @Test
    void updateTaxonForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateTaxon(3L, "\"2\"", TRANSLATIONS);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateTaxonStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateTaxon(3L, "\"1\"", TRANSLATIONS);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void deleteTaxonSendsIfMatchWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteTaxon(3L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteTaxonForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.deleteTaxon(3L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void deleteTaxonStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.deleteTaxon(3L, "\"1\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void restoreTaxonPostsWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"id\":3,\"type\":\"CATEGORY\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"4\""));

        CallToolResult result = tools.restoreTaxon(3L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"4\"")
                .containsEntry("item", Map.of("id", 3, "type", "CATEGORY"));
        server.verify();
    }

    @Test
    void restoreTaxonForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3/restore")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.restoreTaxon(3L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void restoreTaxonFromSnapshotPostsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/taxons/3/history/11/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withSuccess("{\"id\":3,\"type\":\"CATEGORY\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.restoreTaxonFromSnapshot(3L, 11L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"");
        server.verify();
    }

    @Test
    void restoreTaxonFromSnapshotForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/taxons/3/history/11/restore")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.restoreTaxonFromSnapshot(3L, 11L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
