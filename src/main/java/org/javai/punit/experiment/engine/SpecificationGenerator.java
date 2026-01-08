package org.javai.punit.experiment.engine;

import java.time.Instant;
import java.util.Objects;

import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ExecutionSpecification;
import org.javai.punit.experiment.model.ExecutionSpecification.Approval;
import org.javai.punit.experiment.model.ExecutionSpecification.Provenance;
import org.javai.punit.experiment.model.ExecutionSpecification.Thresholds;
import org.javai.punit.experiment.model.ExecutionSpecification.Tolerances;

/**
 * Generates an ExecutionSpecification from an EmpiricalBaseline.
 *
 * <p>This class applies statistical reasoning to derive appropriate thresholds
 * from observed experimental data, including:
 * <ul>
 *   <li>Minimum success rate based on confidence interval lower bound</li>
 *   <li>Recommended sample sizes for tests</li>
 *   <li>Tolerance margins accounting for sampling variance</li>
 * </ul>
 */
public final class SpecificationGenerator {

    // Default margin to subtract from observed rate for minimum threshold
    private static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    // Use 2 standard errors as a conservative margin
    private static final double STANDARD_ERRORS_MARGIN = 2.0;

    private SpecificationGenerator() {
    }

    /**
     * Generates a specification from a baseline with approval metadata.
     *
     * @param baseline the empirical baseline
     * @param approver the person/system approving
     * @param notes    optional approval notes
     * @return the generated specification
     */
    public static ExecutionSpecification generate(
            EmpiricalBaseline baseline, String approver, String notes) {
        Objects.requireNonNull(baseline, "baseline is required");
        Objects.requireNonNull(approver, "approver is required");

        return ExecutionSpecification.builder()
                .useCaseId(baseline.getUseCaseId())
                .specVersion("1.0")
                .createdAt(Instant.now())
                .provenance(createProvenance(baseline))
                .approval(new Approval(approver, Instant.now(), notes))
                .thresholds(deriveThresholds(baseline))
                .tolerances(deriveTolerances(baseline))
                .context(baseline.getContext())
                .build();
    }

    private static Provenance createProvenance(EmpiricalBaseline baseline) {
        return new Provenance(
                baseline.getUseCaseId() + "-baseline",
                baseline.getExperimentId(),
                baseline.getExperimentClass(),
                baseline.getExperimentMethod(),
                baseline.getGeneratedAt(),
                baseline.getExecution().samplesExecuted()
        );
    }

    private static Thresholds deriveThresholds(EmpiricalBaseline baseline) {
        EmpiricalBaseline.StatisticsSummary stats = baseline.getStatistics();

        double observed = stats.observedSuccessRate();
        double se = stats.standardError();

        // Use the lower bound of the 95% CI as the minimum success rate
        // This ensures we have high confidence the true rate is at least this value
        double minRate = stats.confidenceIntervalLower();

        // Ensure minimum rate is sensible (at least 0, at most observed)
        minRate = Math.max(0.0, Math.min(minRate, observed));

        // Round to 4 decimal places
        minRate = Math.round(minRate * 10000.0) / 10000.0;

        return new Thresholds(
                minRate,
                observed,
                se,
                DEFAULT_CONFIDENCE_LEVEL,
                "95% confidence interval lower bound"
        );
    }

    private static Tolerances deriveTolerances(EmpiricalBaseline baseline) {
        EmpiricalBaseline.StatisticsSummary stats = baseline.getStatistics();
        int samplesExecuted = baseline.getExecution().samplesExecuted();

        // Success rate margin: 2 standard errors (accounts for sampling variance)
        double margin = Math.min(stats.standardError() * STANDARD_ERRORS_MARGIN, 0.1);

        // Recommended sample sizes based on baseline sample size
        // For tests, we want smaller samples but enough for statistical validity
        int minSamples = Math.max(30, samplesExecuted / 50);
        int maxSamples = Math.max(100, samplesExecuted / 10);

        return new Tolerances(
                Math.round(margin * 10000.0) / 10000.0,
                minSamples,
                maxSamples
        );
    }
}

