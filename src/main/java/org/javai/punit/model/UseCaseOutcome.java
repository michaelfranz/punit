package org.javai.punit.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A compound return type that bundles a use case result with its success criteria.
 *
 * <p>{@code UseCaseOutcome} solves the "round-trip type safety" problem: it ensures
 * that a use case result and its criteria are always returned together, preventing
 * the programming error of evaluating criteria with an unrelated result.
 *
 * <h2>Why This Matters</h2>
 * <p>Without {@code UseCaseOutcome}, the old pattern was:
 * <pre>{@code
 * UseCaseResult result = useCase.search(query);
 * UseCaseCriteria criteria = useCase.criteria(result);  // What if result is from a different use case?
 * }</pre>
 *
 * <p>With {@code UseCaseOutcome}, the result and criteria are inextricably linked:
 * <pre>{@code
 * UseCaseOutcome outcome = useCase.search(query);
 * outcome.assertAll();  // Type-safe: criteria is bound to this result
 * }</pre>
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
 *     UseCaseCriteria criteria = UseCaseCriteria.ordered()
 *         .criterion("JSON parsed", () -> parsed.get() != null)
 *         .criterion("Has products", () -> countProducts(parsed.get()) > 0)
 *         .build();
 *     
 *     return new UseCaseOutcome(result, criteria);
 * }
 * }</pre>
 *
 * <h2>Usage in Tests</h2>
 * <pre>{@code
 * UseCaseOutcome outcome = useCase.search("wireless headphones");
 * outcome.assertAll();  // Throws on failure
 * }</pre>
 *
 * <h2>Usage in Experiments</h2>
 * <pre>{@code
 * UseCaseOutcome outcome = useCase.search(query);
 * captor.record(outcome);  // Records both result and criteria outcomes
 * }</pre>
 *
 * @see UseCaseResult
 * @see UseCaseCriteria
 * @see CriterionOutcome
 */
public record UseCaseOutcome(
    UseCaseResult result,
    UseCaseCriteria criteria
) {
    
    /**
     * Compact constructor for validation.
     */
    public UseCaseOutcome {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
    }
    
    // ========== Convenience Delegations to Criteria ==========
    
    /**
     * Evaluates all criteria and returns their outcomes.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#evaluate()}.
     *
     * @return list of criterion outcomes in order
     */
    public List<CriterionOutcome> evaluate() {
        return criteria.evaluate();
    }
    
    /**
     * Returns true if all criteria pass.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#allPassed()}.
     *
     * @return true if all criteria evaluate to passed
     */
    public boolean allPassed() {
        return criteria.allPassed();
    }
    
    /**
     * Asserts that all criteria pass.
     *
     * <p>This is a convenience delegation to {@link UseCaseCriteria#assertAll()}.
     *
     * @throws AssertionError if any criterion fails
     */
    public void assertAll() {
        criteria.assertAll();
    }
    
    /**
     * Evaluates criteria and returns results as a map of description to pass/fail.
     *
     * @return ordered map of criterion description to pass status
     */
    public Map<String, Boolean> evaluateAsMap() {
        java.util.LinkedHashMap<String, Boolean> map = new java.util.LinkedHashMap<>();
        for (CriterionOutcome outcome : evaluate()) {
            map.put(outcome.description(), outcome.passed());
        }
        return map;
    }
    
    // ========== Convenience Delegations to Result ==========
    
    /**
     * Returns the timestamp of when this use case was executed.
     *
     * <p>This is a convenience delegation to {@link UseCaseResult#timestamp()}.
     *
     * @return the execution timestamp
     */
    public Instant timestamp() {
        return result.timestamp();
    }
    
    /**
     * Returns the execution time of this use case.
     *
     * <p>This is a convenience delegation to {@link UseCaseResult#executionTime()}.
     *
     * @return the execution duration
     */
    public Duration executionTime() {
        return result.executionTime();
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Creates an outcome representing an invocation failure.
     *
     * <p>Use this when the use case method itself throws an exception.
     * The result is empty and the criteria contains a single errored criterion.
     *
     * @param cause the exception that occurred during invocation
     * @return an outcome representing the failure
     */
    public static UseCaseOutcome invocationFailed(Throwable cause) {
        Objects.requireNonNull(cause, "cause must not be null");
        UseCaseResult emptyResult = UseCaseResult.builder()
            .value("error", cause.getMessage())
            .value("errorType", cause.getClass().getName())
            .build();
        return new UseCaseOutcome(emptyResult, UseCaseCriteria.constructionFailed(cause));
    }
    
    /**
     * Creates an outcome from a result with default (trivial) criteria.
     *
     * <p>This is a migration helper for use cases that still return only
     * {@link UseCaseResult}. The framework wraps these results with the
     * trivial postcondition (empty criteria that always passes).
     *
     * <p>See {@link UseCaseCriteria#defaultCriteria()} for the Design by Contract
     * rationale behind using empty criteria as the default.
     *
     * @param result the use case result
     * @return an outcome with trivial (empty) criteria
     */
    public static UseCaseOutcome withDefaultCriteria(UseCaseResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new UseCaseOutcome(result, UseCaseCriteria.defaultCriteria());
    }
}

