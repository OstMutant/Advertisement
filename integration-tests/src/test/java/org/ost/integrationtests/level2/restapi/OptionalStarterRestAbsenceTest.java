package org.ost.integrationtests.level2.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.RepositoryTestAutoConfig;
import org.ost.integrationtests.support.RestApiTestAutoConfig;
import org.ost.integrationtests.support.RestApiTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Level 2: every operation of the REST API, called against a real Postgres once with every starter present and once for each
 * optional starter with its configuration left out, answers without a server fault; the operations listed for that starter answer 503.
 */
class OptionalStarterRestAbsenceTest extends AbstractPostgresIntegrationTest {

    /** One optional starter: its name (the test's display name), its base package and the operations that must answer 503 when it is the one left out. */
    private record Starter(String name, String basePackage, List<String> unavailableOperations) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Starter> optionalStarters() {
        return Stream.of(
                new Starter("audit", "org.ost.audit.", List.of()),
                new Starter("attachment", "org.ost.attachment.", List.of()),
                new Starter("advertisement", "org.ost.advertisement.", List.of()),
                new Starter("provider-profile", "org.ost.provider.", List.of()),
                new Starter("apikey", "org.ost.apikey.", List.of("POST /api/api-keys")),
                new Starter("contact", "org.ost.contact.", List.of()),
                new Starter("feedback", "org.ost.feedback.", List.of(
                        "POST /api/moderation/feedback/{id}/approve", "POST /api/moderation/feedback/{id}/confirm-violation",
                        "POST /api/moderation/replies/{id}/approve", "POST /api/moderation/replies/{id}/confirm-violation")),
                new Starter("promotion", "org.ost.promotion.", List.of()),
                new Starter("taxon", "org.ost.taxon.", List.of()));
    }

    @Test
    void everyOperationAnswersWithEveryStarterPresent() {
        assertThat(faults(callAllOperations(null))).isEmpty();
    }

    @ParameterizedTest(name = "every operation answers without the {0} starter")
    @MethodSource("optionalStarters")
    void everyOperationAnswersWithoutOneOptionalStarter(Starter starter) {
        Map<String, String> statuses = callAllOperations(starter.basePackage());

        assertThat(faults(statuses)).isEmpty();
        assertThat(starter.unavailableOperations()).allSatisfy(operation -> assertThat(statuses).containsEntry(operation, "503"));
    }

    /** A server fault is a 5xx other than 503, a request that ended in an unhandled exception, or a context that did not start. */
    private static List<String> faults(Map<String, String> statuses) {
        return statuses.entrySet().stream()
                .filter(e -> (e.getValue().startsWith("5") && !e.getValue().equals("503")) || e.getValue().startsWith("EXC") || e.getKey().equals("CONTEXT"))
                .map(e -> e.getKey() + " -> " + e.getValue())
                .toList();
    }

    private Map<String, String> callAllOperations(String hiddenPackage) {
        Map<String, String> statuses = new TreeMap<>();
        runner(hiddenPackage).run(context -> {
            if (context.getStartupFailure() != null) {
                statuses.put("CONTEXT", String.valueOf(context.getStartupFailure().getMessage()));
                return;
            }
            TestDataCleaner.cleanAll(context.getBean(JdbcClient.class));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            String authorization = authorization(mvc);
            for (String[] operation : operations(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class))) {
                // Every path variable is an id that does not exist, so no call changes the data the next one needs.
                String url = operation[1].replaceAll("\\{[^}]+}", "999999");
                try {
                    var builder = request(HttpMethod.valueOf(operation[0]), url).header(HttpHeaders.AUTHORIZATION, authorization);
                    if (!operation[0].equals("GET") && !operation[0].equals("DELETE")) {
                        builder = builder.contentType(MediaType.APPLICATION_JSON).content("{}");
                    }
                    statuses.put(operation[0] + " " + operation[1], String.valueOf(mvc.perform(builder).andReturn().getResponse().getStatus()));
                } catch (Exception e) {
                    statuses.put(operation[0] + " " + operation[1], "EXC " + rootOf(e).getClass().getSimpleName());
                }
            }
        });
        return statuses;
    }

    /** Every operation of the REST adapter as {method, path pattern}, reads first and deletes last. */
    private static List<String[]> operations(RequestMappingHandlerMapping mapping) {
        List<String[]> operations = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType().getName().startsWith("org.ost.restapi") && info.getPathPatternsCondition() != null) {
                List<String> methods = info.getMethodsCondition().getMethods().isEmpty()
                        ? List.of("GET") : info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
                methods.forEach(method -> info.getPathPatternsCondition().getPatternValues()
                        .forEach(pattern -> operations.add(new String[]{method, pattern})));
            }
        });
        operations.sort((a, b) -> Integer.compare(order(a[0]), order(b[0])));
        return operations;
    }

    private static int order(String method) {
        return switch (method) {
            case "GET" -> 0;
            case "POST" -> 1;
            case "PUT", "PATCH" -> 2;
            default -> 3;
        };
    }

    private static Throwable rootOf(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    /** Registers the first user (an administrator) and returns a bearer key, or the basic credentials when no key can be issued. */
    private static String authorization(MockMvc mvc) {
        String email = "absence-" + System.nanoTime() + "@example.com";
        String basic = "Basic " + Base64.getEncoder().encodeToString((email + ":password123").getBytes());
        try {
            mvc.perform(request(HttpMethod.POST, "/api/users").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Admin\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)));
            String body = mvc.perform(request(HttpMethod.POST, "/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic))
                    .andReturn().getResponse().getContentAsString();
            return body.contains("rawKey") ? "Bearer " + JsonScenarioUtils.extractStringField(body, "rawKey") : basic;
        } catch (Exception e) {
            return basic;
        }
    }

    private WebApplicationContextRunner runner(String hiddenPackage) {
        List<Class<?>> modules = new ArrayList<>();
        for (String name : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates()) {
            // The MCP adapter reaches the REST API over HTTP and is not part of this check.
            if (name.startsWith("org.ost.") && !name.startsWith("org.ost.mcp.") && (hiddenPackage == null || !name.startsWith(hiddenPackage))) {
                modules.add(load(name));
            }
        }
        modules.add(RestApiTestSupport.class);
        modules.add(AuditingConfig.class);
        List<Class<?>> infrastructure = new ArrayList<>(List.of(RepositoryTestAutoConfig.class.getAnnotation(ImportAutoConfiguration.class).value()));
        infrastructure.addAll(List.of(RestApiTestAutoConfig.class.getAnnotation(ImportAutoConfiguration.class).value()));
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(infrastructure.toArray(Class<?>[]::new)))
                .withUserConfiguration(modules.toArray(Class<?>[]::new))
                .withPropertyValues(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.datasource.hikari.maximum-pool-size=2",
                        "storage.s3.endpoint=http://localhost:9000",
                        "storage.s3.region=us-east-1",
                        "storage.s3.access-key=test",
                        "storage.s3.secret-key=test",
                        "storage.s3.bucket=test",
                        "storage.s3.public-url=http://localhost:9000/test",
                        "app.public-base-url=http://localhost:8080");
        // RestApiTestSupport asks for the audit starter's mapper, so without that starter the test wiring supplies a plain one.
        return "org.ost.audit.".equals(hiddenPackage)
                ? runner.withBean("auditObjectMapper", ObjectMapper.class, ObjectMapper::new) : runner;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Auditing for the entities' created-by and changed-by columns, which a Level 3 context enables through its repository support class. */
    @TestConfiguration
    @EnableJdbcAuditing
    static class AuditingConfig {
    }
}
