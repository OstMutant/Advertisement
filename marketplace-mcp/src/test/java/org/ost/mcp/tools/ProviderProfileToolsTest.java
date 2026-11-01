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

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link ProviderProfileTools}. */
class ProviderProfileToolsTest extends AbstractToolTest {

    private ProviderProfileTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProviderProfileTools(restApi);
    }

    @Test
    void listProviderProfilesWithoutFiltersAsksFirstPage() {
        server.expect(requestTo("http://api.test/api/provider-profiles?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "1"));

        CallToolResult result = tools.listProviderProfiles(null, null, null, null, null, null);

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
                        + "&cityTaxonId=5&sort=updatedAt%2Cdesc&locale=uk"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<x>; rel=\"next\""));

        CallToolResult result = tools.listProviderProfiles(1, List.of("MASTER", "SHOP"), List.of(1L, 2L), 5L, "updatedAt,desc", "uk");

        assertThat(structured(result)).containsEntry("hasMore", true).containsEntry("nextPage", 2);
        server.verify();
    }

    @Test
    void listProviderProfilesUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles?page=0")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.listProviderProfiles(null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
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
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("HTTP 404: no such resource.");
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
}
