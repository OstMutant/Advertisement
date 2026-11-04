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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
        assertThat(errorText(result)).startsWith("HTTP 401");
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
        assertThat(errorText(result)).startsWith("HTTP 401");
    }

    @Test
    void updateMySettingsSendsMergePatchWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/me/settings"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/merge-patch+json"))
                .andExpect(content().string("{\"adsPageSize\":20}"))
                .andRespond(withSuccess("{\"adsPageSize\":20}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.updateMySettings("\"2\"", Map.of("adsPageSize", 20));

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("adsPageSize", 20));
        server.verify();
    }

    @Test
    void updateMySettingsInvalidPageSizeBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"Page size out of range\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateMySettings("\"2\"", Map.of("adsPageSize", 0));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 400: Page size out of range");
    }

    @Test
    void updateMySettingsStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateMySettings("\"1\"", Map.of("adsPageSize", 20));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void changeMyLocalePutsLocaleAsJson() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/me/locale"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"locale\":\"uk\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.changeMyLocale("uk");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void changeMyLocaleUnsupportedLocaleBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/locale"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"Unsupported locale\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.changeMyLocale("de");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 400: Unsupported locale");
    }

    @Test
    void getMySettingsHistoryReturnsItems() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/me/settings/history"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"snapshotId\":11,\"version\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getMySettingsHistory();

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        assertThat((List<?>) structured(result).get("items")).hasSize(1);
        server.verify();
    }

    @Test
    void getMySettingsHistoryNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings/history")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getMySettingsHistory();

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getMySettingsHistorySnapshotReturnsRecordedState() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/me/settings/history/11"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"snapshot\":{\"adsPageSize\":20},\"version\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getMySettingsHistorySnapshot(11L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("version", 2);
        server.verify();
    }

    @Test
    void getMySettingsHistorySnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings/history/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getMySettingsHistorySnapshot(99L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void restoreMySettingsFromSnapshotPostsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/me/settings/history/11/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withSuccess("{\"adsPageSize\":20}", MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.restoreMySettingsFromSnapshot(11L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("adsPageSize", 20));
        server.verify();
    }

    @Test
    void restoreMySettingsFromSnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings/history/99/restore")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.restoreMySettingsFromSnapshot(99L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void restoreMySettingsFromSnapshotStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/me/settings/history/11/restore"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.restoreMySettingsFromSnapshot(11L, "\"1\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }
}
