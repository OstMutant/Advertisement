package org.ost.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards that the tool names are exactly the stable REST operation ids this module exposes. */
class ToolNamesTest {

    private static final Set<String> EXPOSED = Set.of("listAdvertisements", "getAdvertisement", "listAdvertisementMedia",
            "listProviderProfiles", "getProviderProfile", "listProviderProfileFeedback", "getProviderProfileFeedbackAggregate",
            "listTaxons", "getTaxon", "getMySettings", "getMe");

    @Test
    void toolNamesEqualTheExposedOperationIds() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        Set<String> names = scanner.findCandidateComponents("org.ost.mcp.tools").stream()
                .map(definition -> ((AnnotatedBeanDefinition) definition).getMetadata().getClassName())
                .map(ToolNamesTest::load)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, McpTool.class))
                .filter(annotation -> annotation != null)
                .map(McpTool::name)
                .collect(Collectors.toSet());

        assertThat(names).isEqualTo(EXPOSED);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
