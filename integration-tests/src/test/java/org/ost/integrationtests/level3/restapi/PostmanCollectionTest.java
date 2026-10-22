package org.ost.integrationtests.level3.restapi;

import io.swagger.v3.oas.annotations.Operation;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Checks that every request of the Postman collection names an existing operation and sends the same method and path. */
@Level3ScenarioTest
class PostmanCollectionTest extends AbstractRestApiScenarioTest {

    private static final String COLLECTION = "docs/api-collections/postman/marketplace-rest-api.postman_collection.json";
    private static final String PATH_VARIABLE = "\\{\\{?[^}]*\\}\\}?";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private ApplicationContext applicationContext;

    @Test
    void everyCollectionRequestNamesAnExistingOperation() throws IOException {
        Map<String, List<Endpoint>> operations = declaredOperations();
        List<CollectionRequest> requests = collectionRequests(repoRoot().resolve(COLLECTION));
        List<String> unknownOperations = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        for (CollectionRequest request : requests) {
            List<Endpoint> endpoints = operations.get(request.name());
            if (endpoints == null) {
                unknownOperations.add("request '" + request.name() + "' names no operation");
            } else if (!endpoints.contains(request.endpoint())) {
                mismatches.add("request '" + request.name() + "' sends " + request.endpoint()
                        + " but its operation is " + endpoints);
            }
        }
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(unknownOperations).as("collection requests naming an unknown operationId").isEmpty();
            soft.assertThat(mismatches).as("collection requests whose method or path differs from their operation").isEmpty();
        });
    }

    /** Maps every operationId declared by a marketplace-rest-api handler to its method and normalised path. */
    private Map<String, List<Endpoint>> declaredOperations() {
        RequestMappingHandlerMapping mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<String, List<Endpoint>> result = new TreeMap<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (!ClassUtils.getUserClass(handler.getBeanType()).getPackageName().startsWith("org.ost.restapi")) {
                return;
            }
            String operationId = operationId(handler);
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                    result.computeIfAbsent(operationId, k -> new ArrayList<>()).add(new Endpoint(method.name(), normalise(pattern)));
                }
            }
        });
        return result;
    }

    private static String operationId(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        return operation == null ? handler.getMethod().getName() : operation.operationId();
    }

    private static List<CollectionRequest> collectionRequests(Path collection) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(collection));
        List<CollectionRequest> result = new ArrayList<>();
        collectItems(root.path("item"), result);
        return result;
    }

    private static void collectItems(JsonNode items, List<CollectionRequest> result) {
        for (JsonNode item : items) {
            if (item.has("request")) {
                JsonNode request = item.path("request");
                String path = requestPath(request.path("url"));
                result.add(new CollectionRequest(item.path("name").asString(),
                        new Endpoint(request.path("method").asString(), normalise(path))));
            }
            collectItems(item.path("item"), result);
        }
    }

    private static String requestPath(JsonNode url) {
        JsonNode segments = url.path("path");
        if (segments.isArray()) {
            List<String> parts = new ArrayList<>();
            segments.forEach(segment -> parts.add(segment.asString()));
            return "/" + String.join("/", parts);
        }
        String path = url.path("raw").asString().replace("{{host}}", "");
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    /** Replaces every path variable, in either Postman {{var}} or Spring {var} form, with a placeholder so names are ignored. */
    private static String normalise(String path) {
        return path.replaceAll(PATH_VARIABLE, "{}");
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docs/api-collections/postman"))) {
            dir = dir.getParent();
        }
        return Objects.requireNonNull(dir, "no ancestor of the working directory contains docs/api-collections/postman");
    }

    private record Endpoint(String method, String path) {
    }

    private record CollectionRequest(String name, Endpoint endpoint) {
    }
}
