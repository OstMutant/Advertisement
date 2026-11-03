package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
        assertThat(errorText(result)).isEqualTo("HTTP 404: no such resource.");
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
        assertThat(errorText(result)).startsWith("HTTP 401");
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

    @Test
    void createProviderProfileFeedbackPostsRatingAndText() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"rating\":5,\"text\":\"Great service\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":21,\"rating\":5,\"text\":\"Great service\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/feedback/21"));

        CallToolResult result = tools.createProviderProfileFeedback(7L, 5, "Great service");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("location", "http://api.test/api/feedback/21")
                .containsEntry("item", Map.of("id", 21, "rating", 5, "text", "Great service"));
        server.verify();
    }

    @Test
    void createProviderProfileFeedbackForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/7/feedback")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.createProviderProfileFeedback(7L, 5, "Great service");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateFeedbackPutsRatingAndTextWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"rating\":4,\"text\":\"Good service\"}"))
                .andRespond(withSuccess("{\"id\":21,\"rating\":4,\"text\":\"Good service\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"2\""));

        CallToolResult result = tools.updateFeedback(21L, "\"1\"", 4, "Good service");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"2\"")
                .containsEntry("item", Map.of("id", 21, "rating", 4, "text", "Good service"));
        server.verify();
    }

    @Test
    void updateFeedbackForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateFeedback(21L, "\"1\"", 4, "Good service");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateFeedbackStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateFeedback(21L, "\"0\"", 4, "Good service");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void createFeedbackReplyPostsText() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21/reply"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"text\":\"Thanks for the review\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"text\":\"Thanks for the review\"}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LOCATION, "http://api.test/api/feedback/21/reply"));

        CallToolResult result = tools.createFeedbackReply(21L, "Thanks for the review");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("location", "http://api.test/api/feedback/21/reply")
                .containsEntry("item", Map.of("text", "Thanks for the review"));
        server.verify();
    }

    @Test
    void createFeedbackReplyForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/reply")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.createFeedbackReply(21L, "Thanks for the review");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateFeedbackReplyPutsTextWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21/reply"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"text\":\"Thanks\"}"))
                .andRespond(withSuccess("{\"text\":\"Thanks\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"2\""));

        CallToolResult result = tools.updateFeedbackReply(21L, "\"1\"", "Thanks");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"2\"")
                .containsEntry("item", Map.of("text", "Thanks"));
        server.verify();
    }

    @Test
    void updateFeedbackReplyForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/reply")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateFeedbackReply(21L, "\"1\"", "Thanks");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateFeedbackReplyStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/reply"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateFeedbackReply(21L, "\"0\"", "Thanks");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void deleteFeedbackReplySendsDeleteWithoutIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21/reply"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(headerDoesNotExist(HttpHeaders.IF_MATCH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteFeedbackReply(21L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteFeedbackReplyForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/reply")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.deleteFeedbackReply(21L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void flagFeedbackPostsReason() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21/flags"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"reason\":\"Abusive language\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.flagFeedback(21L, "Abusive language");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void flagFeedbackForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/flags")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.flagFeedback(21L, "Abusive language");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void flagFeedbackAlreadyFlaggedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/flags"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Already flagged\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.flagFeedback(21L, "Abusive language");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 409");
    }

    @Test
    void flagFeedbackReplyPostsReason() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/feedback/21/reply/flags"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"reason\":\"Abusive language\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.flagFeedbackReply(21L, "Abusive language");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void flagFeedbackReplyForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/feedback/21/reply/flags")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.flagFeedbackReply(21L, "Abusive language");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
