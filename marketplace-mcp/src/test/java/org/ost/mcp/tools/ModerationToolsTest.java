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

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes, error mapping and header forwarding of {@link ModerationTools}. */
class ModerationToolsTest extends AbstractToolTest {

    private ModerationTools tools;

    @BeforeEach
    void setUp() {
        tools = new ModerationTools(restApi);
    }

    @Test
    void getModerationQueueWithoutFiltersAsksFirstPage() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/queue?page=0"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "2"));

        CallToolResult result = tools.getModerationQueue(null, null, null, null, null, null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("totalCount", 2L)
                .containsEntry("page", 0)
                .containsEntry("returned", 2)
                .containsEntry("hasMore", false);
        assertThat((List<?>) structured(result).get("items")).hasSize(2);
        server.verify();
    }

    @Test
    void getModerationQueuePassesEveryFilterEncoded() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/queue?page=1"
                        + "&types=FEEDBACK%2CCOMMENT&statuses=NEW%2CHIDDEN"
                        + "&createdAtStart=2026-01-01T00%3A00%3A00Z&createdAtEnd=2026-02-01T00%3A00%3A00Z"
                        + "&authorName=Anna&reasonText=spam&sort=createdAt%2Cdesc"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getModerationQueue(1, List.of("FEEDBACK", "COMMENT"), List.of("NEW", "HIDDEN"),
                "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "Anna", "spam", "createdAt,desc");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("page", 1);
        server.verify();
    }

    @Test
    void getModerationQueueNextLinkGivesHasMoreAndNextPage() {
        server.expect(requestTo("http://api.test/api/moderation/queue?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<http://api.test/api/moderation/queue?page=1>; rel=\"next\""));

        CallToolResult result = tools.getModerationQueue(0, null, null, null, null, null, null, null);

        assertThat(structured(result))
                .containsEntry("hasMore", true)
                .containsEntry("nextPage", 1);
        server.verify();
    }

    @Test
    void getModerationQueueForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/queue?page=0")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.getModerationQueue(null, null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void approveFeedbackPostsWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/approve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.approveFeedback(5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void approveFeedbackConflictBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/approve"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Not awaiting a decision\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.approveFeedback(5L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 409");
    }

    @Test
    void approveFeedbackForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/approve")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.approveFeedback(5L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void confirmFeedbackViolationPostsWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/confirm-violation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.confirmFeedbackViolation(5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void confirmFeedbackViolationConflictBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/confirm-violation"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Not awaiting a decision\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.confirmFeedbackViolation(5L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 409");
    }

    @Test
    void confirmFeedbackViolationForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/feedback/5/confirm-violation")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.confirmFeedbackViolation(5L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void approveFeedbackReplyPostsWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/replies/6/approve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.approveFeedbackReply(6L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void approveFeedbackReplyConflictBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/replies/6/approve"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Not awaiting a decision\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.approveFeedbackReply(6L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 409");
    }

    @Test
    void approveFeedbackReplyForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/replies/6/approve")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.approveFeedbackReply(6L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void confirmFeedbackReplyViolationPostsWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/moderation/replies/6/confirm-violation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.confirmFeedbackReplyViolation(6L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void confirmFeedbackReplyViolationConflictBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/replies/6/confirm-violation"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Not awaiting a decision\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.confirmFeedbackReplyViolation(6L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 409");
    }

    @Test
    void confirmFeedbackReplyViolationForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/moderation/replies/6/confirm-violation")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.confirmFeedbackReplyViolation(6L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
