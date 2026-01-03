package org.javai.punit.model;

/**
 * Reasons why a probabilistic test terminated.
 *
 * <p>Termination reasons fall into several categories:
 * <ul>
 *   <li>COMPLETED: Normal completion (all samples ran)</li>
 *   <li>IMPOSSIBILITY: Mathematical impossibility to reach pass rate</li>
 *   <li>Budget exhaustion: Time or token budget exceeded at method, class, or suite level</li>
 * </ul>
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
    IMPOSSIBILITY("Cannot reach required pass rate"),

    /**
     * Method-level time budget was exhausted.
     */
    METHOD_TIME_BUDGET_EXHAUSTED("Method time budget exhausted"),

    /**
     * Method-level token budget was exhausted.
     */
    METHOD_TOKEN_BUDGET_EXHAUSTED("Method token budget exhausted"),

    /**
     * Class-level time budget was exhausted.
     */
    CLASS_TIME_BUDGET_EXHAUSTED("Class time budget exhausted"),

    /**
     * Class-level token budget was exhausted.
     */
    CLASS_TOKEN_BUDGET_EXHAUSTED("Class token budget exhausted"),

    /**
     * Suite-level time budget was exhausted.
     */
    SUITE_TIME_BUDGET_EXHAUSTED("Suite time budget exhausted"),

    /**
     * Suite-level token budget was exhausted.
     */
    SUITE_TOKEN_BUDGET_EXHAUSTED("Suite token budget exhausted");

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

    /**
     * Returns true if this is a budget-related termination.
     *
     * @return true if budget exhaustion caused termination
     */
    public boolean isBudgetExhaustion() {
        return this == METHOD_TIME_BUDGET_EXHAUSTED ||
               this == METHOD_TOKEN_BUDGET_EXHAUSTED ||
               this == CLASS_TIME_BUDGET_EXHAUSTED ||
               this == CLASS_TOKEN_BUDGET_EXHAUSTED ||
               this == SUITE_TIME_BUDGET_EXHAUSTED ||
               this == SUITE_TOKEN_BUDGET_EXHAUSTED;
    }

    /**
     * Returns true if this is a time budget exhaustion.
     *
     * @return true if time budget exhausted
     */
    public boolean isTimeBudgetExhaustion() {
        return this == METHOD_TIME_BUDGET_EXHAUSTED ||
               this == CLASS_TIME_BUDGET_EXHAUSTED ||
               this == SUITE_TIME_BUDGET_EXHAUSTED;
    }

    /**
     * Returns true if this is a token budget exhaustion.
     *
     * @return true if token budget exhausted
     */
    public boolean isTokenBudgetExhaustion() {
        return this == METHOD_TOKEN_BUDGET_EXHAUSTED ||
               this == CLASS_TOKEN_BUDGET_EXHAUSTED ||
               this == SUITE_TOKEN_BUDGET_EXHAUSTED;
    }
}
