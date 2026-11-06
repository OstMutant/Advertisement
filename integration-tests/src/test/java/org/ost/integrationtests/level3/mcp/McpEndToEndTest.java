package org.ost.integrationtests.level3.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.advertisement.config.AdvertisementAutoConfiguration;
import org.ost.apikey.config.ApiKeyAutoConfiguration;
import org.ost.attachment.config.AttachmentAutoConfiguration;
import org.ost.attachment.services.StorageService;
import org.ost.audit.config.AuditAutoConfiguration;
import org.ost.contact.config.ContactAutoConfiguration;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.RestApiTestAutoConfig;
import org.ost.integrationtests.support.RestApiTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.mcp.config.McpAutoConfiguration;
import org.ost.orchestrator.config.OrchestratorAutoConfiguration;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.promotion.config.PromotionAutoConfiguration;
import org.ost.restapi.config.RestApiAutoConfiguration;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.user.config.UserAutoConfiguration;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the MCP endpoint over real HTTP with JSON-RPC against a real embedded server: compares tools/list by name with the live OpenAPI operation ids, invokes a subset of tools (identity, pagination, single read, create/patch/delete, taxon listing, moderator and user roles) through the real loopback REST call, and checks the Origin filter on initialize. */
@SpringBootTest(
        classes = {
                UserAutoConfiguration.class,
                ApiKeyAutoConfiguration.class,
                TaxonAutoConfiguration.class,
                AttachmentAutoConfiguration.class,
                AuditAutoConfiguration.class,
                AdvertisementAutoConfiguration.class,
                ProviderProfileAutoConfiguration.class,
                ContactAutoConfiguration.class,
                FeedbackAutoConfiguration.class,
                PromotionAutoConfiguration.class,
                OrchestratorAutoConfiguration.class,
                RestApiAutoConfiguration.class,
                RestApiTestSupport.class,
                RepositoryTestSupport.class,
                McpAutoConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.ai.mcp.server.name=marketplace",
                "spring.ai.mcp.server.version=0.0.1",
                "spring.ai.mcp.server.type=SYNC",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp"
        })
@RestApiTestAutoConfig
@ImportAutoConfiguration({
        TomcatServletWebServerAutoConfiguration.class,
        RestClientAutoConfiguration.class,
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        McpServerJsonMapperAutoConfiguration.class,
        ToolCallbackConverterAutoConfiguration.class,
        McpServerAnnotationScannerAutoConfiguration.class,
        McpServerSpecificationFactoryAutoConfiguration.class,
        McpServerAutoConfiguration.class,
        McpServerStreamableHttpWebMvcAutoConfiguration.class
})
class McpEndToEndTest extends AbstractPostgresIntegrationTest {

    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final int PORT = freePort();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @MockitoBean S3Client s3Client;
    @MockitoBean StorageService storageService;

    @Autowired private JdbcClient jdbcClient;

    private final HttpClient http = HttpClient.newHttpClient();
    private long userId;
    private String rawKey;
    private String sessionId;
    private int requestId;

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("marketplace.mcp.rest-base-url", () -> "http://localhost:" + PORT);
    }

    @BeforeEach
    void registerUserAndOpenSession() throws Exception {
        TestDataCleaner.cleanAll(jdbcClient);
        Account first = registerAccount("Mcp User");
        userId = first.id();
        rawKey = first.key();

        HttpResponse<String> init = mcpPost(null, request("initialize", """
                {"protocolVersion":"%s","capabilities":{},"clientInfo":{"name":"it","version":"1"}}""".formatted(PROTOCOL_VERSION)));
        assertThat(init.statusCode()).isEqualTo(200);
        sessionId = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
        HttpResponse<String> initialized = mcpPost(sessionId, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertThat(initialized.statusCode()).isEqualTo(202);
    }

    @Test
    void toolsList_returnsOneToolForEveryOperationIdOfTheLiveOpenApi() throws Exception {
        JsonNode tools = MAPPER.readTree(extractJson(mcpPost(sessionId, request("tools/list", "{}")).body())).path("result").path("tools");
        List<String> names = new ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asString()));

        Set<String> operationIds = new HashSet<>();
        MAPPER.readTree(rest("GET", "/v3/api-docs", null, null).body()).path("paths").forEach(pathItem ->
                pathItem.forEach(operation -> {
                    String operationId = operation.path("operationId").asString();
                    if (!operationId.isBlank()) {
                        operationIds.add(operationId);
                    }
                }));
        assertThat(names).hasSameSizeAs(operationIds).containsExactlyInAnyOrderElementsOf(operationIds);
    }

    @Test
    void getMe_withKey_returnsTheRegisteredUser() throws Exception {
        JsonNode result = callTool("getMe", "{}", "Bearer " + rawKey);
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("item").path("id").asLong()).isEqualTo(userId);
    }

    @Test
    void getMe_withoutKey_isAnErrorStartingWithHttp401() throws Exception {
        JsonNode result = callTool("getMe", "{}", null);
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").path(0).path("text").asString()).startsWith("HTTP 401");
    }

    @Test
    void listAdvertisements_paginatesTwentyOneAdsOverTwoPages() throws Exception {
        for (int i = 0; i < 21; i++) {
            createAdvertisement("Paged ad " + i);
        }

        JsonNode first = callTool("listAdvertisements", "{\"page\":0}", null).path("structuredContent");
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(first.path("nextPage").asInt()).isEqualTo(1);
        assertThat(first.path("returned").asInt()).isEqualTo(20);
        assertThat(first.path("totalCount").asInt()).isEqualTo(21);

        JsonNode second = callTool("listAdvertisements", "{\"page\":1}", null).path("structuredContent");
        assertThat(second.path("returned").asInt()).isEqualTo(1);
        assertThat(second.path("hasMore").asBoolean()).isFalse();
        assertThat(second.path("nextPage").isNull()).isTrue();
    }

    @Test
    void getAdvertisement_returnsTheItemAndAnEtag() throws Exception {
        long adId = createAdvertisement("Single ad");

        JsonNode result = callTool("getAdvertisement", "{\"id\":" + adId + "}", null);
        assertThat(result.path("isError").asBoolean()).isFalse();
        JsonNode structured = result.path("structuredContent");
        assertThat(structured.path("item").path("title").asString()).isEqualTo("Single ad");
        assertThat(structured.path("etag").isNull()).isFalse();
        assertThat(structured.path("etag").asString()).isNotBlank();
    }

    @Test
    void listTaxons_isNotAnError() throws Exception {
        assertThat(callTool("listTaxons", "{}", null).path("isError").asBoolean()).isFalse();
    }

    @Test
    void createPatchAndDeleteAnAdvertisement_throughTheWriteTools() throws Exception {
        String bearer = "Bearer " + rawKey;
        JsonNode created = callTool("createAdvertisement",
                "{\"title\":\"Tool ad\",\"description\":\"Made by a tool\",\"adKind\":\"OFFER\"}", bearer);
        assertThat(created.path("isError").asBoolean()).isFalse();
        assertThat(created.path("structuredContent").path("status").asInt()).isEqualTo(201);
        long adId = created.path("structuredContent").path("item").path("id").asLong();

        String etag = callTool("getAdvertisement", "{\"id\":" + adId + "}", null).path("structuredContent").path("etag").asString();
        JsonNode patched = callTool("patchAdvertisement",
                "{\"id\":%d,\"etag\":%s,\"patch\":{\"title\":\"Patched by a tool\"}}".formatted(adId, MAPPER.writeValueAsString(etag)), bearer);
        assertThat(patched.path("isError").asBoolean()).isFalse();

        JsonNode read = callTool("getAdvertisement", "{\"id\":" + adId + "}", null).path("structuredContent");
        assertThat(read.path("item").path("title").asString()).isEqualTo("Patched by a tool");

        JsonNode deleted = callTool("deleteAdvertisement",
                "{\"id\":%d,\"etag\":%s}".formatted(adId, MAPPER.writeValueAsString(read.path("etag").asString())), bearer);
        assertThat(deleted.path("isError").asBoolean()).isFalse();
        assertThat(deleted.path("structuredContent").path("status").asInt()).isEqualTo(204);

        JsonNode gone = callTool("getAdvertisement", "{\"id\":" + adId + "}", null);
        assertThat(gone.path("isError").asBoolean()).isTrue();
        assertThat(gone.path("content").path(0).path("text").asString()).startsWith("HTTP 404");
    }

    @Test
    void privilegedTools_followTheRoleOfTheKey() throws Exception {
        Account moderator = registerAccount("Mcp Moderator");
        jdbcClient.sql("UPDATE user_information SET role = 'MODERATOR' WHERE id = :id").param("id", moderator.id()).update();
        Account plain = registerAccount("Mcp Plain");
        String translations = "[{\"locale\":\"en\",\"name\":\"Plumbing\",\"description\":\"Plumbing services\"},"
                + "{\"locale\":\"uk\",\"name\":\"Сантехніка\",\"description\":\"Сантехнічні послуги\"}]";
        String createTaxon = "{\"type\":\"CATEGORY\",\"translations\":%s}".formatted(translations);

        JsonNode queue = callTool("getModerationQueue", "{}", "Bearer " + moderator.key());
        assertThat(queue.path("isError").asBoolean()).isFalse();
        assertThat(queue.path("structuredContent").path("items").isArray()).isTrue();

        JsonNode taxon = callTool("createTaxon", createTaxon, "Bearer " + moderator.key());
        assertThat(taxon.path("isError").asBoolean()).isFalse();
        assertThat(taxon.path("structuredContent").path("status").asInt()).isEqualTo(201);

        JsonNode queueAsUser = callTool("getModerationQueue", "{}", "Bearer " + plain.key());
        assertThat(queueAsUser.path("isError").asBoolean()).isTrue();
        assertThat(queueAsUser.path("content").path(0).path("text").asString()).startsWith("HTTP 403");

        JsonNode taxonAsUser = callTool("createTaxon", createTaxon, "Bearer " + plain.key());
        assertThat(taxonAsUser.path("isError").asBoolean()).isTrue();
        assertThat(taxonAsUser.path("content").path(0).path("text").asString()).startsWith("HTTP 403");
    }

    @Test
    void initialize_withAForeignOrigin_isForbiddenAndOpensNoSession() throws Exception {
        HttpResponse<String> response = initializeWithOrigin("http://evil.example");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isEmpty();
    }

    @Test
    void initialize_withALocalOrigin_opensASession() throws Exception {
        HttpResponse<String> response = initializeWithOrigin("http://localhost:" + PORT);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isPresent();
    }

    private HttpResponse<String> initializeWithOrigin(String origin) throws Exception {
        String body = request("initialize", """
                {"protocolVersion":"%s","capabilities":{},"clientInfo":{"name":"it","version":"1"}}""".formatted(PROTOCOL_VERSION));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/event-stream")
                .header("Origin", origin)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Account registerAccount(String name) throws Exception {
        String email = "mcp-" + System.nanoTime() + "@example.com";
        JsonNode user = MAPPER.readTree(rest("POST", "/api/users", null, """
                {"name":"%s","email":"%s","password":"password123"}""".formatted(name, email)).body());
        String basic = Base64.getEncoder().encodeToString((email + ":password123").getBytes());
        JsonNode key = MAPPER.readTree(rest("POST", "/api/api-keys", "Basic " + basic, null).body());
        return new Account(user.path("id").asLong(), key.path("rawKey").asString());
    }

    /** The registered user's id and the raw API key the tests authenticate with. */
    private record Account(long id, String key) {}

    private long createAdvertisement(String title) throws Exception {
        String body = """
                {"title":"%s","description":"Description","adKind":"OFFER"}""".formatted(title);
        HttpResponse<String> response = rest("POST", "/api/advertisements", "Bearer " + rawKey, body);
        assertThat(response.statusCode()).isEqualTo(201);
        return MAPPER.readTree(response.body()).path("id").asLong();
    }

    private JsonNode callTool(String name, String arguments, String authorization) throws Exception {
        String params = "{\"name\":\"%s\",\"arguments\":%s}".formatted(name, arguments);
        HttpResponse<String> response = mcpPost(sessionId, request("tools/call", params), authorization);
        assertThat(response.statusCode()).isEqualTo(200);
        return MAPPER.readTree(extractJson(response.body())).path("result");
    }

    private String request(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(++requestId, method, params);
    }

    private HttpResponse<String> mcpPost(String session, String body) throws Exception {
        return mcpPost(session, body, null);
    }

    private HttpResponse<String> mcpPost(String session, String body, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session != null) {
            builder.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", PROTOCOL_VERSION);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rest(String method, String path, String authorization, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extractJson(String body) {
        return body.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim()).findFirst().orElse(body);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port for the embedded server", e);
        }
    }
}
