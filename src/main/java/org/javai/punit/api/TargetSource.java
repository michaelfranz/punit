package org.javai.punit.api;

/**
 * Indicates the source of a probabilistic test's threshold target.
 *
 * <p>This enum documents where the {@code minPassRate} threshold originated,
 * enabling traceability between test configuration and business requirements.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ProbabilisticTest(
 *     samples = 100,
 *     minPassRate = 0.95,
 *     targetSource = TargetSource.SLA,
 *     contractRef = "Acme API SLA v3.2 §2.1"
 * )
 * void serviceReturnsValidResponse() { ... }
 * }</pre>
 *
 * <h2>Available Sources</h2>
 * <ul>
 *   <li>{@link #UNSPECIFIED} - Default; threshold origin not documented</li>
 *   <li>{@link #SLA} - Service Level Agreement (external contract)</li>
 *   <li>{@link #SLO} - Service Level Objective (internal target)</li>
 *   <li>{@link #POLICY} - Organizational policy or compliance requirement</li>
 *   <li>{@link #EMPIRICAL} - Derived from baseline measurement</li>
 * </ul>
 *
 * @see ProbabilisticTest#targetSource()
 * @see ProbabilisticTest#contractRef()
 */
public enum TargetSource {

    /**
     * No source specified (default).
     * The threshold is ad-hoc or its origin is not documented.
     */
    UNSPECIFIED,

    /**
     * Threshold derived from a Service Level Agreement (SLA).
     * SLAs are typically contractual commitments to external customers.
     */
    SLA,

    /**
     * Threshold derived from a Service Level Objective (SLO).
     * SLOs are internal targets that may be more stringent than SLAs.
     */
    SLO,

    /**
     * Threshold derived from an organizational policy.
     * Policies may include security requirements, compliance mandates, etc.
     */
    POLICY,

    /**
     * Threshold derived from empirical measurement.
     * This indicates the threshold was established through observation
     * (e.g., baseline experiments) rather than contractual requirements.
     */
    EMPIRICAL
}

