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
                        + "&adKinds=OFFER%2CPRODUCT&sort=title,asc&locale=uk".replace(",asc", "%2Casc")))
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
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
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
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("HTTP 404: no such resource.");
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
}
