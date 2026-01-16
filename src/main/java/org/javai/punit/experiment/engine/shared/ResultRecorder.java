package org.javai.punit.experiment.engine.shared;

import org.javai.punit.api.ResultCaptor;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;

/**
 * Records results from a ResultCaptor into an ExperimentResultAggregator.
 *
 * <p>Handles success/failure determination and failure categorization.
 */
public final class ResultRecorder {

    private ResultRecorder() {
        // Utility class
    }

    /**
     * Records the result from the captor into the aggregator.
     *
     * <p>Success is determined in priority order:
     * <ol>
     *   <li>If criteria are recorded, use {@code criteria.allPassed()}</li>
     *   <li>Otherwise, fall back to legacy heuristics</li>
     * </ol>
     */
    public static void recordResult(ResultCaptor captor, ExperimentResultAggregator aggregator) {
        if (captor != null && captor.hasResult()) {
            UseCaseResult result = captor.getResult();

            // Determine success: prefer criteria if available
            boolean success;
            if (captor.hasCriteria()) {
                success = captor.getCriteria().allPassed();
                aggregator.recordCriteria(captor.getCriteria());
            } else {
                success = determineSuccess(result);
            }

            if (success) {
                aggregator.recordSuccess(result);
            } else {
                String failureCategory = determineFailureCategory(result, captor.getCriteria());
                aggregator.recordFailure(result, failureCategory);
            }
        } else if (captor != null && captor.hasException()) {
            aggregator.recordException(captor.getException());
        } else {
            aggregator.recordSuccess(UseCaseResult.builder().value("recorded", false).build());
        }
    }

    /**
     * Determines success from common result indicators.
     */
    private static boolean determineSuccess(UseCaseResult result) {
        if (result.hasValue("success")) {
            return result.getBoolean("success", false);
        }
        if (result.hasValue("isSuccess")) {
            return result.getBoolean("isSuccess", false);
        }
        if (result.hasValue("passed")) {
            return result.getBoolean("passed", false);
        }
        if (result.hasValue("isValid")) {
            return result.getBoolean("isValid", false);
        }
        if (result.hasValue("isValidJson")) {
            return result.getBoolean("isValidJson", false);
        }
        if (result.hasValue("error")) {
            return result.getValue("error", Object.class).isEmpty();
        }

        // Default to success if no failure indicators
        return true;
    }

    /**
     * Determines the failure category from criteria or result.
     */
    private static String determineFailureCategory(UseCaseResult result, UseCaseCriteria criteria) {
        // If criteria are available, derive failure category from first failed criterion
        if (criteria != null) {
            for (CriterionOutcome outcome : criteria.evaluate()) {
                if (!outcome.passed()) {
                    return outcome.description();
                }
            }
        }

        // Legacy: check for common failure category indicators in result
        if (result.hasValue("failureCategory")) {
            return result.getString("failureCategory", "unknown");
        }
        if (result.hasValue("errorType")) {
            return result.getString("errorType", "unknown");
        }
        if (result.hasValue("errorCode")) {
            return result.getString("errorCode", "unknown");
        }
        return "unknown";
    }
}
