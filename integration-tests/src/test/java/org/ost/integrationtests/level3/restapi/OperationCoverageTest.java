package org.ost.integrationtests.level3.restapi;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.ExercisedOperations;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Level 3 coverage check: every REST operation of marketplace-rest-api must be reached by at least one scenario request or be listed, with a reason, in the allow-list. A whole-module run through build.sh (level3.requireCoverage=true) fails when a scenario class did not run; any other run skips. */
@Slf4j
@Level3ScenarioTest
@Order(Integer.MAX_VALUE)
class OperationCoverageTest extends AbstractRestApiScenarioTest {

    private static final String ALLOW_LIST = "level3/operation-coverage-allowlist.txt";
    private static final String REQUIRE_COVERAGE = "level3.requireCoverage";

    @Autowired private ApplicationContext applicationContext;

    @Test
    void everyOperationIsExercisedByAScenarioOrAllowListed() throws IOException {
        List<String> missing = scenarioClassesNotRun();
        if (!missing.isEmpty() && Boolean.getBoolean(REQUIRE_COVERAGE)) {
            fail("Level 3 scenario classes that did not run (a whole-module run through build.sh requires all of them): " + missing);
        }
        assumeTrue(missing.isEmpty(), "coverage is only meaningful when every Level 3 scenario class ran: " + missing);
        Map<String, String> operations = declaredOperations();
        Set<String> exercised = ExercisedOperations.requests();
        Map<String, Set<Integer>> statuses = ExercisedOperations.statuses();
        operations.forEach((key, operationId) -> log.info("OPERATION-STATUSES {} ({}) -> {}", operationId, key, statuses.getOrDefault(key, Set.of())));
        Map<String, String> allowed = readAllowList();
        List<String> uncovered = operations.keySet().stream()
                .filter(key -> !exercised.contains(key) && !allowed.containsKey(key))
                .sorted()
                .map(key -> operations.get(key) + " (" + key + ")")
                .toList();
        List<String> stale = allowed.keySet().stream()
                .filter(key -> !operations.containsKey(key) || exercised.contains(key))
                .sorted()
                .toList();
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(uncovered).as("operations no scenario request reached and not allow-listed").isEmpty();
            soft.assertThat(stale).as("allow-list entries that are unknown or now covered -- remove them").isEmpty();
        });
    }

    /** Maps every "METHOD /path" declared by a marketplace-rest-api handler to its operationId. */
    private Map<String, String> declaredOperations() {
        RequestMappingHandlerMapping mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        TreeMap<String, String> result = new TreeMap<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (!ClassUtils.getUserClass(handler.getBeanType()).getPackageName().startsWith("org.ost.restapi")) {
                return;
            }
            if (info.getPathPatternsCondition() == null) {
                throw new IllegalStateException("handler mapping must use PathPatternParser");
            }
            String operationId = operationId(handler);
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                    result.put(ExercisedOperations.key(method.name(), pattern), operationId);
                }
            }
        });
        return result;
    }

    private static String operationId(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        return operation == null ? handler.getMethod().getName() : operation.operationId();
    }

    /** Sorted names of the Level 3 scenario classes found on the classpath that did not run in this JVM. */
    private List<String> scenarioClassesNotRun() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Level3ScenarioTest.class));
        Set<String> expected = scanner.findCandidateComponents("org.ost.integrationtests").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> !name.equals(OperationCoverageTest.class.getName()))
                .collect(Collectors.toSet());
        Set<String> ran = ExercisedOperations.scenarioClasses().stream()
                .map(Class::getName)
                .collect(Collectors.toSet());
        return expected.stream().filter(name -> !ran.contains(name)).sorted().toList();
    }

    /** Reads "METHOD /path # reason" lines; blank lines and lines starting with '#' are skipped. */
    private Map<String, String> readAllowList() throws IOException {
        String content = new ClassPathResource(ALLOW_LIST).getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> allowed = new LinkedHashMap<>();
        for (String raw : content.lines().toList()) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(" # ");
            if (separator < 0) {
                throw new IllegalStateException("allow-list line has no reason: " + line);
            }
            String key = line.substring(0, separator).trim();
            String reason = line.substring(separator + 3).trim();
            if (reason.isBlank()) {
                throw new IllegalStateException("allow-list line has a blank reason: " + line);
            }
            allowed.put(key, reason);
        }
        return allowed;
    }
}
