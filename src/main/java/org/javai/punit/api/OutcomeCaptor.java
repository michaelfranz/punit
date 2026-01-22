package org.javai.punit.api;

import org.javai.punit.contract.ContractCriteriaAdapter;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;

import java.time.Instant;
import java.util.List;

/**
 * Captures use case execution outcomes during experiments.
 *
 * <p>The {@code OutcomeCaptor} is injected into experiment methods and provides
 * a way to record execution outcomes for aggregation. Each experiment invocation
 * gets a fresh captor instance.
 *
 * <h2>Recommended Usage with Contract-based UseCaseOutcome</h2>
 * <p>The recommended pattern is to use the Design by Contract system with
 * {@link org.javai.punit.contract.UseCaseOutcome}:
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, OutcomeCaptor captor) {
 *     UseCaseOutcome<SearchResult> outcome = useCase.searchProducts("wireless headphones");
 *     captor.record(outcome);  // Records result and postconditions
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>The extension creates a new captor for each sample</li>
 *   <li>The captor is injected as a method parameter</li>
 *   <li>The experiment method calls {@code captor.record(outcome)}</li>
 *   <li>After method execution, the extension reads the outcome and aggregates it</li>
 * </ol>
 *
 * @see org.javai.punit.api.Experiment
 * @see org.javai.punit.contract.UseCaseOutcome
 * @see org.javai.punit.contract.ServiceContract
 */
public class OutcomeCaptor {

    private volatile UseCaseResult result;
    private volatile UseCaseCriteria criteria;
    private volatile org.javai.punit.contract.UseCaseOutcome<?> contractOutcome;
    private volatile Throwable exception;
    private volatile boolean recorded = false;

    /**
     * Records a contract-based use case outcome.
     *
     * <p>This is the recommended method for recording use case executions.
     * It captures the typed result and postconditions for aggregation.
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * @Experiment(useCase = MyUseCase.class, samples = 1000)
     * void measureBaseline(MyUseCase useCase, OutcomeCaptor captor) {
     *     UseCaseOutcome<String> outcome = useCase.performAction(input);
     *     captor.record(outcome);
     * }
     * }</pre>
     *
     * @param outcome the contract-based outcome to record
     * @param <R> the result type
     * @return the outcome (for fluent chaining)
     */
    public <R> org.javai.punit.contract.UseCaseOutcome<R> record(
            org.javai.punit.contract.UseCaseOutcome<R> outcome) {
        if (!recorded) {
            // Store the contract outcome for direct postcondition access
            this.contractOutcome = outcome;

            // Convert contract outcome to model types for legacy compatibility
            UseCaseResult.Builder builder = UseCaseResult.builder()
                    .executionTime(outcome.executionTime())
                    .timestamp(Instant.now());

            // Copy metadata
            outcome.metadata().forEach(builder::meta);

            // Store the raw result as a value (if not null)
            if (outcome.result() != null) {
                builder.value("result", outcome.result());
            }

            this.result = builder.build();
            this.criteria = ContractCriteriaAdapter.from(outcome);
            this.recorded = true;
        }
        return outcome;
    }

    /**
     * Records a contract-based use case outcome.
     *
     * @param outcome the contract-based outcome to record
     * @param <R> the result type
     * @return the outcome (for fluent chaining)
     * @deprecated Use {@link #record(org.javai.punit.contract.UseCaseOutcome)} instead
     */
    @Deprecated(forRemoval = true)
    public <R> org.javai.punit.contract.UseCaseOutcome<R> recordContract(
            org.javai.punit.contract.UseCaseOutcome<R> outcome) {
        return record(outcome);
    }

    /**
     * Records a legacy use case outcome (result + criteria together).
     *
     * @param outcome the legacy use case outcome to record
     * @return the outcome (for fluent chaining)
     * @deprecated Use {@link #record(org.javai.punit.contract.UseCaseOutcome)} with
     *             the contract-based UseCaseOutcome instead
     */
    @Deprecated(forRemoval = true)
    public UseCaseOutcome record(UseCaseOutcome outcome) {
        if (!recorded) {
            this.result = outcome.result();
            this.criteria = outcome.criteria();
            this.recorded = true;
        }
        return outcome;
    }

    /**
     * Records a use case execution result.
     *
     * <p>Call this method with the result of your use case invocation.
     * Only the first recorded result is kept; subsequent calls are ignored.
     *
     * @param result the use case result to record
     * @return the result (for fluent chaining)
     * @deprecated Use {@link #record(org.javai.punit.contract.UseCaseOutcome)} with
     *             a contract-based outcome instead
     */
    @Deprecated(forRemoval = true)
    public UseCaseResult record(UseCaseResult result) {
        if (!recorded) {
            this.result = result;
            this.recorded = true;
        }
        return result;
    }

    /**
     * Records success criteria for the use case result.
     *
     * <p>Call this method with the criteria obtained from the use case's
     * {@code criteria()} method. The criteria are evaluated lazily when
     * the extension processes the result.
     *
     * @param criteria the success criteria to record
     * @return the criteria (for fluent chaining)
     * @deprecated Use {@link #record(org.javai.punit.contract.UseCaseOutcome)} with
     *             a contract-based outcome that includes postconditions
     */
    @Deprecated(forRemoval = true)
    public UseCaseCriteria recordCriteria(UseCaseCriteria criteria) {
        this.criteria = criteria;
        return criteria;
    }

    /**
     * Records an exception that occurred during use case execution.
     *
     * @param e the exception to record
     */
    public void recordException(Throwable e) {
        if (!recorded) {
            this.exception = e;
            this.recorded = true;
        }
    }

    /**
     * Checks if a result has been recorded.
     *
     * <p>Returns true when a result (UseCaseResult or contract outcome) was recorded,
     * not when only an exception was recorded. Use {@link #hasException()} to check
     * for exceptions.
     *
     * @return true if a result was recorded
     */
    public boolean hasResult() {
        return result != null;
    }

    /**
     * Checks if anything (result or exception) has been recorded.
     *
     * @return true if something was recorded
     */
    public boolean isRecorded() {
        return recorded;
    }

    /**
     * Gets the recorded result.
     *
     * @return the recorded result, or null if none was recorded
     */
    public UseCaseResult getResult() {
        return result;
    }

    /**
     * Gets the recorded exception.
     *
     * @return the recorded exception, or null if none was recorded
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Checks if an exception was recorded.
     *
     * @return true if an exception was recorded
     */
    public boolean hasException() {
        return exception != null;
    }

    /**
     * Gets the recorded success criteria.
     *
     * @return the recorded criteria, or null if none was recorded
     * @deprecated Use {@link #getPostconditionResults()} for contract-based outcomes
     */
    @Deprecated(forRemoval = true)
    public UseCaseCriteria getCriteria() {
        return criteria;
    }

    /**
     * Checks if criteria have been recorded.
     *
     * @return true if criteria were recorded
     * @deprecated Use {@link #hasContractOutcome()} for contract-based outcomes
     */
    @Deprecated(forRemoval = true)
    public boolean hasCriteria() {
        return criteria != null;
    }

    /**
     * Checks if a contract-based outcome was recorded.
     *
     * <p>When true, {@link #getPostconditionResults()} can be used to get
     * postcondition results directly.
     *
     * @return true if a contract outcome was recorded via {@link #record}
     */
    public boolean hasContractOutcome() {
        return contractOutcome != null;
    }

    /**
     * Gets postcondition results from the recorded contract outcome.
     *
     * <p>This method evaluates the postconditions and returns the results.
     * For contracts with derivations, the results include both derivation
     * outcomes and their nested postconditions.
     *
     * @return the evaluated postcondition results, or null if no contract was recorded
     */
    public List<PostconditionResult> getPostconditionResults() {
        if (contractOutcome == null) {
            return null;
        }
        return contractOutcome.evaluatePostconditions();
    }

    /**
     * Checks if all postconditions passed.
     *
     * <p>This is a convenience method that evaluates the contract's postconditions
     * and returns true if all passed.
     *
     * @return true if a contract was recorded and all postconditions passed,
     *         false otherwise
     */
    public boolean allPostconditionsPassed() {
        if (contractOutcome == null) {
            return false;
        }
        return contractOutcome.allPostconditionsSatisfied();
    }

    /**
     * Resets the captor for reuse.
     */
    void reset() {
        this.result = null;
        this.criteria = null;
        this.contractOutcome = null;
        this.exception = null;
        this.recorded = false;
    }
}
