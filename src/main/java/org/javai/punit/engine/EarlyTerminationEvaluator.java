package org.javai.punit.engine;

import org.javai.punit.model.TerminationReason;

import java.util.Optional;

/**
 * Evaluates whether a probabilistic test should terminate early.
 *
 * <p>Early termination occurs when it becomes mathematically impossible
 * to reach the required minimum pass rate. This is a deterministic,
 * lossless optimization that accelerates failure detection without
 * changing the outcome.
 *
 * <h2>Impossibility Detection</h2>
 * <p>After each sample k, we check:
 * <pre>
 *   successes_so_far + remaining_samples < required_successes
 * </pre>
 * If true, even if all remaining samples pass, we cannot reach the threshold.
 */
public class EarlyTerminationEvaluator {

    private final int totalSamples;
    private final int requiredSuccesses;

    /**
     * Creates an evaluator for the given test configuration.
     *
     * @param totalSamples the total number of planned samples
     * @param minPassRate the minimum required pass rate (0.0 to 1.0)
     */
    public EarlyTerminationEvaluator(int totalSamples, double minPassRate) {
        this.totalSamples = totalSamples;
        this.requiredSuccesses = calculateRequiredSuccesses(totalSamples, minPassRate);
    }

    /**
     * Calculates the number of successes required to meet the minimum pass rate.
     * Uses ceiling to ensure the threshold is met, not merely approached.
     *
     * @param totalSamples total number of samples
     * @param minPassRate minimum pass rate (0.0 to 1.0)
     * @return minimum number of successes required
     */
    public static int calculateRequiredSuccesses(int totalSamples, double minPassRate) {
        return (int) Math.ceil(totalSamples * minPassRate);
    }

    /**
     * Evaluates whether the test should terminate early after a sample has completed.
     *
     * @param successesSoFar number of successful samples so far
     * @param samplesExecuted total samples executed so far
     * @return the termination reason if early termination should occur, empty otherwise
     */
    public Optional<TerminationReason> shouldTerminate(int successesSoFar, int samplesExecuted) {
        // Check for impossibility: can we still reach the required successes?
        int remainingSamples = totalSamples - samplesExecuted;
        int maxPossibleSuccesses = successesSoFar + remainingSamples;

        if (maxPossibleSuccesses < requiredSuccesses) {
            return Optional.of(TerminationReason.IMPOSSIBILITY);
        }

        return Optional.empty();
    }

    /**
     * Returns the number of successes required to pass the test.
     *
     * @return required number of successes
     */
    public int getRequiredSuccesses() {
        return requiredSuccesses;
    }

    /**
     * Returns the total number of planned samples.
     *
     * @return total samples
     */
    public int getTotalSamples() {
        return totalSamples;
    }

    /**
     * Calculates how many more failures can occur before impossibility is triggered.
     * Useful for understanding how close we are to early termination.
     *
     * @param successesSoFar current number of successes
     * @param samplesExecuted current number of samples executed
     * @return number of additional failures allowed before impossibility
     */
    public int getFailuresUntilImpossibility(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        int maxPossibleSuccesses = successesSoFar + remainingSamples;
        return maxPossibleSuccesses - requiredSuccesses;
    }

    /**
     * Builds a detailed explanation of why impossibility was triggered.
     *
     * @param successesSoFar successes at time of termination
     * @param samplesExecuted samples executed at time of termination
     * @return explanation string
     */
    public String buildImpossibilityExplanation(int successesSoFar, int samplesExecuted) {
        int remainingSamples = totalSamples - samplesExecuted;
        int maxPossibleSuccesses = successesSoFar + remainingSamples;
        
        return String.format(
            "After %d samples with %d successes, maximum possible successes (%d + %d = %d) " +
            "is less than required (%d)",
            samplesExecuted, successesSoFar,
            successesSoFar, remainingSamples, maxPossibleSuccesses,
            requiredSuccesses);
    }
}

