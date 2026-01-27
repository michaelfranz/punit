package org.javai.punit.controls.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

/**
 * Thread-safe accumulator for global cost tracking across all test and experiment
 * executions in a JUnit engine lifecycle.
 *
 * <p>This class is stored in {@code ExtensionContext.getRoot().getStore()} and implements
 * {@link CloseableResource} to emit a final summary when the JUnit engine shuts down.
 *
 * <h2>Purpose</h2>
 * <p>Provides a single source of truth for:
 * <ul>
 *   <li>Total elapsed time across all probabilistic tests and experiments</li>
 *   <li>Total token consumption across all executions</li>
 *   <li>Counts of test methods, experiment methods, and samples executed</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All recording methods are thread-safe, using {@link LongAdder} for high-concurrency
 * accumulation. Multiple test threads can safely record metrics simultaneously.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GlobalCostAccumulator accumulator = GlobalCostAccumulator.getOrCreate(extensionContext);
 * accumulator.recordTokens(1500);
 * accumulator.recordSampleExecuted();
 * }</pre>
 *
 * <h2>Global Budget Enforcement</h2>
 * <p>When global budgets are configured via system properties, this accumulator
 * can be queried to check if the global budget is exhausted.
 *
 * @see SuiteBudgetManager
 */
public final class GlobalCostAccumulator implements CloseableResource, AutoCloseable {

    private static final Namespace NAMESPACE = Namespace.create(GlobalCostAccumulator.class);
    private static final String KEY = "global-cost-accumulator";

    // System property names for global budgets
    public static final String PROP_GLOBAL_TIME_BUDGET_MS = "punit.global.timeBudgetMs";
    public static final String PROP_GLOBAL_TOKEN_BUDGET = "punit.global.tokenBudget";
    public static final String PROP_GLOBAL_EMIT_SUMMARY = "punit.global.emitSummary";

    // Environment variable names
    public static final String ENV_GLOBAL_TIME_BUDGET_MS = "PUNIT_GLOBAL_TIME_BUDGET_MS";
    public static final String ENV_GLOBAL_TOKEN_BUDGET = "PUNIT_GLOBAL_TOKEN_BUDGET";
    public static final String ENV_GLOBAL_EMIT_SUMMARY = "PUNIT_GLOBAL_EMIT_SUMMARY";

    private final Instant startTime;
    private final long timeBudgetMs;
    private final long tokenBudget;
    private final boolean emitSummary;

    private final LongAdder totalTokens = new LongAdder();
    private final LongAdder totalSamplesExecuted = new LongAdder();
    private final LongAdder totalTestMethods = new LongAdder();
    private final LongAdder totalExperimentMethods = new LongAdder();
    private final LongAdder measureExperiments = new LongAdder();
    private final LongAdder exploreExperiments = new LongAdder();
    private final LongAdder optimizeExperiments = new LongAdder();

    /**
     * Creates a new global cost accumulator.
     *
     * <p>Reads configuration from system properties and environment variables.
     */
    public GlobalCostAccumulator() {
        this.startTime = Instant.now();
        this.timeBudgetMs = resolveLong(PROP_GLOBAL_TIME_BUDGET_MS, ENV_GLOBAL_TIME_BUDGET_MS, 0);
        this.tokenBudget = resolveLong(PROP_GLOBAL_TOKEN_BUDGET, ENV_GLOBAL_TOKEN_BUDGET, 0);
        this.emitSummary = resolveBoolean(PROP_GLOBAL_EMIT_SUMMARY, ENV_GLOBAL_EMIT_SUMMARY, true);
    }

    /**
     * Gets or creates the global cost accumulator from the root extension context store.
     *
     * <p>This method ensures only one accumulator exists per JUnit engine lifecycle.
     * The accumulator is lazily created on first access and shared across all tests.
     *
     * @param context any extension context (will navigate to root)
     * @return the global cost accumulator
     */
    public static GlobalCostAccumulator getOrCreate(ExtensionContext context) {
        ExtensionContext root = context.getRoot();
        return root.getStore(NAMESPACE)
                .getOrComputeIfAbsent(KEY, k -> new GlobalCostAccumulator(), GlobalCostAccumulator.class);
    }

    /**
     * Gets the global cost accumulator if it exists.
     *
     * @param context any extension context (will navigate to root)
     * @return the accumulator if present
     */
    public static Optional<GlobalCostAccumulator> get(ExtensionContext context) {
        ExtensionContext root = context.getRoot();
        return Optional.ofNullable(
                root.getStore(NAMESPACE).get(KEY, GlobalCostAccumulator.class));
    }

    // === Recording Methods (Thread-Safe) ===

    /**
     * Records token consumption.
     *
     * @param tokens the number of tokens consumed
     */
    public void recordTokens(long tokens) {
        totalTokens.add(tokens);
    }

    /**
     * Records that a sample was executed.
     */
    public void recordSampleExecuted() {
        totalSamplesExecuted.increment();
    }

    /**
     * Records that a probabilistic test method completed.
     */
    public void recordTestMethodCompleted() {
        totalTestMethods.increment();
    }

    /**
     * Records that an experiment method completed.
     *
     * @param mode the experiment mode (MEASURE, EXPLORE, or OPTIMIZE)
     */
    public void recordExperimentMethodCompleted(ExperimentMode mode) {
        totalExperimentMethods.increment();
        switch (mode) {
            case MEASURE -> measureExperiments.increment();
            case EXPLORE -> exploreExperiments.increment();
            case OPTIMIZE -> optimizeExperiments.increment();
        }
    }

    // === Query Methods ===

    /**
     * @return total tokens consumed across all executions
     */
    public long getTotalTokens() {
        return totalTokens.sum();
    }

    /**
     * @return total samples executed across all tests
     */
    public long getTotalSamplesExecuted() {
        return totalSamplesExecuted.sum();
    }

    /**
     * @return total probabilistic test methods completed
     */
    public long getTotalTestMethods() {
        return totalTestMethods.sum();
    }

    /**
     * @return total experiment methods completed
     */
    public long getTotalExperimentMethods() {
        return totalExperimentMethods.sum();
    }

    /**
     * @return elapsed time since accumulator creation
     */
    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * @return elapsed time in milliseconds
     */
    public long getElapsedMs() {
        return getElapsedTime().toMillis();
    }

    /**
     * @return the configured global time budget in ms (0 = unlimited)
     */
    public long getTimeBudgetMs() {
        return timeBudgetMs;
    }

    /**
     * @return the configured global token budget (0 = unlimited)
     */
    public long getTokenBudget() {
        return tokenBudget;
    }

    /**
     * @return true if global time budget is configured
     */
    public boolean hasTimeBudget() {
        return timeBudgetMs > 0;
    }

    /**
     * @return true if global token budget is configured
     */
    public boolean hasTokenBudget() {
        return tokenBudget > 0;
    }

    /**
     * Checks if the global time budget is exhausted.
     *
     * @return true if time budget is configured and exhausted
     */
    public boolean isTimeBudgetExhausted() {
        return hasTimeBudget() && getElapsedMs() >= timeBudgetMs;
    }

    /**
     * Checks if the global token budget is exhausted.
     *
     * @return true if token budget is configured and exhausted
     */
    public boolean isTokenBudgetExhausted() {
        return hasTokenBudget() && getTotalTokens() >= tokenBudget;
    }

    /**
     * Checks if any global budget is exhausted.
     *
     * @return true if any configured budget is exhausted
     */
    public boolean isAnyBudgetExhausted() {
        return isTimeBudgetExhausted() || isTokenBudgetExhausted();
    }

    // === CloseableResource Implementation ===

    /**
     * Called when the JUnit engine shuts down.
     *
     * <p>Emits a final summary of all costs if configured to do so.
     */
    @Override
    public void close() {
        if (emitSummary && hasMeaningfulData()) {
            emitFinalSummary();
        }
    }

    private boolean hasMeaningfulData() {
        return getTotalTestMethods() > 0 || getTotalExperimentMethods() > 0;
    }

    private void emitFinalSummary() {
        Duration elapsed = getElapsedTime();
        String formattedTime = formatDuration(elapsed);

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("PUnit Run Summary\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(String.format("  Total elapsed time:     %s%n", formattedTime));
        sb.append(String.format("  Total tokens consumed:  %,d%n", getTotalTokens()));
        sb.append("\n");

        long testMethods = getTotalTestMethods();
        long samples = getTotalSamplesExecuted();
        if (testMethods > 0) {
            sb.append(String.format("  Probabilistic tests:    %d method%s, %,d samples%n",
                    testMethods, testMethods == 1 ? "" : "s", samples));
        }

        long expMethods = getTotalExperimentMethods();
        if (expMethods > 0) {
            sb.append(String.format("  Experiments:            %d method%s",
                    expMethods, expMethods == 1 ? "" : "s"));

            long measure = measureExperiments.sum();
            long explore = exploreExperiments.sum();
            long optimize = optimizeExperiments.sum();

            if (measure > 0 || explore > 0 || optimize > 0) {
                sb.append(" (");
                boolean first = true;
                if (measure > 0) {
                    sb.append(measure).append(" MEASURE");
                    first = false;
                }
                if (explore > 0) {
                    if (!first) sb.append(", ");
                    sb.append(explore).append(" EXPLORE");
                    first = false;
                }
                if (optimize > 0) {
                    if (!first) sb.append(", ");
                    sb.append(optimize).append(" OPTIMIZE");
                }
                sb.append(")");
            }
            sb.append("\n");
        }

        if (hasTimeBudget() || hasTokenBudget()) {
            sb.append("\n");
            if (hasTimeBudget()) {
                sb.append(String.format("  Time budget:            %s of %s%n",
                        formattedTime, formatDuration(Duration.ofMillis(timeBudgetMs))));
            }
            if (hasTokenBudget()) {
                sb.append(String.format("  Token budget:           %,d of %,d%n",
                        getTotalTokens(), tokenBudget));
            }
        }

        sb.append("═══════════════════════════════════════════════════════════════\n");

        System.out.println(sb);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%ds", seconds);
        } else {
            return String.format("%dms", duration.toMillis());
        }
    }

    // === Configuration Resolution ===

    private static long resolveLong(String sysProp, String envVar, long defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        value = System.getenv(envVar);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    private static boolean resolveBoolean(String sysProp, String envVar, boolean defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value.trim());
        }

        value = System.getenv(envVar);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value.trim());
        }

        return defaultValue;
    }

    /**
     * Experiment modes for tracking purposes.
     */
    public enum ExperimentMode {
        MEASURE,
        EXPLORE,
        OPTIMIZE
    }
}