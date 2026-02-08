package org.javai.punit.statistics;

/**
 * Determines whether a sample size is sufficient for SLA verification.
 *
 * <p>Uses the Wilson score one-sided lower confidence bound to determine if
 * even a perfect observation (zero failures) at the given sample size would
 * justify claiming SLA compliance at the specified confidence level.
 *
 * <p><strong>Intuition:</strong> If the test passes with zero failures, the best
 * we can claim is a lower confidence bound for the true success rate. If that
 * bound is still below the SLA target, the sample size cannot distinguish
 * SLA-compliant behavior from random luck — the result is a smoke test, not
 * a verification.
 *
 * <p>For p̂ = 1.0 (perfect observation), the Wilson lower bound simplifies to:
 * <pre>
 *   lower_bound = n / (n + z²)
 * </pre>
 * where z = Φ⁻¹(1 − α).
 *
 * <p>Example with α = 0.001 (z ≈ 3.09) and SLA target p₀ = 0.9999:
 * <ul>
 *   <li>n = 200: lower bound ≈ 0.954 &lt; 0.9999 → undersized</li>
 *   <li>n = 500: lower bound ≈ 0.981 &lt; 0.9999 → undersized</li>
 *   <li>n = 10,000: lower bound ≈ 0.999 &lt; 0.9999 → undersized</li>
 *   <li>n ≈ 95,500: lower bound ≈ 0.9999 → sufficient</li>
 * </ul>
 */
public final class SlaVerificationSizer {

    /** Default significance level for the evidence threshold. */
    public static final double DEFAULT_ALPHA = 0.001;

    /** The exact phrase that must appear in reports when sample is undersized. */
    public static final String SIZING_NOTE = "sample not sized for SLA verification";

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();

    private SlaVerificationSizer() {
        // utility class
    }

    /**
     * Returns true if the sample size is too small for SLA verification
     * at the default significance level (α = 0.001).
     *
     * @param samples   the number of test samples (N)
     * @param slaTarget the SLA target pass rate (p₀), e.g. 0.9999
     * @return true if the sample size is insufficient for SLA verification
     */
    public static boolean isUndersized(int samples, double slaTarget) {
        return isUndersized(samples, slaTarget, DEFAULT_ALPHA);
    }

    /**
     * Returns true if the sample size is too small for SLA verification
     * at the given significance level.
     *
     * <p>A sample is undersized when even a perfect observation (all successes)
     * would produce a one-sided lower confidence bound below the SLA target.
     *
     * @param samples   the number of test samples (N)
     * @param slaTarget the SLA target pass rate (p₀)
     * @param alpha     the significance level (e.g. 0.001)
     * @return true if the sample size is insufficient for SLA verification
     */
    public static boolean isUndersized(int samples, double slaTarget, double alpha) {
        if (samples <= 0 || slaTarget <= 0.0 || slaTarget >= 1.0) {
            return false;
        }
        double confidenceLevel = 1.0 - alpha;
        // Lower bound assuming perfect observation (k = n, i.e. zero failures)
        double lowerBound = ESTIMATOR.lowerBound(samples, samples, confidenceLevel);
        return lowerBound < slaTarget;
    }

    /**
     * Determines whether a test is anchored to an SLA contract.
     *
     * <p>A test is SLA-anchored if its threshold origin is {@code SLA} or
     * if a contract reference is provided.
     *
     * @param thresholdOriginName the threshold origin name (e.g. "SLA", "SLO")
     * @param contractRef         the contract reference string (may be null)
     * @return true if the test is anchored to an SLA
     */
    public static boolean isSlaAnchored(String thresholdOriginName, String contractRef) {
        boolean hasSlaOrigin = "SLA".equalsIgnoreCase(thresholdOriginName);
        boolean hasContractRef = contractRef != null && !contractRef.isEmpty();
        return hasSlaOrigin || hasContractRef;
    }
}
