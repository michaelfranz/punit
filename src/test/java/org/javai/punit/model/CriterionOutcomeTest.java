package org.javai.punit.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CriterionOutcome")
class CriterionOutcomeTest {

    @Nested
    @DisplayName("Passed")
    class PassedTests {

        @Test
        @DisplayName("should return true for passed()")
        void shouldReturnTrueForPassed() {
            CriterionOutcome outcome = new CriterionOutcome.Passed("Test criterion");

            assertThat(outcome.passed()).isTrue();
        }

        @Test
        @DisplayName("should preserve description")
        void shouldPreserveDescription() {
            CriterionOutcome outcome = new CriterionOutcome.Passed("JSON parsed");

            assertThat(outcome.description()).isEqualTo("JSON parsed");
        }
    }

    @Nested
    @DisplayName("Failed")
    class FailedTests {

        @Test
        @DisplayName("should return false for passed()")
        void shouldReturnFalseForPassed() {
            CriterionOutcome outcome = new CriterionOutcome.Failed("Test criterion", "Expected true");

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("should preserve description and reason")
        void shouldPreserveDescriptionAndReason() {
            CriterionOutcome.Failed outcome = new CriterionOutcome.Failed("Has products", "No products found");

            assertThat(outcome.description()).isEqualTo("Has products");
            assertThat(outcome.reason()).isEqualTo("No products found");
        }

        @Test
        @DisplayName("should use default reason when constructed with single argument")
        void shouldUseDefaultReason() {
            CriterionOutcome.Failed outcome = new CriterionOutcome.Failed("Test criterion");

            assertThat(outcome.reason()).isEqualTo("Criterion not satisfied");
        }
    }

    @Nested
    @DisplayName("Errored")
    class ErroredTests {

        @Test
        @DisplayName("should return false for passed()")
        void shouldReturnFalseForPassed() {
            CriterionOutcome outcome = new CriterionOutcome.Errored(
                    "Test criterion", new RuntimeException("Oops"));

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("should preserve description and cause")
        void shouldPreserveDescriptionAndCause() {
            RuntimeException cause = new RuntimeException("Parse error");
            CriterionOutcome.Errored outcome = new CriterionOutcome.Errored("JSON parsed", cause);

            assertThat(outcome.description()).isEqualTo("JSON parsed");
            assertThat(outcome.cause()).isSameAs(cause);
        }

        @Test
        @DisplayName("should format reason from exception")
        void shouldFormatReasonFromException() {
            IllegalArgumentException cause = new IllegalArgumentException("Invalid input");
            CriterionOutcome.Errored outcome = new CriterionOutcome.Errored("Validation", cause);

            assertThat(outcome.reason()).isEqualTo("IllegalArgumentException: Invalid input");
        }

        @Test
        @DisplayName("should handle null exception message")
        void shouldHandleNullExceptionMessage() {
            RuntimeException cause = new RuntimeException((String) null);
            CriterionOutcome.Errored outcome = new CriterionOutcome.Errored("Test", cause);

            assertThat(outcome.reason()).isEqualTo("RuntimeException: null");
        }
    }

    @Nested
    @DisplayName("NotEvaluated")
    class NotEvaluatedTests {

        @Test
        @DisplayName("should return false for passed()")
        void shouldReturnFalseForPassed() {
            CriterionOutcome outcome = new CriterionOutcome.NotEvaluated("Dependent criterion");

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("should preserve description")
        void shouldPreserveDescription() {
            CriterionOutcome outcome = new CriterionOutcome.NotEvaluated("Has products");

            assertThat(outcome.description()).isEqualTo("Has products");
        }
    }

    @Nested
    @DisplayName("Type hierarchy")
    class TypeHierarchyTests {

        @Test
        @DisplayName("all variants should be instances of CriterionOutcome")
        void allVariantsShouldBeInstancesOfCriterionOutcome() {
            assertThat(new CriterionOutcome.Passed("test")).isInstanceOf(CriterionOutcome.class);
            assertThat(new CriterionOutcome.Failed("test", "reason")).isInstanceOf(CriterionOutcome.class);
            assertThat(new CriterionOutcome.Errored("test", new RuntimeException())).isInstanceOf(CriterionOutcome.class);
            assertThat(new CriterionOutcome.NotEvaluated("test")).isInstanceOf(CriterionOutcome.class);
        }
    }
}

