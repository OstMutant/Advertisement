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

/** Covers the URLs, methods, result shapes and error mapping of {@link ApiKeyTools}. */
class ApiKeyToolsTest extends AbstractToolTest {

    private ApiKeyTools tools;

    @BeforeEach
    void setUp() {
        tools = new ApiKeyTools(restApi);
    }

    @Test
    void issueApiKeyPostsLabelAndReturnsRawKey() {
        CallerRequest.authorizeAs("Basic dXNlcjpwYXNz");
        server.expect(requestTo("http://api.test/api/api-keys"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"label\":\"ci\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"rawKey\":\"k-123\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.issueApiKey("ci");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("item", Map.of("rawKey", "k-123"));
        server.verify();
    }

    @Test
    void issueApiKeyWithoutLabelPostsEmptyObject() {
        server.expect(requestTo("http://api.test/api/api-keys"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"rawKey\":\"k-456\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.issueApiKey(null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("item", Map.of("rawKey", "k-456"));
        server.verify();
    }

    @Test
    void issueApiKeyUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/api-keys")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.issueApiKey("ci");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void issueApiKeyRateLimitedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/api-keys"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{\"message\":\"Too many requests\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.issueApiKey("ci");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result))
                .startsWith("HTTP 429: rate limit reached. Retry after 30 seconds");
    }

    @Test
    void listApiKeysReturnsItemsAndTotalCount() {
        server.expect(requestTo("http://api.test/api/api-keys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":1,\"label\":\"ci\"},{\"id\":2,\"label\":\"web\"}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.listApiKeys(null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 2);
        assertThat((List<?>) structured(result).get("items")).hasSize(2);
        server.verify();
    }

    @Test
    void listApiKeysWithIncludeRevokedPassesFlag() {
        server.expect(requestTo("http://api.test/api/api-keys?includeRevoked=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":3,\"revokedAt\":\"2026-10-01T00:00:00Z\"}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.listApiKeys(true);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        server.verify();
    }

    @Test
    void listApiKeysUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/api-keys")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.listApiKeys(null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void revokeApiKeySendsDeleteWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/api-keys/7"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.revokeApiKey(7L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void revokeApiKeyNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/api-keys/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.revokeApiKey(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void revokeApiKeyAlreadyRevokedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/api-keys/7"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Key already revoked\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.revokeApiKey(7L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 409: Key already revoked");
    }
}
