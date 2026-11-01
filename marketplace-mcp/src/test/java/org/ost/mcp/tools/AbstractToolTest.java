package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.ost.mcp.config.McpProperties;
import org.ost.mcp.rest.CallerAuthorization;
import org.ost.mcp.rest.RestApiClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

/** Shared wiring of the tool tests: a mock REST server behind a real {@link RestApiClient}, plus the result helper they all use. */
abstract class AbstractToolTest {

    protected MockRestServiceServer server;
    protected RestApiClient restApi;

    @BeforeEach
    void bindMockRestServer() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restApi = new RestApiClient(builder, new McpProperties("http://api.test", List.of()), new CallerAuthorization());
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> structured(CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }
}
