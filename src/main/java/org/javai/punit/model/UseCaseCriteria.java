package org.javai.punit.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;


/**
 * Defines the success criteria (postconditions) for a use case.
 *
 * <p>Success criteria are an ordered collection of named criterion functions (lambdas)
 * that, when evaluated, determine whether specific success conditions are met. Each
 * criterion function, when evaluated, produces a {@link CriterionOutcome}.
 *
 * <p>The ordering is significant: criteria are evaluated sequentially in the order
 * they are defined. This allows developers to place cheaper evaluations first and
 * defer costly ones (e.g., hallucination detection via an LLM call) until later.
 * If an early criterion fails due to an exception, subsequent criteria that depend
 * on the same computation are marked as "not evaluated," avoiding unnecessary expense.
 *
 * <h2>Usage in Use Cases</h2>
 * <pre>{@code
 * public UseCaseOutcome search(String query) {
 *     LlmResponse response = llm.complete(buildPrompt(query));
 *     
 *     UseCaseResult result = UseCaseResult.builder()
 *         .value("response", response.content())
 *         .value("tokensUsed", response.tokens())
 *         .build();
 *     
 *     String content = response.content();
 *     Lazy<JsonNode> parsed = Lazy.of(() -> parseJson(content));
 *     
 *     UseCaseCriteria criteria = UseCaseCriteria.ordered()
 *         .criterion("JSON parsed", () -> parsed.get() != null)
 *         .criterion("Has products", () -> countProducts(parsed.get()) > 0)
 *         .criterion("No hallucination", () -> !detectHallucination(parsed.get()))
 *         .build();
 *     
 *     return new UseCaseOutcome(result, criteria);
 * }
 * }</pre>
 *
 * <h2>Consumption</h2>
 * <ul>
 *   <li><strong>Tests</strong>: Call {@link #assertAll()} to assert all criteria</li>
 *   <li><strong>Experiments</strong>: Call {@link #evaluate()} to get outcomes for recording</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>Exceptions thrown during criterion evaluation (except {@link Error} subclasses)
 * are caught and recorded as {@link CriterionOutcome.Errored} outcomes. If the same
 * exception instance is encountered by multiple criteria (e.g., from a shared lazy
 * computation), subsequent criteria are marked as {@link CriterionOutcome.NotEvaluated}.
 *
 * @see CriterionOutcome
 * @see UseCaseOutcome
 */
public interface UseCaseCriteria {

    /**
     * Returns the criteria entries in order.
     *
     * @return list of criterion entries (description to check supplier)
     */
    List<Map.Entry<String, Supplier<Boolean>>> entries();

    /**
     * Evaluates all criteria and returns their outcomes.
     *
     * <p>Each criterion is evaluated in order. Exceptions (except {@link Error})
     * are caught and recorded as {@link CriterionOutcome.Errored}. If the same
     * exception is encountered by a subsequent criterion, it is marked as
     * {@link CriterionOutcome.NotEvaluated} to avoid redundant error messages.
     *
     * @return list of criterion outcomes in order
     */
    default List<CriterionOutcome> evaluate() {
        Set<Throwable> seenExceptions = new HashSet<>();
        List<CriterionOutcome> outcomes = new ArrayList<>();

        for (Map.Entry<String, Supplier<Boolean>> entry : entries()) {
            String description = entry.getKey();
            Supplier<Boolean> check = entry.getValue();

            CriterionOutcome outcome = evaluateCriterion(description, check);

            if (outcome instanceof CriterionOutcome.Errored errored) {
                if (seenExceptions.contains(errored.cause())) {
                    // Same exception as before—cascade, mark as not evaluated
                    outcomes.add(new CriterionOutcome.NotEvaluated(description));
                } else {
                    seenExceptions.add(errored.cause());
                    outcomes.add(outcome);
                }
            } else {
                outcomes.add(outcome);
            }
        }

        return outcomes;
    }

    /**
     * Evaluates a single criterion with exception handling.
     */
    private static CriterionOutcome evaluateCriterion(String description, Supplier<Boolean> check) {
        try {
            boolean result = check.get();
            return result
                    ? new CriterionOutcome.Passed(description)
                    : new CriterionOutcome.Failed(description);
        } catch (Error e) {
            // Propagate Errors (OutOfMemoryError, StackOverflowError, etc.)
            throw e;
        } catch (Throwable t) {
            return new CriterionOutcome.Errored(description, t);
        }
    }

    /**
     * Returns true if all criteria pass.
     *
     * @return true if all criteria evaluate to passed
     */
    default boolean allPassed() {
        return evaluate().stream().allMatch(CriterionOutcome::passed);
    }

    /**
     * Asserts that all criteria pass.
     *
     * <p>Each criterion is asserted individually. On failure, an {@link AssertionError}
     * is thrown with a message describing the failed criterion.
     *
     * @throws AssertionError if any criterion fails
     */
    default void assertAll() {
        for (CriterionOutcome outcome : evaluate()) {
            if (!outcome.passed()) {
                throw new AssertionError("Criterion failed: " + formatOutcomeMessage(outcome));
            }
        }
    }

    /**
     * Formats a criterion outcome for error messages.
     *
     * @param outcome the outcome to format
     * @return a human-readable message describing the outcome
     */
    static String formatOutcomeMessage(CriterionOutcome outcome) {
        return switch (outcome) {
            case CriterionOutcome.Passed p -> p.description() + ": passed";
            case CriterionOutcome.Failed f -> f.description() + ": " + f.reason();
            case CriterionOutcome.Errored e -> e.description() + ": " + e.reason();
            case CriterionOutcome.NotEvaluated n -> n.description() + " (not evaluated)";
        };
    }

    /**
     * Creates a new builder for defining success criteria.
     *
     * <p>Criteria are evaluated in the order they are added to the builder.
     *
     * @return a new builder instance
     */
    static Builder ordered() {
        return new Builder();
    }

    /**
     * Creates a UseCaseCriteria representing a construction failure.
     *
     * <p>This is used when the criteria construction itself throws an exception.
     * The resulting criteria contains a single errored criterion.
     *
     * @param cause the exception that occurred during construction
     * @return a criteria with a single errored criterion
     */
    static UseCaseCriteria constructionFailed(Throwable cause) {
        Objects.requireNonNull(cause, "cause must not be null");
        return () -> List.of(
                Map.entry("Criteria construction", (Supplier<Boolean>) () -> {
                    throw new RuntimeException(cause);
                })
        );
    }

    /**
     * Returns the default (trivial) success criteria.
     *
     * <h2>Design by Contract Rationale</h2>
     * <p>In Design by Contract terms, success criteria are <em>postconditions</em>—they
     * define what must be true after a use case method returns. The lightest possible
     * postcondition is the <em>trivial</em> one: "the method returned normally."
     *
     * <p>This method returns an empty criteria list, which trivially passes. This is
     * the DbC-correct default when a use case has not declared explicit postconditions.
     *
     * <h2>Usage</h2>
     * <p>This method is used by the framework when a use case method returns a
     * {@link UseCaseResult} without bundled criteria, or when constructing a {@link UseCaseOutcome} for a
     * use case that hasn't defined explicit criteria.
     *
     * <p>Developers who need meaningful success criteria should define a
     * {@code criteria()} method on their use case class, or return a
     * {@link UseCaseOutcome} that bundles result and criteria together.
     *
     * @return empty criteria that trivially passes
     */
    static UseCaseCriteria defaultCriteria() {
        return ordered().build();
    }

    /**
     * Builder for constructing {@link UseCaseCriteria} instances.
     *
     * <p>Criteria are stored in insertion order and evaluated in that order.
     */
    class Builder {

        private final LinkedHashMap<String, Supplier<Boolean>> criteria = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds a criterion to the definition.
         *
         * @param description human-readable description of the criterion
         * @param check supplier that returns true if the criterion is satisfied
         * @return this builder
         * @throws NullPointerException if description or check is null
         * @throws IllegalArgumentException if a criterion with this description already exists
         */
        public Builder criterion(String description, Supplier<Boolean> check) {
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(check, "check must not be null");
            if (criteria.containsKey(description)) {
                throw new IllegalArgumentException(
                        "Criterion with description '" + description + "' already exists");
            }
            criteria.put(description, check);
            return this;
        }

        /**
         * Builds the success criteria.
         *
         * @return the constructed criteria
         */
        public UseCaseCriteria build() {
            LinkedHashMap<String, Supplier<Boolean>> copy = new LinkedHashMap<>(criteria);
            return () -> List.copyOf(copy.entrySet());
        }
    }
}

