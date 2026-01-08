package org.javai.punit.experiment.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An approved execution specification derived from an empirical baseline.
 *
 * <p>A spec represents the human-reviewed and approved contract for probabilistic
 * behavior. It contains:
 * <ul>
 *   <li><b>Provenance</b>: Links back to the baseline it was derived from</li>
 *   <li><b>Approval metadata</b>: Who approved, when, and any notes</li>
 *   <li><b>Thresholds</b>: The minimum acceptable success rate</li>
 *   <li><b>Tolerances</b>: Acceptable variance margins</li>
 * </ul>
 *
 * <p>This is used by {@link org.javai.punit.api.ProbabilisticTest @ProbabilisticTest}
 * to drive test execution with statistically derived expectations.
 */
public final class ExecutionSpecification {

    private final String useCaseId;
    private final String specVersion;
    private final Instant createdAt;
    private final Provenance provenance;
    private final Approval approval;
    private final Thresholds thresholds;
    private final Tolerances tolerances;
    private final Map<String, Object> context;

    private ExecutionSpecification(Builder builder) {
        this.useCaseId = Objects.requireNonNull(builder.useCaseId, "useCaseId is required");
        this.specVersion = builder.specVersion;
        this.createdAt = builder.createdAt;
        this.provenance = builder.provenance;
        this.approval = builder.approval;
        this.thresholds = builder.thresholds;
        this.tolerances = builder.tolerances;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(builder.context));
    }

    public String getUseCaseId() {
        return useCaseId;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public Approval getApproval() {
        return approval;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public Tolerances getTolerances() {
        return tolerances;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Records the origin of this specification.
     */
    public static final class Provenance {
        private final String baselineId;
        private final String experimentId;
        private final String experimentClass;
        private final String experimentMethod;
        private final Instant baselineGeneratedAt;
        private final int baselineSamples;

        public Provenance(String baselineId, String experimentId, String experimentClass,
                          String experimentMethod, Instant baselineGeneratedAt, int baselineSamples) {
            this.baselineId = baselineId;
            this.experimentId = experimentId;
            this.experimentClass = experimentClass;
            this.experimentMethod = experimentMethod;
            this.baselineGeneratedAt = baselineGeneratedAt;
            this.baselineSamples = baselineSamples;
        }

        public String getBaselineId() {
            return baselineId;
        }

        public String getExperimentId() {
            return experimentId;
        }

        public String getExperimentClass() {
            return experimentClass;
        }

        public String getExperimentMethod() {
            return experimentMethod;
        }

        public Instant getBaselineGeneratedAt() {
            return baselineGeneratedAt;
        }

        public int getBaselineSamples() {
            return baselineSamples;
        }
    }

    /**
     * Records who approved this specification and when.
     */
    public static final class Approval {
        private final String approver;
        private final Instant approvedAt;
        private final String notes;

        public Approval(String approver, Instant approvedAt, String notes) {
            this.approver = approver;
            this.approvedAt = approvedAt;
            this.notes = notes;
        }

        public String getApprover() {
            return approver;
        }

        public Instant getApprovedAt() {
            return approvedAt;
        }

        public String getNotes() {
            return notes;
        }
    }

    /**
     * The minimum acceptable thresholds derived from the baseline.
     */
    public static final class Thresholds {
        private final double minSuccessRate;
        private final double observedSuccessRate;
        private final double standardError;
        private final double confidenceLevel;
        private final String derivationMethod;

        public Thresholds(double minSuccessRate, double observedSuccessRate,
                          double standardError, double confidenceLevel, String derivationMethod) {
            this.minSuccessRate = minSuccessRate;
            this.observedSuccessRate = observedSuccessRate;
            this.standardError = standardError;
            this.confidenceLevel = confidenceLevel;
            this.derivationMethod = derivationMethod;
        }

        public double getMinSuccessRate() {
            return minSuccessRate;
        }

        public double getObservedSuccessRate() {
            return observedSuccessRate;
        }

        public double getStandardError() {
            return standardError;
        }

        public double getConfidenceLevel() {
            return confidenceLevel;
        }

        public String getDerivationMethod() {
            return derivationMethod;
        }
    }

    /**
     * Acceptable variance margins for test execution.
     */
    public static final class Tolerances {
        private final double successRateMargin;
        private final int recommendedMinSamples;
        private final int recommendedMaxSamples;

        public Tolerances(double successRateMargin, int recommendedMinSamples, int recommendedMaxSamples) {
            this.successRateMargin = successRateMargin;
            this.recommendedMinSamples = recommendedMinSamples;
            this.recommendedMaxSamples = recommendedMaxSamples;
        }

        public double getSuccessRateMargin() {
            return successRateMargin;
        }

        public int getRecommendedMinSamples() {
            return recommendedMinSamples;
        }

        public int getRecommendedMaxSamples() {
            return recommendedMaxSamples;
        }
    }

    /**
     * Builder for ExecutionSpecification.
     */
    public static final class Builder {
        private String useCaseId;
        private String specVersion = "1.0";
        private Instant createdAt = Instant.now();
        private Provenance provenance;
        private Approval approval;
        private Thresholds thresholds;
        private Tolerances tolerances;
        private Map<String, Object> context = new LinkedHashMap<>();

        public Builder useCaseId(String useCaseId) {
            this.useCaseId = useCaseId;
            return this;
        }

        public Builder specVersion(String specVersion) {
            this.specVersion = specVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder provenance(Provenance provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder approval(Approval approval) {
            this.approval = approval;
            return this;
        }

        public Builder thresholds(Thresholds thresholds) {
            this.thresholds = thresholds;
            return this;
        }

        public Builder tolerances(Tolerances tolerances) {
            this.tolerances = tolerances;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = new LinkedHashMap<>(context);
            return this;
        }

        public ExecutionSpecification build() {
            return new ExecutionSpecification(this);
        }
    }
}

