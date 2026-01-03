package org.javai.punit.model;

/**
 * Reasons why a probabilistic test terminated.
 *
 * <p>For Phase 2, we support COMPLETED (all samples ran) and IMPOSSIBILITY
 * (mathematically impossible to reach the required pass rate).
 * Future phases will add budget-related termination reasons.
 */
public enum TerminationReason {

    /**
     * All planned samples were executed.
     */
    COMPLETED("All samples completed"),

    /**
     * Test terminated early because it became mathematically impossible
     * to reach the required minimum pass rate.
     */
    IMPOSSIBILITY("Cannot reach required pass rate");

    private final String description;

    TerminationReason(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of the termination reason.
     *
     * @return description of the termination reason
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns true if this termination reason represents an early termination
     * (before all samples were executed).
     *
     * @return true if early termination
     */
    public boolean isEarlyTermination() {
        return this != COMPLETED;
    }
}

