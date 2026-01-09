package org.javai.punit.experiment.api;

import org.javai.punit.experiment.model.UseCaseResult;

/**
 * Captures use case execution results during experiments.
 *
 * <p>The {@code ResultCaptor} is injected into experiment methods and provides
 * a way to record execution results for aggregation. Each experiment invocation
 * gets a fresh captor instance.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     // Execute and capture result
 *     captor.record(useCase.searchProducts("wireless headphones", context));
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>The extension creates a new captor for each sample</li>
 *   <li>The captor is injected as a method parameter</li>
 *   <li>The experiment method calls {@code captor.record(result)}</li>
 *   <li>After method execution, the extension reads the result and aggregates it</li>
 * </ol>
 *
 * @see org.javai.punit.experiment.api.Experiment
 */
public class ResultCaptor {

    private volatile UseCaseResult result;
    private volatile Throwable exception;
    private volatile boolean recorded = false;

    /**
     * Records a use case execution result.
     *
     * <p>Call this method with the result of your use case invocation.
     * Only the first recorded result is kept; subsequent calls are ignored.
     *
     * @param result the use case result to record
     * @return the result (for fluent chaining)
     */
    public UseCaseResult record(UseCaseResult result) {
        if (!recorded) {
            this.result = result;
            this.recorded = true;
        }
        return result;
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
     * @return true if a result or exception was recorded
     */
    public boolean hasResult() {
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
     * Resets the captor for reuse.
     */
    void reset() {
        this.result = null;
        this.exception = null;
        this.recorded = false;
    }
}

