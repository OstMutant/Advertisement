package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URL, result shape, paging and error mapping of {@link AuditTools}. */
class AuditToolsTest extends AbstractToolTest {

    private AuditTools tools;

    @BeforeEach
    void setUp() {
        tools = new AuditTools(restApi);
    }

    @Test
    void getAuditTimelineWithoutFiltersAsksFirstPage() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/audit/timeline?page=0"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "2"));

        CallToolResult result = tools.getAuditTimeline(null, null, null, null, null, null, null);

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
    void getAuditTimelinePassesEveryFilterEncoded() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/audit/timeline?page=1"
                        + "&actorIds=3%2C4&entityTypes=ADVERTISEMENT%2CUSER&actionTypes=CREATED%2CDELETED"
                        + "&fromDate=2026-01-01T00%3A00%3A00Z&toDate=2026-02-01T00%3A00%3A00Z&sort=createdAt%2Casc"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getAuditTimeline(1, List.of(3L, 4L), List.of("ADVERTISEMENT", "USER"),
                List.of("CREATED", "DELETED"), "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "createdAt,asc");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("page", 1);
        server.verify();
    }

    @Test
    void getAuditTimelineNextLinkGivesHasMoreAndNextPage() {
        server.expect(requestTo("http://api.test/api/audit/timeline?page=0"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<http://api.test/api/audit/timeline?page=1>; rel=\"next\""));

        CallToolResult result = tools.getAuditTimeline(0, null, null, null, null, null, null);

        assertThat(structured(result))
                .containsEntry("hasMore", true)
                .containsEntry("nextPage", 1);
        server.verify();
    }

    @Test
    void getAuditTimelineForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/audit/timeline?page=0")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.getAuditTimeline(null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void getAuditTimelineWithoutKeyBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/audit/timeline?page=0")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CallToolResult result = tools.getAuditTimeline(null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 401");
    }
}
