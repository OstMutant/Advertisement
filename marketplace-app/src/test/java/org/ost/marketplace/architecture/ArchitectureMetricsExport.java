package org.ost.marketplace.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.metrics.ArchitectureMetrics;
import com.tngtech.archunit.library.metrics.ComponentDependencyMetrics;
import com.tngtech.archunit.library.metrics.MetricsComponents;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes real Efferent/Afferent Coupling, Instability, and Abstractness per module from the
 * actual class-level dependency graph (ArchUnit's ArchitectureMetrics.componentDependencyMetrics)
 * and writes them to a fixed path — generate-architecture-model.sh reads this file if present, no
 * external server required, unlike the SonarQube-sourced metrics.
 */
@AnalyzeClasses(packages = "org.ost", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureMetricsExport {

    private static final Map<String, String> MODULE_PACKAGES = new LinkedHashMap<>();

    static {
        MODULE_PACKAGES.put("audit-spring-boot-starter", "org.ost.audit");
        MODULE_PACKAGES.put("attachment-spring-boot-starter", "org.ost.attachment");
        MODULE_PACKAGES.put("user-spring-boot-starter", "org.ost.user");
        MODULE_PACKAGES.put("advertisement-spring-boot-starter", "org.ost.advertisement");
        MODULE_PACKAGES.put("taxon-spring-boot-starter", "org.ost.taxon");
        MODULE_PACKAGES.put("provider-profile-spring-boot-starter", "org.ost.provider");
        MODULE_PACKAGES.put("platform-commons", "org.ost.platform");
        MODULE_PACKAGES.put("marketplace-app", "org.ost.marketplace");
        MODULE_PACKAGES.put("query-lib", "org.ost.query");
    }

    @ArchTest
    static void export_architecture_metrics(JavaClasses classes) throws IOException {
        List<JavaPackage> packages = MODULE_PACKAGES.values().stream()
                .map(classes::getPackage)
                .toList();
        MetricsComponents<com.tngtech.archunit.core.domain.JavaClass> components =
                MetricsComponents.fromPackages(packages);
        ComponentDependencyMetrics metrics = ArchitectureMetrics.componentDependencyMetrics(components);

        Map<String, Object> modulesOut = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : MODULE_PACKAGES.entrySet()) {
            String identifier = entry.getValue();
            Map<String, Object> moduleOut = new LinkedHashMap<>();
            moduleOut.put("efferentCoupling", metrics.getEfferentCoupling(identifier));
            moduleOut.put("afferentCoupling", metrics.getAfferentCoupling(identifier));
            moduleOut.put("instability", metrics.getInstability(identifier));
            moduleOut.put("abstractness", metrics.getAbstractness(identifier));
            modulesOut.put(entry.getKey(), moduleOut);
        }

        File outputFile = new File("target/architecture-metrics.json");
        outputFile.getParentFile().mkdirs();
        new ObjectMapper().writeValue(outputFile, Map.of("modules", modulesOut));
    }
}
