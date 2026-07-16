package org.ost.marketplace.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
            noClasses().should(new ArchCondition<JavaClass>("not declare Optional method parameters") {
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
}
