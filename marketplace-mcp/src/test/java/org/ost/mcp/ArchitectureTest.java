package org.ost.mcp;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Guards that the MCP module reaches the application only over HTTP, never through another project module. */
@AnalyzeClasses(packages = "org.ost.mcp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule mcp_must_not_depend_on_any_project_module = noClasses()
            .that().resideInAPackage("org.ost.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.ost.marketplace..",
                    "org.ost.orchestrator..",
                    "org.ost.restapi..",
                    "org.ost.platform..",
                    "com.vaadin..");
}
