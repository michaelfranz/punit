package org.javai.punit.ptest.strategy;

/**
 * Enum for token charging modes in probabilistic tests.
 *
 * <p>Token charging determines how LLM API call costs are tracked during test execution.
 * The mode affects when and how tokens are recorded against the budget.
 */
public enum TokenMode {
    /**
     * No token tracking enabled.
     *
     * <p>Use when cost tracking is not required or the test doesn't involve LLM calls.
     */
    NONE,

    /**
     * Fixed token charge per sample.
     *
     * <p>Use when each sample has a predictable, constant token cost. The charge
     * is specified via the {@code tokenCharge} parameter on the annotation.
     * Tokens are recorded after each sample completes.
     */
    STATIC,

    /**
     * Dynamic token recording via TokenChargeRecorder.
     *
     * <p>Use when token consumption varies per sample. The actual tokens used
     * are recorded by injecting a {@code TokenChargeRecorder} into the test method
     * and calling its recording methods. Tokens are finalized after each sample.
     */
    DYNAMIC
}
