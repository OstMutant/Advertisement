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

/** Covers the URLs, bodies, result shapes and error mapping of {@link UserTools}. */
class UserToolsTest extends AbstractToolTest {

    private UserTools tools;

    @BeforeEach
    void setUp() {
        tools = new UserTools(restApi);
    }

    @Test
    void registerUserPostsNameEmailAndPasswordWithoutAuthorization() {
        server.expect(requestTo("http://api.test/api/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"name\":\"Ann\",\"email\":\"ann@example.com\",\"password\":\"password123\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":4,\"name\":\"Ann\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.registerUser("Ann", "ann@example.com", "password123");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 201)
                .containsEntry("item", Map.of("id", 4, "name", "Ann"));
        server.verify();
    }

    @Test
    void registerUserEmailTakenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Email already registered\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.registerUser("Ann", "ann@example.com", "password123");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 409: Email already registered");
    }

    @Test
    void registerUserRateLimitedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "60")
                        .body("{\"message\":\"Too many attempts\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.registerUser("Ann", "ann@example.com", "password123");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result))
                .startsWith("HTTP 429: rate limit reached. Retry after 60 seconds");
    }

    @Test
    void listUsersWithoutFiltersAsksFirstPage() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users?page=0"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON).header("X-Total-Count", "2"));

        CallToolResult result = tools.listUsers(null, null, null, null, null, null, null, null, null, null, null);

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
    void listUsersPassesEveryFilterEncoded() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users?page=1"
                        + "&name=Ann&email=ann%40example.com&roles=ADMIN%2CMODERATOR"
                        + "&createdAtStart=2026-01-01T00%3A00%3A00Z&createdAtEnd=2026-02-01T00%3A00%3A00Z"
                        + "&updatedAtStart=2026-01-02T00%3A00%3A00Z&updatedAtEnd=2026-02-02T00%3A00%3A00Z"
                        + "&startId=1&endId=9&sort=name%2Casc"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.listUsers(1, "Ann", "ann@example.com", List.of("ADMIN", "MODERATOR"),
                "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "2026-01-02T00:00:00Z", "2026-02-02T00:00:00Z",
                1L, 9L, "name,asc");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("page", 1);
        server.verify();
    }

    @Test
    void listUsersForbiddenBecomesErrorResult() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users?page=0")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.listUsers(null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void getUserReturnsItemAndEtag() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"id\":3,\"name\":\"Ann\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"2\""));

        CallToolResult result = tools.getUser(3L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("item", Map.of("id", 3, "name", "Ann"))
                .containsEntry("etag", "\"2\"");
        server.verify();
    }

    @Test
    void getUserNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getUser(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void updateUserPutsNameAndRoleWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string("{\"name\":\"Ann\",\"role\":\"MODERATOR\"}"))
                .andRespond(withSuccess("{\"id\":3,\"name\":\"Ann\",\"role\":\"MODERATOR\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.updateUser(3L, "\"2\"", "Ann", "MODERATOR");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"")
                .containsEntry("item", Map.of("id", 3, "name", "Ann", "role", "MODERATOR"));
        server.verify();
    }

    @Test
    void updateUserForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/3")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.updateUser(3L, "\"2\"", "Ann", "ADMIN");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void updateUserStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/3"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.updateUser(3L, "\"1\"", "Ann", "MODERATOR");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }

    @Test
    void deleteUserSendsWithoutBodyOrIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(headerDoesNotExist(HttpHeaders.IF_MATCH))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteUser(3L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteOwnAdministratorAccountBecomesErrorResult() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\":\"An administrator cannot delete their own account\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.deleteUser(1L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void deleteUserNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.deleteUser(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getUserHistoryReturnsItems() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3/history"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[{\"snapshotId\":11,\"version\":2}]", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getUserHistory(3L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("totalCount", 1);
        assertThat((List<?>) structured(result).get("items")).hasSize(1);
        server.verify();
    }

    @Test
    void getUserHistoryNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/9/history")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getUserHistory(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getUserHistorySnapshotReturnsRecordedState() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3/history/11"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"snapshot\":{\"name\":\"Ann\",\"role\":\"USER\"},\"version\":2}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getUserHistorySnapshot(3L, 11L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("version", 2);
        server.verify();
    }

    @Test
    void getUserHistorySnapshotNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/3/history/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getUserHistorySnapshot(3L, 99L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void restoreUserFromSnapshotPostsWithIfMatch() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/users/3/history/11/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"2\""))
                .andRespond(withSuccess("{\"id\":3,\"name\":\"Ann\",\"role\":\"USER\"}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        CallToolResult result = tools.restoreUserFromSnapshot(3L, 11L, "\"2\"");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("etag", "\"3\"");
        server.verify();
    }

    @Test
    void restoreUserFromSnapshotForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/3/history/11/restore")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.restoreUserFromSnapshot(3L, 11L, "\"2\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void restoreUserFromSnapshotStaleEtagBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/users/3/history/11/restore"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED)
                        .body("{\"message\":\"Stale version\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.restoreUserFromSnapshot(3L, 11L, "\"1\"");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 412");
    }
}
