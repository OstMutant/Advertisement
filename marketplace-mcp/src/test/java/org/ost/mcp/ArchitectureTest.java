package org.ost.mcp;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Guards that classes of this module depend on no other package of the project and on no Vaadin class. */
@AnalyzeClasses(packages = "org.ost.mcp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule mcp_must_not_depend_on_any_project_module = noClasses()
            .that().resideInAPackage("org.ost.mcp..")
            .should().dependOnClassesThat(resideInAPackage("org.ost..")
                    .and(DescribedPredicate.not(resideInAPackage("org.ost.mcp..")))
                    .or(resideInAPackage("com.vaadin..")));
}
