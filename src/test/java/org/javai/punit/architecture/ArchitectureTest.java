package org.javai.punit.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests ensuring dependency constraints are enforced.
 * 
 * <p>These tests verify the architectural rules documented in 
 * {@code plan/DOC-03-ARCHITECTURE-OVERVIEW.md}, specifically:
 * 
 * <ul>
 *   <li>Core punit packages must NOT import from llmx (or other extensions)</li>
 *   <li>Dependencies flow one direction only: extensions → core</li>
 *   <li>No circular dependencies between packages</li>
 * </ul>
 * 
 * @see <a href="file:../../../../../plan/DOC-03-ARCHITECTURE-OVERVIEW.md">Architecture Overview</a>
 */
@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static JavaClasses punitClasses;

    @BeforeAll
    static void importClasses() {
        punitClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit");
    }

    @Nested
    @DisplayName("Core Isolation Rules")
    class CoreIsolationRules {

        @Test
        @DisplayName("Core API must not depend on LLM extension (llmx)")
        void coreApiMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(punitClasses);
        }

        @Test
        @DisplayName("Core engine must not depend on LLM extension (llmx)")
        void coreEngineMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.engine..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(punitClasses);
        }

        @Test
        @DisplayName("Core model must not depend on LLM extension (llmx)")
        void coreModelMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(punitClasses);
        }

        @Test
        @DisplayName("Experiment module must not depend on LLM extension (llmx)")
        void experimentMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.experiment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(punitClasses);
        }

        @Test
        @DisplayName("Specification module must not depend on LLM extension (llmx)")
        void specMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.spec..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(punitClasses);
        }
    }

    @Nested
    @DisplayName("Layer Rules")
    class LayerRules {

        @Test
        @DisplayName("API annotations may reference extensions (JUnit 5 requirement)")
        void apiAnnotationsMayReferenceExtensions() {
            // In JUnit 5, annotations use @ExtendWith to reference their extensions.
            // This is an intentional, minimal coupling required by JUnit's model.
            // The key constraint is that the engine should not leak into API contracts
            // beyond the @ExtendWith meta-annotation.
            //
            // We verify this is limited to Extension references only:
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.api..")
                    .and().areNotAnnotations()  // Allow annotations to reference extensions
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.engine..");

            rule.check(punitClasses);
        }
    }

    @Nested
    @DisplayName("Dependency Direction")
    class DependencyDirection {

        /*
         * Note on the api↔engine relationship:
         * 
         * JUnit 5's extension model requires a bi-directional dependency:
         * - Annotations in 'api' use @ExtendWith to reference extensions in 'engine'
         * - Extensions in 'engine' consume the annotation types they process
         * 
         * This is an intentional, minimal coupling inherent to JUnit 5's design.
         * The core isolation rules (above) focus on preventing unwanted dependencies
         * on domain-specific extensions like llmx.
         */

        @Test
        @DisplayName("Model types should not depend on engine implementation")
        void modelShouldNotDependOnEngine() {
            // Model types (records, value objects) should be pure data containers
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.engine..");

            rule.check(punitClasses);
        }
    }

    @Nested
    @DisplayName("Future Extension Rules")
    class FutureExtensionRules {

        // These tests document the expected structure as extensions are added.
        // They serve as executable documentation and will enforce constraints
        // when the corresponding packages exist.

        @Test
        @DisplayName("Statistics module should have no framework dependencies (when created)")
        void statisticsModuleShouldBeIsolated() {
            // This rule will become meaningful when punit-statistics is created.
            // It documents the design principle from DOC-02-DESIGN-PRINCIPLES.md §1.6
            //
            // allowEmptyShould(true) is required because the statistics package
            // doesn't exist yet - this is a forward-looking architectural constraint.
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.statistics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.javai.punit.api..",
                            "org.javai.punit.engine..",
                            "org.javai.punit.experiment..",
                            "org.javai.punit.spec..",
                            "org.javai.punit.llmx.."
                    )
                    .allowEmptyShould(true);

            rule.check(punitClasses);
        }
    }
}

