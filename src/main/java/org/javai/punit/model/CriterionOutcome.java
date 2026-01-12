package org.javai.punit.model;

/**
 * Represents the outcome of evaluating a single success criterion.
 *
 * <p>A criterion can have one of four outcomes:
 * <ul>
 *   <li>{@link Passed} - The criterion was satisfied</li>
 *   <li>{@link Failed} - The criterion was not satisfied</li>
 *   <li>{@link Errored} - The criterion evaluation threw an exception</li>
 *   <li>{@link NotEvaluated} - The criterion was skipped due to a prior error</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * for (CriterionOutcome outcome : criteria.evaluate()) {
 *     switch (outcome) {
 *         case Passed p -> logger.info("✓ " + p.description());
 *         case Failed f -> logger.warn("✗ " + f.description() + ": " + f.reason());
 *         case Errored e -> logger.error("! " + e.description(), e.cause());
 *         case NotEvaluated n -> logger.info("— " + n.description() + " (not evaluated)");
 *     }
 * }
 * }</pre>
 *
 * @see UseCaseCriteria
 */
public sealed interface CriterionOutcome {

    /**
     * Returns the human-readable description of this criterion.
     *
     * @return the criterion description
     */
    String description();

    /**
     * Returns whether this criterion passed.
     *
     * @return true if the criterion was satisfied, false otherwise
     */
    boolean passed();

    /**
     * Represents a criterion that was satisfied.
     *
     * @param description the criterion description
     */
    record Passed(String description) implements CriterionOutcome {
        @Override
        public boolean passed() {
            return true;
        }
    }

    /**
     * Represents a criterion that was not satisfied.
     *
     * @param description the criterion description
     * @param reason the reason the criterion failed (may be empty)
     */
    record Failed(String description, String reason) implements CriterionOutcome {
        
        /**
         * Creates a Failed outcome with a default reason.
         *
         * @param description the criterion description
         */
        public Failed(String description) {
            this(description, "Criterion not satisfied");
        }
        
        @Override
        public boolean passed() {
            return false;
        }
    }

    /**
     * Represents a criterion whose evaluation threw an exception.
     *
     * @param description the criterion description
     * @param cause the exception that was thrown
     */
    record Errored(String description, Throwable cause) implements CriterionOutcome {
        @Override
        public boolean passed() {
            return false;
        }

        /**
         * Returns a formatted reason string including the exception type and message.
         *
         * @return the formatted error reason
         */
        public String reason() {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }

    /**
     * Represents a criterion that was not evaluated due to a prior error.
     *
     * <p>This occurs when a previous criterion threw an exception that also
     * affects this criterion (e.g., a shared lazy computation failed).
     *
     * @param description the criterion description
     */
    record NotEvaluated(String description) implements CriterionOutcome {
        @Override
        public boolean passed() {
            return false;
        }
    }
}

