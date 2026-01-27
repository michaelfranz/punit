package org.javai.punit.controls.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.model.TerminationReason;

/**
 * Unified cost monitor for tracking time and token budgets.
 *
 * <p>This class provides budget monitoring for both probabilistic tests and experiments.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Tracks elapsed time from monitor creation</li>
 *   <li>Tracks token consumption</li>
 *   <li>Tracks sample execution counts</li>
 *   <li>Provides budget exhaustion checks</li>
 *   <li>Optionally propagates costs to a {@link GlobalCostAccumulator}</li>
 *   <li>Thread-safe token and sample recording</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All recording methods are thread-safe using {@link LongAdder}. Time tracking is
 * inherently thread-safe as it's based on an immutable start time.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create with time and token budgets
 * CostMonitor monitor = CostMonitor.builder()
 *     .timeBudgetMs(60000)
 *     .tokenBudget(100000)
 *     .onBudgetExhausted(BudgetExhaustedBehavior.FAIL)
 *     .build();
 *
 * // Record costs
 * monitor.recordTokens(1500);
 * monitor.recordSampleExecuted();
 *
 * // Check budgets
 * if (monitor.isAnyBudgetExhausted()) {
 *     // Handle exhaustion
 * }
 * }</pre>
 */
public final class CostMonitor {

    private final long timeBudgetMs;
    private final long tokenBudget;
    private final BudgetExhaustedBehavior onBudgetExhausted;
    private final TerminationReason timeExhaustedReason;
    private final TerminationReason tokenExhaustedReason;

    private final Instant startTime;
    private final LongAdder tokensConsumed = new LongAdder();
    private final LongAdder samplesExecuted = new LongAdder();

    private GlobalCostAccumulator globalAccumulator;

    private CostMonitor(Builder builder) {
        this.timeBudgetMs = builder.timeBudgetMs;
        this.tokenBudget = builder.tokenBudget;
        this.onBudgetExhausted = builder.onBudgetExhausted;
        this.timeExhaustedReason = builder.timeExhaustedReason;
        this.tokenExhaustedReason = builder.tokenExhaustedReason;
        this.startTime = Instant.now();
    }

    /**
     * Creates a new builder for CostMonitor.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a monitor with no budget limits (tracking only).
     *
     * @return a new monitor with unlimited budgets
     */
    public static CostMonitor unlimited() {
        return builder().build();
    }

    /**
     * Creates a monitor with only a time budget.
     *
     * @param timeBudgetMs the time budget in milliseconds
     * @return a new monitor with the specified time budget
     */
    public static CostMonitor withTimeBudget(long timeBudgetMs) {
        return builder().timeBudgetMs(timeBudgetMs).build();
    }

    /**
     * Creates a monitor with only a token budget.
     *
     * @param tokenBudget the token budget
     * @return a new monitor with the specified token budget
     */
    public static CostMonitor withTokenBudget(long tokenBudget) {
        return builder().tokenBudget(tokenBudget).build();
    }

    // === Global Accumulator Integration ===

    /**
     * Sets the global accumulator for cost propagation.
     *
     * <p>When set, token consumption and sample counts are automatically
     * propagated to the global accumulator.
     *
     * @param accumulator the global cost accumulator
     */
    public void setGlobalAccumulator(GlobalCostAccumulator accumulator) {
        this.globalAccumulator = accumulator;
    }

    /**
     * @return the global accumulator, or null if not set
     */
    public GlobalCostAccumulator getGlobalAccumulator() {
        return globalAccumulator;
    }

    // === Recording Methods (Thread-Safe) ===

    /**
     * Records token consumption.
     *
     * <p>If a global accumulator is set, tokens are also recorded there.
     *
     * @param tokens the number of tokens consumed
     */
    public void recordTokens(long tokens) {
        if (tokens > 0) {
            tokensConsumed.add(tokens);
            if (globalAccumulator != null) {
                globalAccumulator.recordTokens(tokens);
            }
        }
    }

    /**
     * Records that a sample was executed.
     *
     * <p>If a global accumulator is set, the sample is also recorded there.
     */
    public void recordSampleExecuted() {
        samplesExecuted.increment();
        if (globalAccumulator != null) {
            globalAccumulator.recordSampleExecuted();
        }
    }

    /**
     * Records a sample execution with associated token consumption.
     *
     * <p>Convenience method that combines {@link #recordSampleExecuted()} and
     * {@link #recordTokens(long)}.
     *
     * @param tokens tokens consumed by this sample (0 if unknown)
     */
    public void recordSampleWithTokens(long tokens) {
        recordSampleExecuted();
        recordTokens(tokens);
    }

    // === Time Tracking ===

    /**
     * Gets the elapsed duration since the monitor was created.
     *
     * @return the elapsed duration
     */
    public Duration getElapsedDuration() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Gets the elapsed time in milliseconds.
     *
     * @return elapsed milliseconds
     */
    public long getElapsedMs() {
        return getElapsedDuration().toMillis();
    }

    /**
     * Gets the start time of the monitor.
     *
     * @return the start instant
     */
    public Instant getStartTime() {
        return startTime;
    }

    // === Budget Checks ===

    /**
     * Checks if the time budget is exhausted.
     *
     * @return true if time budget is configured and exhausted
     */
    public boolean isTimeBudgetExhausted() {
        return hasTimeBudget() && getElapsedMs() >= timeBudgetMs;
    }

    /**
     * Checks if the token budget is exhausted.
     *
     * @return true if token budget is configured and exhausted
     */
    public boolean isTokenBudgetExhausted() {
        return hasTokenBudget() && getTokensConsumed() >= tokenBudget;
    }

    /**
     * Checks if any local budget is exhausted.
     *
     * @return true if any configured budget is exhausted
     */
    public boolean isAnyBudgetExhausted() {
        return isTimeBudgetExhausted() || isTokenBudgetExhausted();
    }

    /**
     * Checks if the global budget is exhausted (if a global accumulator is set).
     *
     * @return true if global budget is exhausted
     */
    public boolean isGlobalBudgetExhausted() {
        return globalAccumulator != null && globalAccumulator.isAnyBudgetExhausted();
    }

    /**
     * Checks if any budget (local or global) is exhausted.
     *
     * @return true if any budget is exhausted
     */
    public boolean shouldTerminate() {
        return isAnyBudgetExhausted() || isGlobalBudgetExhausted();
    }

    /**
     * Checks the time budget and returns a termination reason if exhausted.
     *
     * <p>This method is compatible with the existing budget orchestration system.
     *
     * @return termination reason if time budget exceeded, empty otherwise
     */
    public Optional<TerminationReason> checkTimeBudget() {
        if (isTimeBudgetExhausted()) {
            return Optional.of(timeExhaustedReason);
        }
        return Optional.empty();
    }

    /**
     * Checks the token budget and returns a termination reason if exhausted.
     *
     * <p>This method is compatible with the existing budget orchestration system.
     *
     * @return termination reason if token budget exceeded, empty otherwise
     */
    public Optional<TerminationReason> checkTokenBudget() {
        if (isTokenBudgetExhausted()) {
            return Optional.of(tokenExhaustedReason);
        }
        return Optional.empty();
    }

    /**
     * Gets the reason for budget exhaustion, if any.
     *
     * @return the exhaustion reason description, or empty if no budget is exhausted
     */
    public Optional<String> getExhaustionReason() {
        if (isTimeBudgetExhausted()) {
            return Optional.of(String.format(
                    "Time budget exhausted: %dms elapsed >= %dms budget",
                    getElapsedMs(), timeBudgetMs));
        }
        if (isTokenBudgetExhausted()) {
            return Optional.of(String.format(
                    "Token budget exhausted: %d tokens >= %d budget",
                    getTokensConsumed(), tokenBudget));
        }
        if (isGlobalBudgetExhausted()) {
            if (globalAccumulator.isTimeBudgetExhausted()) {
                return Optional.of(String.format(
                        "Global time budget exhausted: %dms elapsed >= %dms budget",
                        globalAccumulator.getElapsedMs(), globalAccumulator.getTimeBudgetMs()));
            }
            if (globalAccumulator.isTokenBudgetExhausted()) {
                return Optional.of(String.format(
                        "Global token budget exhausted: %d tokens >= %d budget",
                        globalAccumulator.getTotalTokens(), globalAccumulator.getTokenBudget()));
            }
        }
        return Optional.empty();
    }

    // === Accessors ===

    /**
     * @return total tokens consumed
     */
    public long getTokensConsumed() {
        return tokensConsumed.sum();
    }

    /**
     * @return total samples executed
     */
    public long getSamplesExecuted() {
        return samplesExecuted.sum();
    }

    /**
     * @return the configured time budget in ms (0 = unlimited)
     */
    public long getTimeBudgetMs() {
        return timeBudgetMs;
    }

    /**
     * @return the configured token budget (0 = unlimited)
     */
    public long getTokenBudget() {
        return tokenBudget;
    }

    /**
     * @return true if time budget is configured
     */
    public boolean hasTimeBudget() {
        return timeBudgetMs > 0;
    }

    /**
     * @return true if token budget is configured
     */
    public boolean hasTokenBudget() {
        return tokenBudget > 0;
    }

    /**
     * @return the budget exhaustion behavior
     */
    public BudgetExhaustedBehavior getOnBudgetExhausted() {
        return onBudgetExhausted;
    }

    /**
     * @return remaining time budget in ms, or Long.MAX_VALUE if unlimited
     */
    public long getRemainingTimeMs() {
        if (!hasTimeBudget()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, timeBudgetMs - getElapsedMs());
    }

    /**
     * @return remaining token budget, or Long.MAX_VALUE if unlimited
     */
    public long getRemainingTokenBudget() {
        if (!hasTokenBudget()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, tokenBudget - getTokensConsumed());
    }

    // === Builder ===

    /**
     * Builder for {@link CostMonitor}.
     */
    public static final class Builder {
        private long timeBudgetMs = 0;
        private long tokenBudget = 0;
        private BudgetExhaustedBehavior onBudgetExhausted = BudgetExhaustedBehavior.FAIL;
        private TerminationReason timeExhaustedReason = TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED;
        private TerminationReason tokenExhaustedReason = TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED;

        private Builder() {}

        /**
         * Sets the time budget in milliseconds.
         *
         * @param timeBudgetMs the time budget (0 = unlimited)
         * @return this builder
         */
        public Builder timeBudgetMs(long timeBudgetMs) {
            this.timeBudgetMs = timeBudgetMs;
            return this;
        }

        /**
         * Sets the token budget.
         *
         * @param tokenBudget the token budget (0 = unlimited)
         * @return this builder
         */
        public Builder tokenBudget(long tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }

        /**
         * Sets the behavior when budget is exhausted.
         *
         * @param behavior the exhaustion behavior
         * @return this builder
         */
        public Builder onBudgetExhausted(BudgetExhaustedBehavior behavior) {
            this.onBudgetExhausted = behavior;
            return this;
        }

        /**
         * Sets the termination reason for time budget exhaustion.
         *
         * @param reason the termination reason
         * @return this builder
         */
        public Builder timeExhaustedReason(TerminationReason reason) {
            this.timeExhaustedReason = reason;
            return this;
        }

        /**
         * Sets the termination reason for token budget exhaustion.
         *
         * @param reason the termination reason
         * @return this builder
         */
        public Builder tokenExhaustedReason(TerminationReason reason) {
            this.tokenExhaustedReason = reason;
            return this;
        }

        /**
         * Builds the CostMonitor.
         *
         * @return a new CostMonitor
         */
        public CostMonitor build() {
            return new CostMonitor(this);
        }
    }
}