package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link AccountTools}. */
class AccountToolsTest extends AbstractToolTest {

    private AccountTools tools;

    @BeforeEach
    void setUp() {
        tools = new AccountTools(restApi);
    }

    @Test
    void getMeReturnsItemAndEtag() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/users/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("{\"id\":11,\"name\":\"Ann\"}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"5\""));

        CallToolResult result = tools.getMe();

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("item", Map.of("id", 11, "name", "Ann")).containsEntry("etag", "\"5\"");
        server.verify();
    }

    @Test
    void getMeWithoutKeyBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.getMe();

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
    }

    @Test
    void getMeOmitsAuthorizationWithoutRequest() {
        server.expect(requestTo("http://api.test/api/users/me"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(tools.getMe().isError()).isTrue();
        server.verify();
    }

    @Test
    void getMySettingsReturnsSettingsAndEtag() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/users/me/settings"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("{\"pageSize\":20}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"2\""));

        CallToolResult result = tools.getMySettings();

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("settings", Map.of("pageSize", 20)).containsEntry("etag", "\"2\"");
        server.verify();
    }

    @Test
    void getMySettingsWithoutKeyBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.getMySettings();

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
    }
}
