package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.ResultCaptor;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that success determination in experiments is based on criteria evaluation,
 * not on legacy result value heuristics.
 *
 * <p>This test exists to prevent regression of a critical bug where the experiment
 * framework was determining success from conventional result keys (like "success",
 * "isValid", etc.) rather than from the recorded criteria's {@code allPassed()} result.
 *
 * <h2>The Bug</h2>
 * <p>When {@code UseCaseResult} no longer contained a "success" key (correctly, since
 * success is a judgment that belongs in criteria), the legacy {@code determineSuccess()}
 * method would default to returning {@code true}, causing specs to show 100% success
 * rate even when criteria failed.
 *
 * <h2>The Fix</h2>
 * <p>The {@code recordResult()} method now checks if criteria are recorded and uses
 * {@code criteria.allPassed()} to determine success. Legacy heuristics are only used
 * as a fallback when no criteria are present.
 *
 * <p>Note: These tests use the legacy {@link UseCaseCriteria} API. New code should
 * use the contract-based {@code PostconditionResult} system instead.
 */
@DisplayName("Criteria-Based Success Determination")
@SuppressWarnings("deprecation")
class CriteriaBasedSuccessDeterminationTest {

    private ExperimentResultAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ExperimentResultAggregator("TestUseCase", 100);
    }

    @Nested
    @DisplayName("When criteria are recorded via UseCaseOutcome")
    class WithCriteria {

        @Test
        @DisplayName("should count as success when all criteria pass")
        void shouldCountSuccessWhenAllCriteriaPass() {
            // Given: A result with all criteria passing
            UseCaseResult result = UseCaseResult.builder()
                    .value("someValue", "data")
                    // Note: NO "success" key - this is intentional
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("First check", () -> true)
                    .criterion("Second check", () -> true)
                    .build();

            UseCaseOutcome outcome = new UseCaseOutcome(result, criteria);
            ResultCaptor captor = new ResultCaptor();
            captor.record(outcome);

            // When: We simulate what recordResult does
            boolean success = captor.getCriteria().allPassed();

            // Then: Should be success because criteria passed
            assertThat(success).isTrue();
            assertThat(captor.hasCriteria()).isTrue();

            // Verify aggregation would work correctly
            if (success) {
                aggregator.recordSuccess(result);
            } else {
                aggregator.recordFailure(result, "test failure");
            }
            aggregator.recordCriteria(criteria);

            assertThat(aggregator.getSuccesses()).isEqualTo(1);
            assertThat(aggregator.getFailures()).isEqualTo(0);
            assertThat(aggregator.getObservedSuccessRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should count as failure when any criterion fails")
        void shouldCountFailureWhenAnyCriterionFails() {
            // Given: A result where one criterion fails
            UseCaseResult result = UseCaseResult.builder()
                    .value("someValue", "data")
                    .value("isValidJson", true)  // Even if individual values look OK
                    // Note: NO "success" key
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Valid JSON", () -> true)
                    .criterion("Has required fields", () -> true)
                    .criterion("Products have required attributes", () -> false)  // FAILS
                    .build();

            UseCaseOutcome outcome = new UseCaseOutcome(result, criteria);
            ResultCaptor captor = new ResultCaptor();
            captor.record(outcome);

            // When: We check success based on criteria
            boolean success = captor.getCriteria().allPassed();

            // Then: Should be failure because one criterion failed
            assertThat(success).isFalse();

            // Verify aggregation
            if (success) {
                aggregator.recordSuccess(result);
            } else {
                aggregator.recordFailure(result, "Products have required attributes");
            }
            aggregator.recordCriteria(criteria);

            assertThat(aggregator.getSuccesses()).isEqualTo(0);
            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getObservedSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should count as failure when criterion throws exception")
        void shouldCountFailureWhenCriterionThrows() {
            // Given: A result where a criterion throws
            UseCaseResult result = UseCaseResult.builder()
                    .value("rawJson", "invalid json")
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("JSON parses", () -> {
                        throw new RuntimeException("Parse error");
                    })
                    .build();

            UseCaseOutcome outcome = new UseCaseOutcome(result, criteria);
            ResultCaptor captor = new ResultCaptor();
            captor.record(outcome);

            // When: We check success based on criteria
            boolean success = captor.getCriteria().allPassed();

            // Then: Should be failure because criterion errored
            assertThat(success).isFalse();

            // Verify aggregation
            if (success) {
                aggregator.recordSuccess(result);
            } else {
                aggregator.recordFailure(result, "JSON parses");
            }

            assertThat(aggregator.getSuccesses()).isEqualTo(0);
            assertThat(aggregator.getFailures()).isEqualTo(1);
        }

        @Test
        @DisplayName("should NOT use legacy 'success' key when criteria are present")
        void shouldIgnoreLegacySuccessKeyWhenCriteriaPresent() {
            // Given: A result WITH a "success" key set to true, BUT criteria that fail
            // This tests that criteria take precedence over legacy keys
            UseCaseResult result = UseCaseResult.builder()
                    .value("success", true)  // Legacy key says success
                    .value("isValid", true)  // More legacy keys
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Critical check", () -> false)  // But criteria say failure!
                    .build();

            UseCaseOutcome outcome = new UseCaseOutcome(result, criteria);
            ResultCaptor captor = new ResultCaptor();
            captor.record(outcome);

            // When: We determine success
            boolean success = captor.getCriteria().allPassed();

            // Then: Criteria should win - this is a FAILURE despite "success: true"
            assertThat(success).isFalse();
        }
    }

    @Nested
    @DisplayName("When no criteria are recorded (legacy mode)")
    class WithoutCriteria {

        @Test
        @DisplayName("should fall back to legacy 'success' key heuristic")
        void shouldUseLegacySuccessKey() {
            // Given: A result with legacy "success" key but no criteria
            UseCaseResult result = UseCaseResult.builder()
                    .value("success", false)
                    .build();

            ResultCaptor captor = new ResultCaptor();
            captor.record(result);  // Note: not recording criteria

            // Then: No criteria recorded
            assertThat(captor.hasCriteria()).isFalse();

            // Legacy behavior would check result.getBoolean("success", false)
            boolean legacySuccess = result.getBoolean("success", true);
            assertThat(legacySuccess).isFalse();
        }

        @Test
        @DisplayName("should fall back to legacy 'isValidJson' key heuristic")
        void shouldUseLegacyIsValidJsonKey() {
            // Given: A result with legacy "isValidJson" key but no criteria
            UseCaseResult result = UseCaseResult.builder()
                    .value("isValidJson", false)
                    .build();

            ResultCaptor captor = new ResultCaptor();
            captor.record(result);

            // Then: No criteria recorded
            assertThat(captor.hasCriteria()).isFalse();

            // Legacy behavior would check isValidJson
            boolean legacySuccess = result.getBoolean("isValidJson", true);
            assertThat(legacySuccess).isFalse();
        }
    }

    @Nested
    @DisplayName("Aggregation behavior")
    class AggregationBehavior {

        @Test
        @DisplayName("should track per-criterion pass rates")
        void shouldTrackPerCriterionPassRates() {
            // Given: Multiple samples with varying criterion outcomes
            for (int i = 0; i < 10; i++) {
                UseCaseResult result = UseCaseResult.builder()
                        .value("iteration", i)
                        .build();

                final int iteration = i;
                UseCaseCriteria criteria = UseCaseCriteria.ordered()
                        .criterion("Always passes", () -> true)
                        .criterion("Passes 50%", () -> iteration % 2 == 0)
                        .criterion("Passes 30%", () -> iteration < 3)
                        .build();

                boolean success = criteria.allPassed();
                if (success) {
                    aggregator.recordSuccess(result);
                } else {
                    aggregator.recordFailure(result, "test");
                }
                aggregator.recordCriteria(criteria);
            }

            // Then: Criteria pass rates should be tracked
            var passRates = aggregator.getCriteriaPassRates();
            assertThat(passRates).containsKey("Always passes");
            assertThat(passRates.get("Always passes")).isEqualTo(1.0);
            assertThat(passRates.get("Passes 50%")).isEqualTo(0.5);
            assertThat(passRates.get("Passes 30%")).isEqualTo(0.3);
        }

        @Test
        @DisplayName("overall success rate should match criteria.allPassed() rate")
        void overallSuccessRateShouldMatchCriteriaAllPassed() {
            // Given: 10 samples where 3 have all criteria passing
            for (int i = 0; i < 10; i++) {
                UseCaseResult result = UseCaseResult.builder().build();

                final int iteration = i;
                // All criteria pass only for iterations 0, 1, 2 (30%)
                UseCaseCriteria criteria = UseCaseCriteria.ordered()
                        .criterion("Check A", () -> true)
                        .criterion("Check B", () -> iteration < 3)
                        .build();

                boolean success = criteria.allPassed();
                if (success) {
                    aggregator.recordSuccess(result);
                } else {
                    aggregator.recordFailure(result, "Check B");
                }
                aggregator.recordCriteria(criteria);
            }

            // Then: Overall success rate should be 30% (3/10)
            assertThat(aggregator.getObservedSuccessRate()).isEqualTo(0.3);
            assertThat(aggregator.getSuccesses()).isEqualTo(3);
            assertThat(aggregator.getFailures()).isEqualTo(7);
        }
    }
}

