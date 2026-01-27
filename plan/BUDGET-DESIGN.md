  # Design: Global Budget Tracking for PUnit

This document describes the design for unified global budget tracking across probabilistic tests and experiments.

## 1. Problem Summary

Two related issues need to be addressed:

1. **Broken time budget in optimization experiments** (per BUDGET-CODE-REVIEW.md): `OptimizeTimeBudgetPolicy` relies on `history.totalDuration()` which always returns `Duration.ZERO` during the optimization loop because `endTime` is never set.

2. **No unified global budget tracking** (per BUDGET-REQ.md): Currently, probabilistic tests have a three-tier budget system (method → class → suite), but experiments have a completely separate, inconsistent budget mechanism. There's no way to track total cost across a mixed run of experiments and tests.

## 2. Current Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ PROBABILISTIC TESTS                                             │
├─────────────────────────────────────────────────────────────────┤
│  Suite Level    →  SuiteBudgetManager (singleton)               │
│  Class Level    →  SharedBudgetMonitor (via @ProbabilisticTest  │
│                    BudgetExtension)                             │
│  Method Level   →  CostBudgetMonitor                            │
│  Coordination   →  BudgetOrchestrator                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ EXPERIMENTS (separate, inconsistent)                            │
├─────────────────────────────────────────────────────────────────┤
│  OPTIMIZE       →  OptimizeTimeBudgetPolicy (BROKEN)            │
│  MEASURE        →  timeBudgetMs in MeasureConfig (not checked?) │
│  EXPLORE        →  timeBudgetMs in ExploreConfig (not checked?) │
└─────────────────────────────────────────────────────────────────┘
```

## 3. Design Goals

1. **Unified tracking**: Single source of truth for elapsed time and token consumption across all run types
2. **Backward compatible**: Existing method/class/suite budgets continue to work
3. **Library-style**: Works without requiring custom launcher configuration
4. **Consistent semantics**: Experiments and tests share the same budget enforcement model

## 4. Recommended Approach: Jupiter Root Store + CloseableResource

Based on the options in BUDGET-REQ.md, we recommend **Option 3** (Jupiter extension level) for these reasons:

| Option                             | Pros                            | Cons                            |
|------------------------------------|---------------------------------|---------------------------------|
| TestExecutionListener              | Truly whole-run, clean          | Requires launcher configuration |
| LauncherSessionListener            | Broadest scope                  | Requires custom launcher setup  |
| **Root Store + CloseableResource** | Library-style, no config needed | Slightly less elegant           |

The root store approach means PUnit remains a **drop-in library** that works with Gradle, Maven, IntelliJ, etc. without requiring users to configure listeners.

## 5. Proposed Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    NEW: GlobalCostAccumulator                   │
│  (Stored in ExtensionContext.getRoot().getStore())              │
├─────────────────────────────────────────────────────────────────┤
│  - Thread-safe accumulators: LongAdder timeMs, LongAdder tokens │
│  - Implements CloseableResource for final emission              │
│  - Single instance per JUnit engine lifecycle                   │
│  - Records contributions from both tests AND experiments        │
└─────────────────────────────────────────────────────────────────┘
          │
          │ contributes to
          ▼
┌─────────────────────────────────────────────────────────────────┐
│              EXISTING: SuiteBudgetManager                       │
│              (Now delegates to GlobalCostAccumulator)           │
├─────────────────────────────────────────────────────────────────┤
│  - Still provides suite-level budget enforcement                │
│  - Reads from GlobalCostAccumulator for totals                  │
│  - Unchanged API for existing users                             │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│   SharedBudgetMonitor    │    │   ExperimentCostMonitor  │
│   (CLASS scope)          │    │   (NEW: for experiments) │
└──────────────────────────┘    └──────────────────────────┘
          │                              │
          ▼                              ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│   CostBudgetMonitor      │    │   OptimizeTimeBudget-    │
│   (METHOD scope)         │    │   Policy (FIXED)         │
└──────────────────────────┘    └──────────────────────────┘
```

## 6. Component Specifications

### 6.1 GlobalCostAccumulator (NEW)

```java
package org.javai.punit.controls.budget;

/**
 * Thread-safe accumulator for global cost tracking across all test/experiment
 * executions in a JUnit engine lifecycle.
 *
 * Stored in ExtensionContext.getRoot().getStore() and implements CloseableResource
 * to emit final totals when the engine shuts down.
 */
public final class GlobalCostAccumulator implements CloseableResource {

    private static final Namespace NAMESPACE = Namespace.create(GlobalCostAccumulator.class);
    private static final String KEY = "global-cost-accumulator";

    private final Instant startTime;
    private final LongAdder totalTokens = new LongAdder();
    private final LongAdder totalSamplesExecuted = new LongAdder();
    private final LongAdder totalTestMethods = new LongAdder();
    private final LongAdder totalExperimentMethods = new LongAdder();

    // Get-or-create from root store
    public static GlobalCostAccumulator getOrCreate(ExtensionContext context) { ... }

    // Thread-safe recording
    public void recordTokens(long tokens) { ... }
    public void recordSampleExecuted() { ... }
    public void recordTestMethodCompleted() { ... }
    public void recordExperimentMethodCompleted() { ... }

    // Accessors
    public long getTotalTokens() { ... }
    public Duration getElapsedTime() { ... }

    // CloseableResource - emits final summary
    @Override
    public void close() {
        emitFinalSummary();
    }
}
```

### 6.2 ExperimentCostMonitor (NEW)

```java
package org.javai.punit.experiment.engine;

/**
 * Cost monitor for experiments, analogous to CostBudgetMonitor for tests.
 * Provides elapsed time tracking that actually works during execution.
 */
public final class ExperimentCostMonitor {

    private final Instant startTime;
    private final long timeBudgetMs;
    private final LongAdder tokensConsumed = new LongAdder();

    public Duration getElapsedDuration() {
        return Duration.between(startTime, Instant.now());
    }

    public boolean isTimeBudgetExhausted() {
        return timeBudgetMs > 0 && getElapsedDuration().toMillis() >= timeBudgetMs;
    }

    public void recordTokens(long tokens) {
        tokensConsumed.add(tokens);
        // Also propagate to GlobalCostAccumulator
    }
}
```

### 6.3 Fix for OptimizeTimeBudgetPolicy

The existing `OptimizeTimeBudgetPolicy` should be modified to use a `startTime`-based approach rather than relying on `history.totalDuration()`:

```java
public final class OptimizeTimeBudgetPolicy implements OptimizeTerminationPolicy {

    private final Duration maxDuration;
    private final Instant startTime;  // NEW: track start time directly

    public OptimizeTimeBudgetPolicy(Duration maxDuration) {
        this.maxDuration = maxDuration;
        this.startTime = Instant.now();  // Capture at construction
    }

    @Override
    public Optional<OptimizeTerminationReason> shouldTerminate(OptimizeHistory history) {
        Duration elapsed = Duration.between(startTime, Instant.now());  // FIXED
        if (elapsed.compareTo(maxDuration) >= 0) {
            return Optional.of(OptimizeTerminationReason.timeBudgetExhausted(maxDuration.toMillis()));
        }
        return Optional.empty();
    }
}
```

Alternatively, add an `elapsedDuration()` method to `OptimizeHistory` as suggested in the code review.

## 7. Integration Points

### 7.1 Experiment Extensions

Each experiment mode strategy should:
1. Create an `ExperimentCostMonitor` at experiment start
2. Check budget before/after each iteration or sample
3. Record tokens consumed to both local monitor and `GlobalCostAccumulator`

### 7.2 Probabilistic Test Extension

The existing `ProbabilisticTestExtension` should be updated to:
1. Obtain `GlobalCostAccumulator` from root store
2. Record tokens and samples to the global accumulator (in addition to existing scoped monitors)

### 7.3 Final Summary Emission

When JUnit engine shuts down, `GlobalCostAccumulator.close()` emits:

```
═══════════════════════════════════════════════════════════════
PUnit Run Summary
═══════════════════════════════════════════════════════════════
  Total elapsed time:     3m 42s
  Total tokens consumed:  127,450

  Probabilistic tests:    12 methods, 1,200 samples
  Experiments:            3 methods (1 MEASURE, 2 EXPLORE)
═══════════════════════════════════════════════════════════════
```

## 8. Configuration

New system properties/environment variables:

| Property                    | Env Variable                  | Description                          |
|-----------------------------|-------------------------------|--------------------------------------|
| `punit.global.timeBudgetMs` | `PUNIT_GLOBAL_TIME_BUDGET_MS` | Hard limit for entire run            |
| `punit.global.tokenBudget`  | `PUNIT_GLOBAL_TOKEN_BUD<br/>GET`   | Hard limit for entire run            |
| `punit.global.emitSummary`  | `PUNIT_GLOBAL_EMIT_SUMMARY`   | Enable final summary (default: true) |

## 9. Implementation Plan

Since there are no external users of the framework, we can implement changes directly without concern for backward compatibility.

**Implementation order:**

1. Fix `OptimizeTimeBudgetPolicy` with `startTime`-based approach
2. Introduce `GlobalCostAccumulator` in the root store
3. Introduce `ExperimentCostMonitor` for experiment budget tracking
4. Update experiment strategies (MEASURE, EXPLORE, OPTIMIZE) to use new monitors
5. Wire `SuiteBudgetManager` to delegate to `GlobalCostAccumulator`
6. Update `ProbabilisticTestExtension` to record to global accumulator
7. Add final summary emission on engine shutdown

## 10. Testing Strategy

- Unit tests for `GlobalCostAccumulator` thread safety
- Integration tests using JUnit TestKit to verify:
  - Cross-test accumulation
  - Cross-experiment accumulation
  - Mixed test + experiment runs
  - Final summary emission
- Regression tests for existing budget behavior

## 11. Related Documents

- [BUDGET-REQ.md](BUDGET-REQ.md) - Original requirements and options analysis
- [BUDGET-CODE-REVIEW.md](BUDGET-CODE-REVIEW.md) - Code review identifying the OptimizeTimeBudgetPolicy bug