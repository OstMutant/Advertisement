package org.ost.marketplace.architecture;

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
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Codifies the cross-module architecture rules from the root {@code CLAUDE.md}/{@code rules.md}
 * as build-breaking tests instead of prose enforced by review alone — see improvement-030 and
 * {@code marketplace-app/DECISIONS.md} for the rationale (two real violations, improvement-011 and
 * improvement-010, previously reached working code unnoticed under the prose-only approach).
 * Scans the whole {@code org.ost} tree, which this module's test classpath can already see in
 * full (marketplace-app depends on every starter + platform-commons + query-lib).
 */
@AnalyzeClasses(packages = "org.ost", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final List<String> STARTER_PACKAGES = List.of(
            "org.ost.audit", "org.ost.attachment", "org.ost.user",
            "org.ost.advertisement", "org.ost.taxon", "org.ost.provider");

    @ArchTest
    static final ArchRule ui_must_not_call_repositories_directly =
            noClasses().that().resideInAPackage("org.ost.marketplace.ui..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("the UI layer must go through a *Port, never a Repository directly — "
                            + "see .claude/rules.md \"Strict Boundaries\"");

    @ArchTest
    static final ArchRule starters_must_not_depend_on_vaadin =
            noClasses().that().resideInAnyPackage(
                            "org.ost.audit..", "org.ost.attachment..", "org.ost.user..",
                            "org.ost.advertisement..", "org.ost.taxon..")
                    .should().dependOnClassesThat().resideInAPackage("com.vaadin..")
                    .because("starters have no Vaadin dependency — UI code lives only in "
                            + "marketplace-app, see each starter's own CLAUDE.md \"Key constraints\"");

    @ArchTest
    static final ArchRule ports_live_only_in_platform_commons =
            classes().that().areInterfaces().and().haveSimpleNameEndingWith("Port")
                    .should().resideInAPackage("org.ost.platform..")
                    .because("*Port SPI interfaces live in platform-commons so marketplace always "
                            + "sees the type regardless of which starters are present — see "
                            + "platform-commons/CLAUDE.md \"Why ports and hooks must live in "
                            + "platform-commons\"");

    @ArchTest
    static final ArchRule hooks_live_only_in_platform_commons =
            classes().that().areInterfaces().and().haveSimpleNameEndingWith("Hook")
                    .should().resideInAPackage("org.ost.platform..")
                    .because("*Hook SPI interfaces live in platform-commons so marketplace always "
                            + "sees the type regardless of which starters are present — see "
                            + "platform-commons/CLAUDE.md \"Why ports and hooks must live in "
                            + "platform-commons\"");

    @ArchTest
    static final ArchRule no_class_level_preauthorize_on_services =
            noClasses().that().haveSimpleNameEndingWith("Service")
                    .should().beAnnotatedWith(PreAuthorize.class)
                    .because("Vaadin initializes view beans before authentication; a class-level "
                            + "@PreAuthorize on a service causes AuthorizationDeniedException during "
                            + "view wiring — see marketplace-app/CLAUDE.md \"Security\"");

    @ArchTest
    static final ArchRule no_optional_method_parameters =
            noClasses().should(new ArchCondition<>("not declare Optional method parameters") {
                @Override
                public void check(JavaClass javaClass, com.tngtech.archunit.lang.ConditionEvents events) {
                    for (JavaMethod method : javaClass.getMethods()) {
                        boolean hasOptionalParam = method.getRawParameterTypes().stream()
                                .anyMatch(p -> p.isEquivalentTo(Optional.class));
                        if (hasOptionalParam) {
                            events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " declares an Optional method parameter — "
                                            + "callers must resolve it before calling, see root "
                                            + "CLAUDE.md \"No Optional parameters\""));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule config_packages_not_configuration =
            noClasses().should().resideInAPackage("..configuration..")
                    .because("all modules use `config`, not `configuration`, for Spring "
                            + "configuration packages — see root CLAUDE.md \"Package structure\"");

    @ArchTest
    static final ArchRule port_and_hook_impl_classes_are_pure_delegation =
            noClasses().that().haveSimpleNameEndingWith("PortImpl")
                    .or().haveSimpleNameEndingWith("HookImpl")
                    .should().dependOnClassesThat().resideInAPackage("java.util.stream..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                    .because("*PortImpl/*HookImpl classes are pure delegation, no business logic — "
                            + "see platform-commons/CLAUDE.md \"Hook and Port Implementation Rules\". "
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
            noClasses().that().resideInAnyPackage("org.ost.marketplace..", "org.ost.orchestrator..")
                    .should().dependOnClassesThat(new DescribedPredicate<JavaClass>(
                            "reside in a starter's util/services/repository package") {
                        @Override
                        public boolean test(JavaClass input) {
                            String pkg = input.getPackageName();
                            return STARTER_PACKAGES.stream().anyMatch(p -> pkg.matches(
                                    Pattern.quote(p) + "\\.(util|services|repository)(\\..*)?"));
                        }
                    })
                    .because("marketplace/orchestrator may import from starters only via "
                            + "platform-commons contracts (Ports/Hooks/DTOs), never via internal "
                            + "impl classes — see .claude/rules.md \"Module Import Rules\"");

    // Counts only ComponentFactory<XPort>-wrapped (optional cross-domain composition) fields --
    // a direct, mandatory *Port field (e.g. UserAccountPort in a user-owned use case) is not the
    // fan-out shape this rule guards against, see marketplace-orchestrator/CLAUDE.md.
    @ArchTest
    static final ArchRule orchestrator_classes_depend_on_at_most_two_domain_ports =
            noClasses().that().resideInAPackage("org.ost.orchestrator..")
                    .should(new ArchCondition<JavaClass>("depend on at most two domain *Port interfaces via ComponentFactory") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            java.util.Set<String> portTypes = new java.util.HashSet<>();
                            for (java.lang.reflect.Field field : javaClass.reflect().getDeclaredFields()) {
                                if (field.getType().getSimpleName().equals("ComponentFactory")
                                        && field.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
                                    for (java.lang.reflect.Type arg : pt.getActualTypeArguments()) {
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
                                                + "instead, see marketplace-orchestrator/CLAUDE.md"));
                            }
                        }
                    });

    @ArchTest
    static final ArchRule orchestrator_has_no_persistence_access =
            noClasses().that().resideInAPackage("org.ost.orchestrator..")
                    .should().dependOnClassesThat().haveNameMatching(".*\\.JdbcClient")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("CrudRepository")
                    .because("marketplace-orchestrator composes results from domain Ports only, no "
                            + "direct persistence access — mirrors the pure-delegation discipline "
                            + "port_and_hook_impl_classes_are_pure_delegation enforces one layer down, "
                            + "see marketplace-orchestrator/CLAUDE.md");

    @ArchTest
    static final ArchRule util_classes_are_non_instantiable =
            classes().that().haveSimpleNameEndingWith("Util")
                    .should().haveOnlyPrivateConstructors()
                    .because("*Util classes are static-only utilities — "
                            + "see marketplace-app/CLAUDE.md \"Class suffixes\"");

    @ArchTest
    static final ArchRule config_classes_are_spring_configuration =
            classes().that().haveSimpleNameEndingWith("Config")
                    .and().areTopLevelClasses()
                    .should().beAnnotatedWith(Configuration.class)
                    .because("*Config classes are Spring @Configuration classes — "
                            + "see marketplace-app/CLAUDE.md \"Class suffixes\". Nested classes named "
                            + "*Config (e.g. a Parameters-style record) are a different, unrelated "
                            + "naming collision and are excluded.");

    @ArchTest
    static final ArchRule message_source_only_used_in_i18n_service_impl =
            noClasses().that().doNotHaveSimpleName("I18nServiceImpl")
                    .and().doNotHaveSimpleName("I18nConfig")
                    .should().dependOnClassesThat().areAssignableTo(MessageSource.class)
                    .because("never use raw MessageSource directly — use I18nService.get(I18nKey) "
                            + "instead, see marketplace-app/CLAUDE.md \"I18n\". I18nConfig is the "
                            + "Spring @Bean factory method that constructs the MessageSource bean "
                            + "itself and is excluded.");

    @ArchTest
    static final ArchRule packages_are_free_of_cycles =
            slices().matching("org.ost.(*)..").should().beFreeOfCycles();
}
