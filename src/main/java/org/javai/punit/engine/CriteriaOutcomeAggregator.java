package org.javai.punit.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;

/**
 * Aggregates success criteria outcomes across multiple experiment samples.
 *
 * <p>This aggregator tracks:
 * <ul>
 *   <li>Pass/fail rates for each criterion across all samples</li>
 *   <li>Overall success rate (all criteria passed)</li>
 *   <li>Error statistics for criteria that threw exceptions</li>
 * </ul>
 *
 * <p>The aggregator preserves criterion insertion order for consistent reporting.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * CriteriaOutcomeAggregator aggregator = new CriteriaOutcomeAggregator();
 * 
 * for (int i = 0; i < samples; i++) {
 *     UseCaseResult result = useCase.execute();
 *     UseCaseCriteria criteria = useCase.criteria(result);
 *     aggregator.record(criteria);
 * }
 * 
 * Map<String, CriterionStats> stats = aggregator.getCriterionStats();
 * double overallRate = aggregator.getOverallSuccessRate();
 * }</pre>
 *
 * @see UseCaseCriteria
 */
public class CriteriaOutcomeAggregator {

    private final Map<String, CriterionStats> statsByDescription = new LinkedHashMap<>();
    private int samplesRecorded = 0;
    private int allPassedCount = 0;

    /**
     * Records outcomes from a success criteria evaluation.
     *
     * <p>The criteria's {@code evaluate()} method is called to obtain outcomes.
     * Each criterion's outcome is recorded in the aggregated statistics.
     *
     * @param criteria the success criteria to record
     */
    public void record(UseCaseCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");

        List<CriterionOutcome> outcomes = criteria.evaluate();
        samplesRecorded++;

        boolean allPassed = true;
        for (CriterionOutcome outcome : outcomes) {
            CriterionStats stats = statsByDescription.computeIfAbsent(
                outcome.description(), 
                CriterionStats::new
            );
            stats.record(outcome);
            
            if (!outcome.passed()) {
                allPassed = false;
            }
        }

        if (allPassed) {
            allPassedCount++;
        }
    }

    /**
     * Returns statistics for each criterion, keyed by description.
     *
     * <p>The map preserves insertion order (first criterion seen comes first).
     *
     * @return unmodifiable map of criterion descriptions to statistics
     */
    public Map<String, CriterionStats> getCriterionStats() {
        return Collections.unmodifiableMap(statsByDescription);
    }

    /**
     * Returns the overall success rate (samples where all criteria passed).
     *
     * @return success rate from 0.0 to 1.0
     */
    public double getOverallSuccessRate() {
        if (samplesRecorded == 0) {
            return 0.0;
        }
        return (double) allPassedCount / samplesRecorded;
    }

    /**
     * Returns the number of samples recorded.
     *
     * @return sample count
     */
    public int getSamplesRecorded() {
        return samplesRecorded;
    }

    /**
     * Returns the number of samples where all criteria passed.
     *
     * @return count of fully successful samples
     */
    public int getAllPassedCount() {
        return allPassedCount;
    }

    /**
     * Returns a summary suitable for inclusion in spec files.
     *
     * <p>The summary is a map of criterion descriptions to pass rates:
     * <pre>
     * criteriaPassRates:
     *   "JSON parsed": 0.98
     *   "Has products": 0.95
     *   "No hallucination": 0.92
     * </pre>
     *
     * @return map of descriptions to pass rates
     */
    public Map<String, Double> getPassRateSummary() {
        Map<String, Double> summary = new LinkedHashMap<>();
        for (Map.Entry<String, CriterionStats> entry : statsByDescription.entrySet()) {
            summary.put(entry.getKey(), entry.getValue().getPassRate());
        }
        return summary;
    }

    /**
     * Statistics for a single criterion across all samples.
     */
    public static class CriterionStats {
        private final String description;
        private int passed = 0;
        private int failed = 0;
        private int errored = 0;
        private int notEvaluated = 0;
        private final List<String> errorMessages = new ArrayList<>();

        CriterionStats(String description) {
            this.description = description;
        }

        void record(CriterionOutcome outcome) {
            switch (outcome) {
                case CriterionOutcome.Passed p -> passed++;
                case CriterionOutcome.Failed f -> failed++;
                case CriterionOutcome.Errored e -> {
                    this.errored++;
                    // Keep first few error messages for diagnostics
                    if (errorMessages.size() < 5) {
                        errorMessages.add(e.reason());
                    }
                }
                case CriterionOutcome.NotEvaluated n -> notEvaluated++;
            }
        }

        /**
         * Returns the criterion description.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the count of samples where this criterion passed.
         */
        public int getPassed() {
            return passed;
        }

        /**
         * Returns the count of samples where this criterion failed.
         */
        public int getFailed() {
            return failed;
        }

        /**
         * Returns the count of samples where this criterion errored.
         */
        public int getErrored() {
            return errored;
        }

        /**
         * Returns the count of samples where this criterion was not evaluated.
         */
        public int getNotEvaluated() {
            return notEvaluated;
        }

        /**
         * Returns the total number of samples this criterion was recorded for.
         */
        public int getTotal() {
            return passed + failed + errored + notEvaluated;
        }

        /**
         * Returns the pass rate for this criterion.
         */
        public double getPassRate() {
            int total = getTotal();
            if (total == 0) {
                return 0.0;
            }
            return (double) passed / total;
        }

        /**
         * Returns sample error messages for diagnostics.
         */
        public List<String> getErrorMessages() {
            return Collections.unmodifiableList(errorMessages);
        }
    }
}

