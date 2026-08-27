package org.ost.marketplace.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.metrics.ArchitectureMetrics;
import com.tngtech.archunit.library.metrics.ComponentDependencyMetrics;
import com.tngtech.archunit.library.metrics.MetricsComponents;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Computes real Efferent/Afferent Coupling, Instability, and Abstractness per module from the
 * actual class-level dependency graph (ArchUnit's ArchitectureMetrics.componentDependencyMetrics),
 * plus real bytecode-derived method-level SPI caller/implementor edges for every
 * platform-commons *.spi interface (replaces generate-architecture-model.sh's spi_map_json()
 * regex/Javadoc extraction -- see improvement-156), and writes both to a fixed path --
 * generate-architecture-model.sh reads this file if present, no external server required, unlike
 * the SonarQube-sourced metrics.
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
        MODULE_PACKAGES.put("marketplace-orchestrator", "org.ost.orchestrator");
    }

    @ArchTest
    static void export_architecture_metrics(JavaClasses classes) throws IOException {
        List<JavaPackage> packages = MODULE_PACKAGES.values().stream()
                .map(classes::getPackage)
                .toList();
        MetricsComponents<JavaClass> components = MetricsComponents.fromPackages(packages);
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
        new ObjectMapper().writeValue(outputFile, Map.of("modules", modulesOut, "spiEdges", spiEdges(classes)));
    }

    // Real, bytecode-derived method-level caller/implementor edges for every platform-commons
    // *.spi interface -- an implementor is any class assignable to the interface (getAllRawInterfaces(),
    // not a text "implements" match); a caller is any class whose method actually calls one of the
    // interface's methods (getCallsOfSelf(), not a field-declaration text match), with each real
    // (callerMethod, interfaceMethod) call-site pair -- distinguishes a real call from a
    // DI-wiring-only field/import reference, and from generate-architecture-model.sh (improvement-157)
    // shows exactly which of the caller's own methods triggers which interface method.
    private static Map<String, Object> spiEdges(JavaClasses classes) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (JavaClass iface : classes) {
            if (!iface.isInterface() || !isSpiPackage(iface.getPackageName())) continue;

            List<Map<String, Object>> implementations = new ArrayList<>();
            for (JavaClass candidate : classes) {
                if (!candidate.equals(iface) && candidate.getAllRawInterfaces().contains(iface)) {
                    implementations.add(classInfo(candidate, null));
                }
            }

            // Pair key "callerMethod\u0001interfaceMethod" in a TreeSet -- dedups repeat call sites
            // (the same caller method calling the same interface method twice) and sorts
            // deterministically, independent of ArchUnit's own iteration order (see improvement-173's
            // determinism fixes for why that matters here).
            Map<String, TreeSet<String>> pairsByCaller = new LinkedHashMap<>();
            Map<String, JavaClass> callerClassByName = new LinkedHashMap<>();
            for (JavaMethod method : iface.getMethods()) {
                for (JavaMethodCall call : method.getCallsOfSelf()) {
                    JavaClass callerOwner = call.getOrigin().getOwner();
                    String ownerName = callerOwner.getFullName();
                    pairsByCaller.computeIfAbsent(ownerName, k -> new TreeSet<>())
                            .add(call.getOrigin().getName() + "\u0001" + method.getName());
                    callerClassByName.putIfAbsent(ownerName, callerOwner);
                }
            }
            List<Map<String, Object>> callers = new ArrayList<>();
            for (Map.Entry<String, JavaClass> entry : callerClassByName.entrySet()) {
                List<Map<String, String>> calls = new ArrayList<>();
                for (String pairKey : pairsByCaller.get(entry.getKey())) {
                    String[] parts = pairKey.split("\u0001", 2);
                    calls.add(Map.of("from", parts[0], "to", parts[1]));
                }
                callers.add(classInfo(entry.getValue(), calls));
            }

            List<String> allMethods = iface.getMethods().stream()
                    .map(JavaMethod::getName)
                    .distinct()
                    .sorted()
                    .toList();

            Map<String, Object> ifaceOut = new LinkedHashMap<>();
            ifaceOut.put("implementations", implementations);
            ifaceOut.put("callers", callers);
            ifaceOut.put("methodCount", iface.getMethods().size());
            ifaceOut.put("allMethods", allMethods);
            out.put(iface.getSimpleName(), ifaceOut);
        }
        return out;
    }

    private static boolean isSpiPackage(String packageName) {
        return packageName.endsWith(".spi");
    }

    private static Map<String, Object> classInfo(JavaClass javaClass, List<Map<String, String>> calls) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class", javaClass.getSimpleName());
        info.put("module", moduleFor(javaClass));
        info.put("file", sourceFileFor(javaClass));
        if (calls != null) info.put("calls", calls);
        return info;
    }

    private static String moduleFor(JavaClass javaClass) {
        String bestModule = "unknown";
        int bestLength = -1;
        for (Map.Entry<String, String> entry : MODULE_PACKAGES.entrySet()) {
            String prefix = entry.getValue();
            boolean matches = javaClass.getPackageName().equals(prefix) || javaClass.getPackageName().startsWith(prefix + ".");
            if (matches && prefix.length() > bestLength) {
                bestModule = entry.getKey();
                bestLength = prefix.length();
            }
        }
        return bestModule;
    }

    private static String sourceFileFor(JavaClass javaClass) {
        return moduleFor(javaClass) + "/src/main/java/" + javaClass.getPackageName().replace('.', '/')
                + "/" + javaClass.getSimpleName() + ".java";
    }
}
