package org.javai.punit.model;

import org.javai.punit.util.Lazy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UseCaseCriteria")
class UseCaseCriteriaTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create empty criteria")
        void shouldCreateEmptyCriteria() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered().build();

            assertThat(criteria.entries()).isEmpty();
            assertThat(criteria.evaluate()).isEmpty();
            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should preserve insertion order")
        void shouldPreserveInsertionOrder() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> true)
                    .criterion("Third", () -> true)
                    .build();

            List<String> descriptions = criteria.entries().stream()
                    .map(e -> e.getKey())
                    .toList();

            assertThat(descriptions).containsExactly("First", "Second", "Third");
        }

        @Test
        @DisplayName("should reject null description")
        void shouldRejectNullDescription() {
            assertThatThrownBy(() -> 
                    UseCaseCriteria.ordered().criterion(null, () -> true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description");
        }

        @Test
        @DisplayName("should reject null check")
        void shouldRejectNullCheck() {
            assertThatThrownBy(() -> 
                    UseCaseCriteria.ordered().criterion("test", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("check");
        }

        @Test
        @DisplayName("should reject duplicate descriptions")
        void shouldRejectDuplicateDescriptions() {
            assertThatThrownBy(() -> 
                    UseCaseCriteria.ordered()
                            .criterion("Same name", () -> true)
                            .criterion("Same name", () -> false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Same name");
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("should return Passed for true criteria")
        void shouldReturnPassedForTrueCriteria() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Always true", () -> true)
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Passed.class);
            assertThat(outcomes.get(0).description()).isEqualTo("Always true");
        }

        @Test
        @DisplayName("should return Failed for false criteria")
        void shouldReturnFailedForFalseCriteria() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Always false", () -> false)
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Failed.class);
            assertThat(outcomes.get(0).description()).isEqualTo("Always false");
        }

        @Test
        @DisplayName("should return Errored for throwing criteria")
        void shouldReturnErroredForThrowingCriteria() {
            RuntimeException exception = new RuntimeException("Test error");
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Throws", () -> { throw exception; })
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
            CriterionOutcome.Errored errored = (CriterionOutcome.Errored) outcomes.get(0);
            assertThat(errored.cause()).isSameAs(exception);
        }

        @Test
        @DisplayName("should evaluate criteria lazily")
        void shouldEvaluateCriteriaLazily() {
            AtomicInteger evaluationCount = new AtomicInteger(0);
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Counted", () -> {
                        evaluationCount.incrementAndGet();
                        return true;
                    })
                    .build();

            // Not evaluated yet
            assertThat(evaluationCount.get()).isZero();

            // Evaluate
            criteria.evaluate();
            assertThat(evaluationCount.get()).isEqualTo(1);

            // Each call to evaluate() re-evaluates
            criteria.evaluate();
            assertThat(evaluationCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should evaluate all criteria even when some fail")
        void shouldEvaluateAllCriteriaEvenWhenSomeFail() {
            AtomicInteger evaluationCount = new AtomicInteger(0);
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First (fails)", () -> {
                        evaluationCount.incrementAndGet();
                        return false;
                    })
                    .criterion("Second (passes)", () -> {
                        evaluationCount.incrementAndGet();
                        return true;
                    })
                    .criterion("Third (fails)", () -> {
                        evaluationCount.incrementAndGet();
                        return false;
                    })
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(evaluationCount.get()).isEqualTo(3);
            assertThat(outcomes).hasSize(3);
            assertThat(outcomes.get(0).passed()).isFalse();
            assertThat(outcomes.get(1).passed()).isTrue();
            assertThat(outcomes.get(2).passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Cascading failure detection")
    class CascadingFailureTests {

        @Test
        @DisplayName("should mark subsequent criteria as NotEvaluated for same exception")
        void shouldMarkSubsequentCriteriaAsNotEvaluated() {
            RuntimeException sharedError = new RuntimeException("Parse failed");
            Lazy<String> lazyValue = Lazy.of(() -> { throw sharedError; });

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> lazyValue.get() != null)
                    .criterion("Second", () -> lazyValue.get().length() > 0)
                    .criterion("Third", () -> lazyValue.get().contains("test"))
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(3);
            
            // First should be Errored
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
            
            // Second and Third should be NotEvaluated (same exception)
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.NotEvaluated.class);
            assertThat(outcomes.get(2)).isInstanceOf(CriterionOutcome.NotEvaluated.class);
        }

        @Test
        @DisplayName("should record different exceptions separately")
        void shouldRecordDifferentExceptionsSeparately() {
            RuntimeException error1 = new RuntimeException("Error 1");
            RuntimeException error2 = new RuntimeException("Error 2");

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> { throw error1; })
                    .criterion("Second", () -> { throw error2; })
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(2);
            
            // Both should be Errored (different exceptions)
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.Errored.class);
        }

        @Test
        @DisplayName("should handle mixed pass/fail/error outcomes")
        void shouldHandleMixedOutcomes() {
            RuntimeException sharedError = new RuntimeException("Shared error");
            Lazy<String> lazyValue = Lazy.of(() -> { throw sharedError; });

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Passes", () -> true)
                    .criterion("Uses lazy", () -> lazyValue.get() != null)
                    .criterion("Also uses lazy", () -> lazyValue.get().isEmpty())
                    .criterion("Fails", () -> false)
                    .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(4);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Passed.class);
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.Errored.class);
            assertThat(outcomes.get(2)).isInstanceOf(CriterionOutcome.NotEvaluated.class);
            assertThat(outcomes.get(3)).isInstanceOf(CriterionOutcome.Failed.class);
        }
    }

    @Nested
    @DisplayName("allPassed()")
    class AllPassedTests {

        @Test
        @DisplayName("should return true when all criteria pass")
        void shouldReturnTrueWhenAllPass() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> true)
                    .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should return false when any criterion fails")
        void shouldReturnFalseWhenAnyFails() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> false)
                    .criterion("Third", () -> true)
                    .build();

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when any criterion errors")
        void shouldReturnFalseWhenAnyErrors() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> { throw new RuntimeException(); })
                    .build();

            assertThat(criteria.allPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("assertAll()")
    class AssertAllTests {

        @Test
        @DisplayName("should not throw when all criteria pass")
        void shouldNotThrowWhenAllPass() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> true)
                    .build();

            // Should not throw
            criteria.assertAll();
        }

        @Test
        @DisplayName("should throw AssertionError when criterion fails")
        void shouldThrowWhenCriterionFails() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First", () -> true)
                    .criterion("Second", () -> false)
                    .build();

            assertThatThrownBy(criteria::assertAll)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Second");
        }

        @Test
        @DisplayName("should throw AssertionError when criterion errors")
        void shouldThrowWhenCriterionErrors() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Throws", () -> { throw new IllegalStateException("boom"); })
                    .build();

            assertThatThrownBy(criteria::assertAll)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Throws")
                    .hasMessageContaining("IllegalStateException");
        }
    }

    @Nested
    @DisplayName("constructionFailed()")
    class ConstructionFailedTests {

        @Test
        @DisplayName("should create criteria with single errored outcome")
        void shouldCreateCriteriaWithErroredOutcome() {
            RuntimeException cause = new RuntimeException("Construction failed");
            UseCaseCriteria criteria = UseCaseCriteria.constructionFailed(cause);

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
            assertThat(outcomes.get(0).description()).isEqualTo("Criteria construction");
        }

        @Test
        @DisplayName("should return false for allPassed()")
        void shouldReturnFalseForAllPassed() {
            UseCaseCriteria criteria = UseCaseCriteria.constructionFailed(
                    new RuntimeException());

            assertThat(criteria.allPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("defaultCriteria()")
    class DefaultCriteriaTests {

        @Test
        @DisplayName("should return empty criteria (trivial postcondition)")
        void shouldReturnEmptyCriteria() {
            UseCaseCriteria criteria = UseCaseCriteria.defaultCriteria();

            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).isEmpty();
        }

        @Test
        @DisplayName("should trivially pass (DbC lightest postcondition)")
        void shouldTriviallyPass() {
            UseCaseCriteria criteria = UseCaseCriteria.defaultCriteria();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("assertAll should not throw for trivial criteria")
        void assertAllShouldNotThrow() {
            UseCaseCriteria criteria = UseCaseCriteria.defaultCriteria();

            // Should not throw - trivial postcondition always satisfied
            criteria.assertAll();
        }
    }

    @Nested
    @DisplayName("formatOutcomeMessage()")
    class FormatOutcomeMessageTests {

        @Test
        @DisplayName("should format Passed outcome")
        void shouldFormatPassedOutcome() {
            CriterionOutcome outcome = new CriterionOutcome.Passed("Test");

            String message = UseCaseCriteria.formatOutcomeMessage(outcome);

            assertThat(message).isEqualTo("Test: passed");
        }

        @Test
        @DisplayName("should format Failed outcome with reason")
        void shouldFormatFailedOutcome() {
            CriterionOutcome outcome = new CriterionOutcome.Failed("Test", "Expected true");

            String message = UseCaseCriteria.formatOutcomeMessage(outcome);

            assertThat(message).isEqualTo("Test: Expected true");
        }

        @Test
        @DisplayName("should format Errored outcome with exception info")
        void shouldFormatErroredOutcome() {
            CriterionOutcome outcome = new CriterionOutcome.Errored(
                    "Test", new IllegalArgumentException("bad input"));

            String message = UseCaseCriteria.formatOutcomeMessage(outcome);

            assertThat(message).isEqualTo("Test: IllegalArgumentException: bad input");
        }

        @Test
        @DisplayName("should format NotEvaluated outcome")
        void shouldFormatNotEvaluatedOutcome() {
            CriterionOutcome outcome = new CriterionOutcome.NotEvaluated("Test");

            String message = UseCaseCriteria.formatOutcomeMessage(outcome);

            assertThat(message).isEqualTo("Test (not evaluated)");
        }
    }
}

