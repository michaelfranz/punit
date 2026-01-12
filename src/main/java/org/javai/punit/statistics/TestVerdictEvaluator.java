package org.javai.punit.statistics;

/**
 * Evaluates test results against a threshold and produces qualified verdicts.
 * 
 * <h2>Purpose</h2>
 * <p>Unlike simple pass/fail testing, this evaluator provides:
 * <ul>
 *   <li>Statistical context for the decision</li>
 *   <li>False positive probability quantification</li>
 *   <li>Human-readable interpretation</li>
 * </ul>
 * 
 * <h2>Interpretation of Failures</h2>
 * <p>A test failure means:
 * <blockquote>
 *   "The observed behavior is statistically inconsistent with the baseline
 *   at the configured confidence level."
 * </blockquote>
 * 
 * <p>It does NOT mean "definitely broken." The {@link VerdictWithConfidence#falsePositiveProbability()}
 * quantifies the chance that this failure is due to sampling variance.
 * 
 * @see VerdictWithConfidence
 */
public class TestVerdictEvaluator {
    
    /**
     * Evaluates test results against a threshold.
     * 
     * <h3>Decision Rule</h3>
     * <pre>
     *   observed_rate = k_test / n_test
     *   
     *   if observed_rate ≥ threshold:
     *       PASS (no evidence of degradation)
     *   else:
     *       FAIL (evidence of degradation at confidence level)
     * </pre>
     * 
     * @param testSuccesses Number of successful samples in the test (k_test)
     * @param testSamples Total number of samples in the test (n_test)
     * @param threshold The derived threshold against which to compare
     * @return Verdict with full statistical context
     */
    public VerdictWithConfidence evaluate(int testSuccesses, int testSamples, DerivedThreshold threshold) {
        validateInputs(testSuccesses, testSamples, threshold);
        
        double observedRate = (double) testSuccesses / testSamples;
        boolean passed = observedRate >= threshold.value();
        
        // False positive probability = α = 1 - confidence
        // This is the probability of failing when the system is actually fine
        double falsePositiveProbability = passed ? 0.0 : (1.0 - threshold.context().confidence());
        
        String interpretation = generateInterpretation(passed, observedRate, threshold);
        
        return new VerdictWithConfidence(
            passed,
            observedRate,
            threshold,
            falsePositiveProbability,
            interpretation
        );
    }
    
    /**
     * Generates a human-readable interpretation of the test result.
     * 
     * @param passed Whether the test passed
     * @param observedRate The observed success rate
     * @param threshold The threshold used
     * @return Interpretation string
     */
    private String generateInterpretation(boolean passed, double observedRate, DerivedThreshold threshold) {
        double thresholdValue = threshold.value();
        double confidence = threshold.context().confidence();
        
        if (passed) {
            return String.format(
                "Observed %.1f%% ≥ %.1f%% threshold. No evidence of degradation from baseline.",
                observedRate * 100,
                thresholdValue * 100
            );
        } else {
            double shortfall = thresholdValue - observedRate;
            double falsePositivePercent = (1.0 - confidence) * 100;
            
            return String.format(
                "Observed %.1f%% < %.1f%% min pass rate (shortfall: %.1f%%). " +
                "This indicates DEGRADATION from the baseline with %.0f%% confidence. " +
                "There is a %.1f%% probability this failure is due to sampling variance " +
                "rather than actual system degradation.",
                observedRate * 100,
                thresholdValue * 100,
                shortfall * 100,
                confidence * 100,
                falsePositivePercent
            );
        }
    }
    
    /**
     * Evaluates multiple test runs and determines if there's a consistent pattern.
     * 
     * <p>This is useful for distinguishing between:
     * <ul>
     *   <li>Single failure (might be false positive)</li>
     *   <li>Repeated failures (strong evidence of degradation)</li>
     * </ul>
     * 
     * @param verdicts Array of individual test verdicts
     * @return Summary interpretation
     */
    public String summarizeMultipleRuns(VerdictWithConfidence... verdicts) {
        if (verdicts.length == 0) {
            return "No test runs to summarize.";
        }
        
        int passCount = 0;
        int failCount = 0;
        double totalFalsePositiveProb = 1.0;
        
        for (VerdictWithConfidence verdict : verdicts) {
            if (verdict.passed()) {
                passCount++;
            } else {
                failCount++;
                // Probability of ALL failures being false positives (assuming independence)
                totalFalsePositiveProb *= verdict.falsePositiveProbability();
            }
        }
        
        if (failCount == 0) {
            return String.format(
                "All %d runs passed. No evidence of degradation.",
                passCount
            );
        } else if (failCount == 1) {
            return String.format(
                "%d of %d runs failed. Single failure may be a false positive (%.1f%% probability).",
                failCount, verdicts.length,
                verdicts[0].falsePositiveProbability() * 100
            );
        } else {
            return String.format(
                "%d of %d runs failed. Probability of ALL being false positives: %.4f%%. " +
                "Strong evidence of actual degradation.",
                failCount, verdicts.length,
                totalFalsePositiveProb * 100
            );
        }
    }
    
    private void validateInputs(int testSuccesses, int testSamples, DerivedThreshold threshold) {
        if (testSamples <= 0) {
            throw new IllegalArgumentException("Test samples must be positive, got: " + testSamples);
        }
        if (testSuccesses < 0) {
            throw new IllegalArgumentException("Test successes must be non-negative, got: " + testSuccesses);
        }
        if (testSuccesses > testSamples) {
            throw new IllegalArgumentException(
                "Test successes (" + testSuccesses + ") cannot exceed samples (" + testSamples + ")");
        }
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold must not be null");
        }
    }
}

