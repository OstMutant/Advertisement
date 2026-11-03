package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, result shapes and error mapping of {@link InfrastructureTools}. */
class InfrastructureToolsTest extends AbstractToolTest {

    private InfrastructureTools tools;

    @BeforeEach
    void setUp() {
        tools = new InfrastructureTools(restApi);
    }

    @Test
    void getHealthReturnsStatusAndBody() {
        server.expect(requestTo("http://api.test/health"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        CallToolResult result = tools.getHealth();

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 200).containsEntry("body", "ok");
        server.verify();
    }

    @Test
    void getHealthWhenServerDownBecomesErrorResult() {
        server.expect(requestTo("http://api.test/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        CallToolResult result = tools.getHealth();

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 503");
    }

    @Test
    void getSitemapReturnsXmlBody() {
        server.expect(requestTo("http://api.test/sitemap.xml"))
                .andRespond(withSuccess("<urlset></urlset>", MediaType.APPLICATION_XML));

        CallToolResult result = tools.getSitemap();

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 200).containsEntry("body", "<urlset></urlset>");
        server.verify();
    }

    @Test
    void getSitemapServerErrorBecomesErrorResult() {
        server.expect(requestTo("http://api.test/sitemap.xml"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CallToolResult result = tools.getSitemap();

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 500");
    }
}
