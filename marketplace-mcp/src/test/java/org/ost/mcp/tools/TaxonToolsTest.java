package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes and error mapping of {@link TaxonTools}. */
class TaxonToolsTest extends AbstractToolTest {

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
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
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
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("HTTP 404: no such resource.");
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
}
