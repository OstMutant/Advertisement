package org.ost.marketplace.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Codifies the cross-module architecture rules from the root {@code CLAUDE.md}/{@code rules.md}
 * as build-breaking tests instead of prose enforced by review alone — see
 * {@code marketplace-app/DECISIONS.md} for the rationale (two real violations previously reached
 * working code unnoticed under the prose-only approach).
 * Scans the whole {@code org.ost} tree, which this module's test classpath can already see in
 * full (every starter reaches marketplace-app transitively via marketplace-orchestrator, and
 * platform-commons is a direct dependency).
 */
@AnalyzeClasses(packages = "org.ost", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final List<String> STARTER_PACKAGES = discoverStarterPackages();

    // Finds each starter's base package from its src/main/java/org/ost/ subdirectory; it cannot be derived from the module name (provider-profile's is org.ost.provider).
    private static List<String> discoverStarterPackages() {
        // A module's test working directory is the module itself, so ".." is the reactor root.
        Path reactorRoot = Paths.get("..").toAbsolutePath().normalize();
        try (Stream<Path> modules = Files.list(reactorRoot)) {
            return modules
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith("-spring-boot-starter"))
                    .map(p -> p.resolve("src/main/java/org/ost"))
                    .filter(Files::isDirectory)
                    .flatMap(ArchitectureRulesTest::listSubpackages)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> listSubpackages(Path orgOstDir) {
        try (Stream<Path> subs = Files.list(orgOstDir)) {
            return subs.filter(Files::isDirectory)
                    .map(s -> "org.ost." + s.getFileName())
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Fails when no starter package is found, so the rules over STARTER_PACKAGES cannot pass on an empty list.
    @Test
    void starterPackagesDiscoveryFoundAtLeastOneStarter() {
        assertFalse(STARTER_PACKAGES.isEmpty(),
                "STARTER_PACKAGES discovery found zero starter modules -- either the reactor-root "
                        + "path assumption broke or every *-spring-boot-starter module is somehow "
                        + "missing its own org.ost.<pkg> subdirectory; several @ArchTest rules "
                        + "silently stop checking anything on an empty list");
    }

    @ArchTest
    static final ArchRule ui_must_not_call_repositories_directly =
            noClasses().that().resideInAPackage("org.ost.marketplace.ui..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("the UI layer must go through a *Port, never a Repository directly — "
                            + "see root CLAUDE.md \"Strict Boundaries\"");

    @ArchTest
    static final ArchRule starters_must_not_depend_on_vaadin =
            noClasses().that().resideInAnyPackage(
                            "org.ost.audit..", "org.ost.attachment..", "org.ost.user..",
                            "org.ost.advertisement..", "org.ost.taxon..", "org.ost.apikey..")
                    .should().dependOnClassesThat().resideInAPackage("com.vaadin..")
                    .because("starters have no Vaadin dependency — UI code lives only in "
                            + "marketplace-app, see each starter's rules file under .claude/rules/, \"Key constraints\"");

    @ArchTest
    static final ArchRule ports_live_only_in_platform_commons =
            classes().that().areInterfaces().and().haveSimpleNameEndingWith("Port")
                    .should().resideInAPackage("org.ost.platform..")
                    .because("*Port SPI interfaces live in platform-commons so marketplace always "
                            + "sees the type regardless of which starters are present — see "
                            + ".claude/rules/platform-commons.md \"Why ports and hooks must live in "
                            + "platform-commons\"");

    // The forwarder SPIs live in org.ost.orchestrator.spi, not platform-commons: see .claude/rules/marketplace-orchestrator.md "Forwarder SPI pattern".
    private static final List<String> ORCHESTRATOR_HOOK_ALLOWLIST = List.of(
            "UiLabelHook", "SessionActorHook", "UiLocaleHook", "SettingsChangeHook", "CurrentUserHook");

    @ArchTest
    static final ArchRule hooks_live_only_in_platform_commons =
            classes().that().areInterfaces().and().haveSimpleNameEndingWith("Hook")
                    .and(new DescribedPredicate<JavaClass>("are not the orchestrator-only Hook exceptions") {
                        @Override
                        public boolean test(JavaClass input) {
                            return !ORCHESTRATOR_HOOK_ALLOWLIST.contains(input.getSimpleName());
                        }
                    })
                    .should().resideInAPackage("org.ost.platform..")
                    .because("*Hook SPI interfaces live in platform-commons so marketplace always "
                            + "sees the type regardless of which starters are present — see "
                            + ".claude/rules/platform-commons.md \"Why ports and hooks must live in "
                            + "platform-commons\". UiLabelHook/SessionActorHook/UiLocaleHook/"
                            + "SettingsChangeHook/CurrentUserHook are allow-listed above: called only by "
                            + "the mandatory marketplace-orchestrator dependency, never a starter, so they live in "
                            + "org.ost.orchestrator.spi.");

    // AuthenticatedPrincipal is the one allowed exception: see .claude/rules/marketplace-orchestrator.md "Forwarder SPI pattern".
    private static final List<String> PLATFORM_SPI_ALLOWLIST = List.of("AuthenticatedPrincipal");

    @ArchTest
    static final ArchRule marketplace_app_must_not_depend_on_platform_commons_spi_directly =
            noClasses().that().resideInAnyPackage("org.ost.marketplace..", "org.ost.restapi..")
                    .should().dependOnClassesThat(new DescribedPredicate<JavaClass>(
                            "reside in a platform-commons *.spi package and are not allow-listed") {
                        @Override
                        public boolean test(JavaClass input) {
                            String pkg = input.getPackageName();
                            return pkg.startsWith("org.ost.platform.") && pkg.endsWith(".spi")
                                    && !PLATFORM_SPI_ALLOWLIST.contains(input.getSimpleName());
                        }
                    })
                    .because("marketplace-app and marketplace-rest-api should have zero direct "
                            + "*Port/*Hook usage from platform-commons — cross-domain composition "
                            + "routes through marketplace-orchestrator instead, see .claude/rules/marketplace-orchestrator.md \"Key constraints\". "
                            + "AuthenticatedPrincipal is allow-listed above.");

    @ArchTest
    static final ArchRule no_class_level_preauthorize_on_services =
            noClasses().that().haveSimpleNameEndingWith("Service")
                    .should().beAnnotatedWith(PreAuthorize.class)
                    .because("Vaadin initializes view beans before authentication; a class-level "
                            + "@PreAuthorize on a service causes AuthorizationDeniedException during "
                            + "view wiring — see .claude/rules/marketplace-app.md \"Security: @PreAuthorize and Vaadin\"");

    @ArchTest
    static final ArchRule no_optional_method_parameters =
            noClasses().should(new ArchCondition<>("not declare Optional method parameters") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (JavaMethod method : javaClass.getMethods()) {
                        boolean hasOptionalParam = method.getRawParameterTypes().stream()
                                .anyMatch(p -> p.isEquivalentTo(Optional.class));
                        if (hasOptionalParam) {
                            events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " declares an Optional method parameter — "
                                            + "callers must resolve it before calling, see root "
                                            + "CLAUDE.md \"No `Optional` parameters\""));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule config_packages_not_configuration =
            noClasses().should().resideInAPackage("..configuration..")
                    .because("all modules use `config`, not `configuration`, for Spring "
                            + "configuration packages — see .claude/rules/marketplace-app.md \"Package structure\"");

    @ArchTest
    static final ArchRule port_and_hook_impl_classes_are_pure_delegation =
            noClasses().that().haveSimpleNameEndingWith("PortImpl")
                    .or().haveSimpleNameEndingWith("HookImpl")
                    .should().dependOnClassesThat().resideInAPackage("java.util.stream..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                    .because("*PortImpl/*HookImpl classes are pure delegation, no business logic — "
                            + "see .claude/rules/platform-commons.md \"Hook and Port Implementation Rules\". "
                            + "Coordination-layer implementations use the Default*Port naming instead "
                            + "(e.g. DefaultTaxonPort), which this rule intentionally does not match.");

    @ArchTest
    static final ArchRule starters_must_not_import_sibling_starters =
            noClasses().should(new ArchCondition<JavaClass>("not depend on a sibling starter's package") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    String ownStarter = STARTER_PACKAGES.stream()
                            .filter(p -> javaClass.getPackageName().equals(p) || javaClass.getPackageName().startsWith(p + "."))
                            .findFirst().orElse(null);
                    if (ownStarter == null) {
                        return;
                    }
                    javaClass.getDirectDependenciesFromSelf().forEach(dep -> {
                        String targetPackage = dep.getTargetClass().getPackageName();
                        STARTER_PACKAGES.stream()
                                .filter(p -> !p.equals(ownStarter) && (targetPackage.equals(p) || targetPackage.startsWith(p + ".")))
                                .findFirst()
                                .ifPresent(sibling -> events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getFullName() + " depends on "
                                                + dep.getTargetClass().getFullName()
                                                + " — starters must not import from each other, see "
                                                + ".claude/rules.md \"Module Import Rules\"")));
                    });
                }
            });

    @ArchTest
    static final ArchRule marketplace_must_not_import_starter_internals =
            noClasses().that().resideInAnyPackage(
                            "org.ost.marketplace..", "org.ost.orchestrator..", "org.ost.restapi..")
                    .should().dependOnClassesThat(new DescribedPredicate<JavaClass>(
                            "reside in a starter's util/services/repository package") {
                        @Override
                        public boolean test(JavaClass input) {
                            String pkg = input.getPackageName();
                            return STARTER_PACKAGES.stream().anyMatch(p -> pkg.matches(
                                    Pattern.quote(p) + "\\.(util|services|repository)(\\..*)?"));
                        }
                    })
                    .because("marketplace/orchestrator/marketplace-rest-api may import from starters "
                            + "only via platform-commons contracts (Ports/Hooks/DTOs), never via "
                            + "internal impl classes — see .claude/rules.md \"Module Import Rules\"");

    private static final Set<String> HTTP_MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.RequestMapping");

    private static final DescribedPredicate<JavaMethod> HTTP_MAPPED = new DescribedPredicate<JavaMethod>("are mapped to an HTTP request") {
        @Override
        public boolean test(JavaMethod method) {
            return method.getAnnotations().stream()
                    .anyMatch(a -> HTTP_MAPPING_ANNOTATIONS.contains(a.getRawType().getName()));
        }
    };

    // Annotation types are named as strings so this test needs no compile-time swagger dependency.
    @ArchTest
    static final ArchRule rest_api_operations_must_declare_an_explicit_operation_id =
            methods().that().areDeclaredInClassesThat().resideInAPackage("org.ost.restapi..")
                    .and(HTTP_MAPPED)
                    .should(new ArchCondition<JavaMethod>("carry @Operation with a non-blank operationId") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            boolean explicit = method.tryGetAnnotationOfType("io.swagger.v3.oas.annotations.Operation")
                                    .flatMap(a -> a.tryGetExplicitlyDeclaredProperty("operationId"))
                                    .map(value -> !String.valueOf(value).isBlank())
                                    .orElse(false);
                            if (!explicit) {
                                events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                        + " has no explicit operationId in @Operation"));
                            }
                        }
                    })
                    .because("springdoc derives an operationId from the Java method name and renames clashes with "
                            + "a numeric suffix that shifts as controllers are added, breaking collections, "
                            + "generated clients and MCP tool names built on it");

    @ArchTest
    static final ArchRule rest_api_operation_ids_must_be_unique =
            methods().that().areDeclaredInClassesThat().resideInAPackage("org.ost.restapi..")
                    .and(HTTP_MAPPED)
                    .should(new ArchCondition<JavaMethod>("have an operationId that no other REST method uses") {
                        private final Map<String, JavaMethod> seen = new HashMap<>();

                        @Override
                        public void init(Collection<JavaMethod> allObjectsToTest) {
                            seen.clear();
                        }

                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            method.tryGetAnnotationOfType("io.swagger.v3.oas.annotations.Operation")
                                    .flatMap(a -> a.tryGetExplicitlyDeclaredProperty("operationId"))
                                    .map(String::valueOf)
                                    .filter(id -> !id.isBlank())
                                    .ifPresent(id -> {
                                        JavaMethod first = seen.putIfAbsent(id, method);
                                        if (first != null) {
                                            events.add(SimpleConditionEvent.violated(method, "operationId '" + id
                                                    + "' is used by both " + first.getFullName() + " and " + method.getFullName()));
                                        }
                                    });
                        }
                    })
                    .because("springdoc never reports a duplicate operationId, it silently renames the second one "
                            + "with a numeric suffix");

    @ArchTest
    static final ArchRule starters_must_route_taxon_assignment_writes_through_orchestrator =
            noClasses().should(new ArchCondition<JavaClass>("not call TaxonPort.replaceAssignments() directly") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean allowedCaller = javaClass.getPackageName().startsWith("org.ost.orchestrator")
                            || javaClass.getPackageName().startsWith("org.ost.marketplace")
                            || javaClass.getPackageName().startsWith("org.ost.restapi")
                            || javaClass.getPackageName().startsWith("org.ost.taxon");
                    if (allowedCaller) {
                        return;
                    }
                    javaClass.getMethodCallsFromSelf().forEach(call -> {
                        if (call.getTarget().getOwner().isAssignableTo(TaxonPort.class)
                                && call.getTarget().getName().equals("replaceAssignments")) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    javaClass.getFullName() + " calls TaxonPort.replaceAssignments() directly — "
                                            + "category/city assignment writes must go through "
                                            + "marketplace-orchestrator's TaxonAssignmentWriteService, "
                                            + "see root CLAUDE.md guideline 2 \"Three layers, not two\""));
                        }
                    });
                }
            });

    // Only ComponentFactory<XPort> fields count, not direct mandatory ports; see .claude/rules/marketplace-orchestrator.md "Key constraints", which also names the allow-listed class.
    private static final List<String> ORCHESTRATOR_PORT_COUNT_ALLOWLIST = List.of(
            "org.ost.orchestrator.services.EntityExistenceService");

    @ArchTest
    static final ArchRule orchestrator_classes_depend_on_at_most_two_domain_ports =
            noClasses().that().resideInAPackage("org.ost.orchestrator..")
                    .should(new ArchCondition<JavaClass>("depend on at most two domain *Port interfaces via ComponentFactory") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            if (ORCHESTRATOR_PORT_COUNT_ALLOWLIST.contains(javaClass.getFullName())) {
                                return;
                            }
                            Set<String> portTypes = new HashSet<>();
                            for (Field field : javaClass.reflect().getDeclaredFields()) {
                                if (field.getType().getSimpleName().equals("ComponentFactory")
                                        && field.getGenericType() instanceof ParameterizedType pt) {
                                    for (Type arg : pt.getActualTypeArguments()) {
                                        if (arg instanceof Class<?> c && c.getSimpleName().endsWith("Port")) {
                                            portTypes.add(c.getName());
                                        }
                                    }
                                }
                            }
                            if (portTypes.size() > 2) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getFullName() + " depends on " + portTypes.size()
                                                + " domain *Port types via ComponentFactory " + portTypes
                                                + " — split into smaller, composed use-case services "
                                                + "instead, see .claude/rules/marketplace-orchestrator.md \"Key constraints\""));
                            }
                        }
                    });

    // The orchestrator and every starter, each of which must stay startable when another starter is absent.
    private static final String[] FACTORY_CONSUMER_PACKAGES = Stream
            .concat(Stream.of("org.ost.orchestrator"), STARTER_PACKAGES.stream())
            .map(p -> p + "..").toArray(String[]::new);

    @ArchTest
    static final ArchRule component_factory_consumers_declare_their_own_factory =
            classes().that().resideInAnyPackage(FACTORY_CONSUMER_PACKAGES)
                    .should(new ArchCondition<JavaClass>("declare a @Bean ComponentFactory in their own module for every ComponentFactory type they hold") {
                        private final Map<String, Set<String>> declaredByModule = new HashMap<>();

                        @Override
                        public void init(Collection<JavaClass> allObjectsToTest) {
                            for (JavaClass javaClass : allObjectsToTest) {
                                for (Method method : javaClass.reflect().getDeclaredMethods()) {
                                    if (method.isAnnotationPresent(Bean.class)) {
                                        factoryTypeOf(method.getGenericReturnType()).ifPresent(type ->
                                                declaredByModule.computeIfAbsent(moduleOf(javaClass), k -> new HashSet<>()).add(type));
                                    }
                                }
                            }
                        }

                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            for (Field field : javaClass.reflect().getDeclaredFields()) {
                                factoryTypeOf(field.getGenericType()).ifPresent(type -> {
                                    if (!declaredByModule.getOrDefault(moduleOf(javaClass), Set.of()).contains(type)) {
                                        events.add(SimpleConditionEvent.violated(javaClass,
                                                javaClass.getFullName() + " holds ComponentFactory<" + type + "> but "
                                                        + moduleOf(javaClass) + " declares no @Bean for it"));
                                    }
                                });
                            }
                        }
                    })
                    .because("a ComponentFactory bean declared only by the starter that owns the port is missing "
                            + "when that starter is removed, and the holder then fails to start");

    private static Optional<String> factoryTypeOf(Type type) {
        if (type instanceof ParameterizedType parameterized
                && ComponentFactory.class.equals(parameterized.getRawType())
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> argument) {
            return Optional.of(argument.getName());
        }
        return Optional.empty();
    }

    private static String moduleOf(JavaClass javaClass) {
        return Stream.concat(Stream.of("org.ost.orchestrator"), STARTER_PACKAGES.stream())
                .filter(p -> javaClass.getPackageName().equals(p) || javaClass.getPackageName().startsWith(p + "."))
                .findFirst().orElseThrow();
    }

    @ArchTest
    static final ArchRule orchestrator_has_no_persistence_access =
            noClasses().that().resideInAPackage("org.ost.orchestrator..")
                    .should().dependOnClassesThat().haveNameMatching(".*\\.JdbcClient")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("CrudRepository")
                    .because("marketplace-orchestrator composes results from domain Ports only, no "
                            + "direct persistence access — mirrors the pure-delegation discipline "
                            + "port_and_hook_impl_classes_are_pure_delegation enforces one layer down, "
                            + "see .claude/rules/marketplace-orchestrator.md \"Key constraints\"");

    @ArchTest
    static final ArchRule util_classes_are_non_instantiable =
            classes().that().haveSimpleNameEndingWith("Util")
                    .should().haveOnlyPrivateConstructors()
                    .because("*Util classes are static-only utilities — "
                            + "see .claude/rules/marketplace-app.md \"Class suffixes\"");

    @ArchTest
    static final ArchRule config_classes_are_spring_configuration =
            classes().that().haveSimpleNameEndingWith("Config")
                    .and().areTopLevelClasses()
                    .should().beAnnotatedWith(Configuration.class)
                    .because("*Config classes are Spring @Configuration classes — "
                            + "see .claude/rules/marketplace-app.md \"Class suffixes\". Nested classes named "
                            + "*Config (e.g. a Parameters-style record) are a different, unrelated "
                            + "naming collision and are excluded.");

    @ArchTest
    static final ArchRule message_source_only_used_in_i18n_service_impl =
            noClasses().that().doNotHaveSimpleName("I18nServiceImpl")
                    .and().doNotHaveSimpleName("I18nConfig")
                    .should().dependOnClassesThat().areAssignableTo(MessageSource.class)
                    .because("never use raw MessageSource directly — use I18nService.get(I18nKey) "
                            + "instead, see .claude/rules/marketplace-app.md \"I18n\". I18nConfig is the "
                            + "@Configuration class whose messageSource() @Bean method constructs the "
                            + "MessageSource itself, and is excluded.");

    @ArchTest
    static final ArchRule packages_are_free_of_cycles =
            slices().matching("org.ost.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule no_jpa_or_hibernate_dependencies =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..", "org.hibernate..")
                    .because("this project uses pure SQL via JdbcClient/NamedParameterJdbcTemplate — "
                            + "no JPA, no Hibernate, see root CLAUDE.md \"Core Stack\"");

    @ArchTest
    static final ArchRule no_primary_object_mapper_beans =
            methods().that().areAnnotatedWith(Bean.class)
                    .and().haveRawReturnType(ObjectMapper.class)
                    .should().notBeAnnotatedWith(Primary.class)
                    .because("when multiple ObjectMapper beans coexist, every injection site must use "
                            + "an explicit @Qualifier — never @Primary to resolve the ambiguity "
                            + "implicitly, see root CLAUDE.md \"No `@Primary` on `ObjectMapper` beans\"");

    @ArchTest
    static final ArchRule shared_hooks_do_not_depend_on_the_ui_locale_provider =
            noClasses().that().resideInAnyPackage("org.ost.marketplace.spi..", "org.ost.marketplace.services..")
                    .and().areNotAssignableTo(LocaleProvider.class)
                    .should().dependOnClassesThat().areAssignableTo(LocaleProvider.class)
                    .because("shared hooks and services are called from the REST API too, where the UI-scoped provider cannot be resolved; "
                            + "they use the orchestrator's CurrentLocaleService");

    @ArchTest
    static final ArchRule services_and_hooks_do_not_depend_on_vaadin =
            noClasses().that().resideInAnyPackage("org.ost.marketplace.services..", "org.ost.marketplace.spi..")
                    .should().dependOnClassesThat().resideInAPackage("com.vaadin..")
                    .because("services and shared hooks are called from the REST API too; Vaadin code lives in ui..");
}
