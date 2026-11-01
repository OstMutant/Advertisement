package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link FeedbackTools}. */
class FeedbackToolsTest extends AbstractToolTest {

    private FeedbackTools tools;

    @BeforeEach
    void setUp() {
        tools = new FeedbackTools(restApi);
    }

    @Test
    void listProviderProfileFeedbackWithoutPageAsksFirstPage() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "1"));

        CallToolResult result = tools.listProviderProfileFeedback(7L, null);

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
    void listProviderProfileFeedbackFollowsNextLink() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback?page=3"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<x>; rel=\"next\""));

        CallToolResult result = tools.listProviderProfileFeedback(7L, 3);

        assertThat(structured(result)).containsEntry("page", 3).containsEntry("hasMore", true).containsEntry("nextPage", 4);
        server.verify();
    }

    @Test
    void listProviderProfileFeedbackNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/8/feedback?page=0"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.listProviderProfileFeedback(8L, null);

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void aggregateReturnsBodyKeys() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback/aggregate"))
                .andRespond(withSuccess("{\"averageRating\":4.5,\"reviewCount\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getProviderProfileFeedbackAggregate(7L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("averageRating", 4.5).containsEntry("reviewCount", 2);
        server.verify();
    }

    @Test
    void aggregateUnauthorizedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback/aggregate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.getProviderProfileFeedbackAggregate(7L);

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("HTTP 401");
    }

    @Test
    void forwardsCallerAuthorization() {
        CallerRequest.authorizeAs("Bearer key-1");
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback/aggregate"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(tools.getProviderProfileFeedbackAggregate(7L).isError()).isFalse();
        server.verify();
    }
}
