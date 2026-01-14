package org.javai.punit.api;

/**
 * Classification of covariates by their nature and matching semantics.
 *
 * <p>Each category determines:
 * <ul>
 *   <li>How mismatches are handled (hard fail vs soft warning)</li>
 *   <li>The language used in statistical reports</li>
 *   <li>Whether the covariate participates in baseline filename hashing</li>
 * </ul>
 *
 * @see Covariate
 * @see StandardCovariate#category()
 */
public enum CovariateCategory {

    /**
     * Temporal and cyclical factors affecting system behavior.
     *
     * <p>Examples: time_of_day, weekday_vs_weekend
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Temporal factors may influence system behavior"
     */
    TEMPORAL,

    /**
     * Deliberate system configuration choices.
     *
     * <p>Examples: llm_model, prompt_version, temperature
     *
     * <p><strong>Matching:</strong> Hard gate — no compatible baseline found on mismatch
     * <p><strong>Report language:</strong> Actionable error guiding to EXPLORE/MEASURE
     *
     * <p>A mismatch in CONFIGURATION covariates indicates the developer should
     * use EXPLORE mode to compare configurations, or MEASURE to establish a
     * new baseline with the current configuration.
     */
    CONFIGURATION,

    /**
     * External services and dependencies outside our control.
     *
     * <p>Examples: third_party_api_version, upstream_service
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Third-party service behavior may have changed"
     */
    EXTERNAL_DEPENDENCY,

    /**
     * Execution environment characteristics.
     *
     * <p>Examples: cloud_provider, instance_type, region, timezone
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Resource availability and latency characteristics may vary"
     */
    INFRASTRUCTURE,

    /**
     * Data state affecting behavior.
     *
     * <p>Examples: cache_state, index_version, training_data_version, catalog_size
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Data volume or distribution may affect performance"
     */
    DATA_STATE,

    /**
     * Traceability metadata with no impact on matching.
     *
     * <p>Examples: run_id, operator_tag, experiment_label, branch_name
     *
     * <p><strong>Matching:</strong> Ignored — not considered in baseline selection
     * <p><strong>Report language:</strong> Not displayed in conformance section
     * <p><strong>Filename hash:</strong> Excluded from filename hash
     *
     * <p>INFORMATIONAL covariates are recorded in the baseline for audit trails
     * and debugging, but do not affect which baseline is selected.
     */
    INFORMATIONAL;

    /**
     * Returns true if this category requires exact match (hard gate).
     *
     * @return true for CONFIGURATION, false for all others
     */
    public boolean isHardGate() {
        return this == CONFIGURATION;
    }

    /**
     * Returns true if this category is ignored during matching.
     *
     * @return true for INFORMATIONAL, false for all others
     */
    public boolean isIgnoredInMatching() {
        return this == INFORMATIONAL;
    }

    /**
     * Returns true if this category participates in soft matching.
     *
     * @return true for TEMPORAL, EXTERNAL_DEPENDENCY, INFRASTRUCTURE, DATA_STATE
     */
    public boolean isSoftMatch() {
        return this != CONFIGURATION && this != INFORMATIONAL;
    }
}

