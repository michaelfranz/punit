package org.javai.punit.engine;

import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EarlyTerminationEvaluator}.
 */
class EarlyTerminationEvaluatorTest {

    @Test
    void shouldNotTerminateWhenStillPossibleToPass() {
        // 95% of 100 = 95 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        // 90 successes, 5 failures = 95 executed, 5 remaining
        // Max possible = 90 + 5 = 95 >= 95 required, so no termination
        Optional<TerminationReason> result = evaluator.shouldTerminate(90, 95);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTerminateWhenImpossibleToPass() {
        // 95% of 100 = 95 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        // 0 successes, 6 failures = 6 executed, 94 remaining
        // Max possible = 0 + 94 = 94 < 95 required, so terminate
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 6);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.IMPOSSIBILITY);
    }

    @Test
    void shouldTerminateOnFirstFailureWhenMinPassRateIsOne() {
        // 100% required = 100 successes out of 100
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 1.0);
        
        // 0 successes, 1 failure = 1 executed, 99 remaining
        // Max possible = 0 + 99 = 99 < 100 required
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 1);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.IMPOSSIBILITY);
    }

    @Test
    void shouldNeverTerminateWhenMinPassRateIsZero() {
        // 0% required = 0 successes needed
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.0);
        
        // All failures
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 100);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTerminateExactlyAtBoundary() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 5 successes, 2 failures = 7 executed, 3 remaining
        // Max possible = 5 + 3 = 8 >= 8 required, no termination yet
        assertThat(evaluator.shouldTerminate(5, 7)).isEmpty();
        
        // 5 successes, 3 failures = 8 executed, 2 remaining
        // Max possible = 5 + 2 = 7 < 8 required, terminate!
        assertThat(evaluator.shouldTerminate(5, 8)).isPresent();
    }

    @Test
    void calculateRequiredSuccessesRoundsUp() {
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(100, 0.95))
                .isEqualTo(95);
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(10, 0.95))
                .isEqualTo(10); // ceil(9.5) = 10
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(7, 0.80))
                .isEqualTo(6);  // ceil(5.6) = 6
    }

    @Test
    void getFailuresUntilImpossibilityCalculatedCorrectly() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // At start: 0 successes, 0 executed, 10 remaining
        // Max possible = 0 + 10 = 10, buffer = 10 - 8 = 2
        assertThat(evaluator.getFailuresUntilImpossibility(0, 0)).isEqualTo(2);
        
        // After 1 success: 1 success, 1 executed, 9 remaining
        // Max possible = 1 + 9 = 10, buffer = 10 - 8 = 2
        assertThat(evaluator.getFailuresUntilImpossibility(1, 1)).isEqualTo(2);
        
        // After 2 failures: 0 successes, 2 executed, 8 remaining
        // Max possible = 0 + 8 = 8, buffer = 8 - 8 = 0
        assertThat(evaluator.getFailuresUntilImpossibility(0, 2)).isEqualTo(0);
    }

    @Test
    void buildImpossibilityExplanationIncludesDetails() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        String explanation = evaluator.buildImpossibilityExplanation(0, 6);
        
        assertThat(explanation)
                .contains("6 samples")
                .contains("0 successes")
                .contains("94")  // remaining
                .contains("95"); // required
    }

    @Test
    void gettersReturnCorrectValues() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        assertThat(evaluator.getTotalSamples()).isEqualTo(100);
        assertThat(evaluator.getRequiredSuccesses()).isEqualTo(95);
    }
}

