# ExperimentExtension Refactoring Plan

## Document Status
**Status**: Draft
**Version**: 2.0
**Last Updated**: 2026-01-16

---

## Problem Statement

`ExperimentExtension` is 1,575 lines serving three distinct experiment modes: MEASURE, EXPLORE, and OPTIMIZE. The current structure:

- Intermingles mode-specific logic via switch/if statements
- Uses a "god record" (`ExperimentConfig`) with many null fields per mode
- Has asymmetric package structure: OPTIMIZE has a subpackage, MEASURE and EXPLORE don't
- Makes it unclear where new mode-specific behavior should go
- Is difficult to test in isolation

## Design Goals

1. **Structural clarity**: Each mode has its own package with symmetric organization
2. **Mode-agnostic coordinator**: `ExperimentExtension` dispatches to strategies, never branches on mode
3. **Testability**: Mode-specific logic can be unit tested without the full extension
4. **Extensibility**: Adding behavior to a mode has an obvious home

## Target Architecture

```
src/main/java/org/javai/punit/experiment/
├── engine/                              # Coordinator + shared infrastructure
│   ├── ExperimentExtension.java         # Thin dispatcher (~200-300 lines)
│   ├── ExperimentModeStrategy.java      # Strategy interface
│   ├── ExperimentConfig.java            # Sealed interface for config hierarchy
│   ├── ExperimentInvocationContext.java # Base interface/record
│   └── shared/                          # Helpers used by all modes
│       ├── FactorResolver.java
│       ├── ResultRecorder.java
│       ├── OutputPathResolver.java
│       └── ProgressReporter.java
│
├── measure/                             # MEASURE mode implementation
│   ├── MeasureConfig.java               # Record implementing ExperimentConfig
│   ├── MeasureStrategy.java             # Implements ExperimentModeStrategy
│   ├── MeasureInvocationContext.java
│   └── MeasureSpecGenerator.java
│
├── explore/                             # EXPLORE mode implementation
│   ├── ExploreConfig.java
│   ├── ExploreStrategy.java
│   ├── ExploreInvocationContext.java
│   └── ExploreSpecGenerator.java
│
└── optimize/                            # OPTIMIZE mode implementation (exists, needs alignment)
    ├── OptimizeConfig.java
    ├── OptimizeStrategy.java
    ├── OptimizeInvocationContext.java
    └── ... (existing: Scorer, FactorMutator, OptimizationHistory, etc.)
```

---

## Core Interfaces

### ExperimentConfig (Sealed Interface)

```java
package org.javai.punit.experiment.engine;

public sealed interface ExperimentConfig
    permits MeasureConfig, ExploreConfig, OptimizeConfig {

    /** The experiment mode. */
    ExperimentMode mode();

    /** The use case class. */
    Class<?> useCaseClass();

    /** Resolved use case ID. */
    String useCaseId();

    /** Time budget in milliseconds (0 = unlimited). */
    long timeBudgetMs();

    /** Token budget (0 = unlimited). */
    long tokenBudget();

    /** Experiment identifier for output naming. */
    String experimentId();
}
```

Each mode package provides its own record:

```java
// experiment.measure.MeasureConfig
public record MeasureConfig(
    Class<?> useCaseClass,
    String useCaseId,
    int samples,
    long timeBudgetMs,
    long tokenBudget,
    String experimentId,
    int expiresInDays
) implements ExperimentConfig {
    @Override public ExperimentMode mode() { return ExperimentMode.MEASURE; }
}

// experiment.explore.ExploreConfig
public record ExploreConfig(
    Class<?> useCaseClass,
    String useCaseId,
    int samplesPerConfig,
    long timeBudgetMs,
    long tokenBudget,
    String experimentId,
    int expiresInDays
) implements ExperimentConfig {
    @Override public ExperimentMode mode() { return ExperimentMode.EXPLORE; }
}

// experiment.optimize.OptimizeConfig
public record OptimizeConfig(
    Class<?> useCaseClass,
    String useCaseId,
    String treatmentFactor,
    Class<?> scorerClass,
    Class<?> mutatorClass,
    OptimizationObjective objective,
    int samplesPerIteration,
    int maxIterations,
    int noImprovementWindow,
    long timeBudgetMs,
    long tokenBudget,
    String experimentId
) implements ExperimentConfig {
    @Override public ExperimentMode mode() { return ExperimentMode.OPTIMIZE; }
}
```

### ExperimentModeStrategy (Interface)

```java
package org.javai.punit.experiment.engine;

/**
 * Strategy for handling a specific experiment mode.
 *
 * Each mode (MEASURE, EXPLORE, OPTIMIZE) provides an implementation that knows how to:
 * - Parse its annotation into a config
 * - Generate invocation contexts (the sample stream)
 * - Intercept and execute each sample
 * - Generate output (specs, optimization history, etc.)
 */
public interface ExperimentModeStrategy {

    /**
     * Parse the experiment annotation into a mode-specific config.
     *
     * @param testMethod the annotated test method
     * @return the parsed configuration
     * @throws ExtensionConfigurationException if annotation is invalid
     */
    ExperimentConfig parseConfig(Method testMethod);

    /**
     * Provide the stream of invocation contexts for this experiment.
     *
     * @param config the parsed configuration
     * @param context the JUnit extension context
     * @param store the extension store for shared state
     * @return stream of invocation contexts (one per sample)
     */
    Stream<TestTemplateInvocationContext> provideInvocationContexts(
        ExperimentConfig config,
        ExtensionContext context,
        ExtensionContext.Store store
    );

    /**
     * Intercept and execute a single sample.
     *
     * @param invocation the JUnit invocation to proceed or skip
     * @param invocationContext reflective context for the method
     * @param extensionContext the JUnit extension context
     * @param store the extension store for shared state
     */
    void intercept(
        InvocationInterceptor.Invocation<Void> invocation,
        ReflectiveInvocationContext<Method> invocationContext,
        ExtensionContext extensionContext,
        ExtensionContext.Store store
    ) throws Throwable;

    /**
     * Check if this strategy handles the given test method.
     *
     * @param testMethod the test method to check
     * @return true if this strategy's annotation is present
     */
    boolean supports(Method testMethod);
}
```

### ExperimentExtension (Thin Coordinator)

```java
package org.javai.punit.experiment.engine;

/**
 * JUnit 5 extension that coordinates experiment execution.
 *
 * This class is intentionally thin - it detects which mode annotation is present,
 * delegates to the corresponding strategy, and manages shared infrastructure
 * (pacing, store setup). Mode-specific logic lives in the strategy implementations.
 */
public class ExperimentExtension implements
    TestTemplateInvocationContextProvider, InvocationInterceptor {

    private static final List<ExperimentModeStrategy> STRATEGIES = List.of(
        new MeasureStrategy(),
        new ExploreStrategy(),
        new OptimizeStrategy()
    );

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
            .map(m -> STRATEGIES.stream().anyMatch(s -> s.supports(m)))
            .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {

        Method testMethod = context.getRequiredTestMethod();
        ExperimentModeStrategy strategy = findStrategy(testMethod);

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        ExperimentConfig config = strategy.parseConfig(testMethod);

        // Store shared state
        store.put("strategy", strategy);
        store.put("config", config);
        store.put("startTimeMs", System.currentTimeMillis());

        // Setup pacing (shared infrastructure)
        setupPacing(testMethod, config, store);

        return strategy.provideInvocationContexts(config, context, store);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {

        ExtensionContext.Store store = getParentStore(extensionContext);
        ExperimentModeStrategy strategy = store.get("strategy", ExperimentModeStrategy.class);

        // Apply pacing (shared infrastructure)
        applyPacingDelay(store);

        strategy.intercept(invocation, invocationContext, extensionContext, store);
    }

    private ExperimentModeStrategy findStrategy(Method testMethod) {
        return STRATEGIES.stream()
            .filter(s -> s.supports(testMethod))
            .findFirst()
            .orElseThrow(() -> new ExtensionConfigurationException(
                "No strategy found for method: " + testMethod.getName()));
    }

    // Pacing setup and application (shared infrastructure)
    private void setupPacing(Method testMethod, ExperimentConfig config,
                             ExtensionContext.Store store) { ... }
    private void applyPacingDelay(ExtensionContext.Store store) { ... }
}
```

---

## Implementation Phases

### Phase 1: Create Package Structure and Interfaces

**Goal**: Establish the target structure without moving existing code.

**Tasks**:
1. Create `experiment.engine.shared` package
2. Create `experiment.measure` package
3. Create `experiment.explore` package
4. Create `ExperimentConfig` sealed interface
5. Create `ExperimentModeStrategy` interface

**Files Created**:
```
experiment/engine/ExperimentConfig.java
experiment/engine/ExperimentModeStrategy.java
experiment/measure/package-info.java
experiment/explore/package-info.java
experiment/engine/shared/package-info.java
```

**Tests**: Interface compilation only (no behavior yet)

**Estimated Effort**: ~1 hour

---

### Phase 2: Extract MeasureStrategy

**Goal**: Move MEASURE-specific logic to its own package.

**Tasks**:
1. Create `MeasureConfig` record
2. Create `MeasureStrategy` implementing the interface
3. Move `MeasureInvocationContext` to `experiment.measure`
4. Move `MeasureWithFactorsInvocationContext` to `experiment.measure`
5. Extract MEASURE spec generation to `MeasureSpecGenerator`
6. Update `ExperimentExtension` to delegate MEASURE to strategy

**Files Created**:
```
experiment/measure/MeasureConfig.java
experiment/measure/MeasureStrategy.java
experiment/measure/MeasureInvocationContext.java
experiment/measure/MeasureWithFactorsInvocationContext.java
experiment/measure/MeasureSpecGenerator.java
```

**Tests**:
- `MeasureStrategyTest` - unit tests for config parsing, context generation
- `MeasureSpecGeneratorTest` - unit tests for spec generation
- Existing integration tests must pass

**Estimated Effort**: ~3 hours

---

### Phase 3: Extract ExploreStrategy

**Goal**: Move EXPLORE-specific logic to its own package.

**Tasks**:
1. Create `ExploreConfig` record
2. Create `ExploreStrategy` implementing the interface
3. Move `ExploreInvocationContext` to `experiment.explore`
4. Extract EXPLORE spec generation to `ExploreSpecGenerator`
5. Update `ExperimentExtension` to delegate EXPLORE to strategy

**Files Created**:
```
experiment/explore/ExploreConfig.java
experiment/explore/ExploreStrategy.java
experiment/explore/ExploreInvocationContext.java
experiment/explore/ExploreSpecGenerator.java
```

**Tests**:
- `ExploreStrategyTest` - unit tests for config parsing, context generation
- `ExploreSpecGeneratorTest` - unit tests for spec generation
- Existing integration tests must pass

**Estimated Effort**: ~3 hours

---

### Phase 4: Align OptimizeStrategy

**Goal**: Align existing `experiment.optimize` package with the strategy pattern.

**Tasks**:
1. Create `OptimizeConfig` record (or adapt existing)
2. Create `OptimizeStrategy` implementing the interface
3. Create `OptimizeInvocationContext`
4. Integrate existing optimize classes (Scorer, FactorMutator, etc.)
5. Update `ExperimentExtension` to delegate OPTIMIZE to strategy

**Files Created/Modified**:
```
experiment/optimize/OptimizeConfig.java
experiment/optimize/OptimizeStrategy.java
experiment/optimize/OptimizeInvocationContext.java
```

**Tests**:
- `OptimizeStrategyTest` - unit tests for config parsing
- Integration with existing optimize tests

**Estimated Effort**: ~2 hours

---

### Phase 5: Extract Shared Helpers

**Goal**: Extract shared infrastructure to `experiment.engine.shared`.

**Tasks**:
1. Extract `FactorResolver` - factor info extraction and naming
2. Extract `ResultRecorder` - recording results to aggregator
3. Extract `OutputPathResolver` - spec/exploration/optimization paths
4. Extract `ProgressReporter` - JUnit report entry publishing
5. Update strategies to use shared helpers

**Files Created**:
```
experiment/engine/shared/FactorResolver.java
experiment/engine/shared/ResultRecorder.java
experiment/engine/shared/OutputPathResolver.java
experiment/engine/shared/ProgressReporter.java
```

**Tests**:
- Unit tests for each helper
- Existing integration tests must pass

**Estimated Effort**: ~3 hours

---

### Phase 6: Extract Parameter Resolvers

**Goal**: Move parameter resolvers to appropriate locations.

**Tasks**:
1. Move `CaptorParameterResolver` to `experiment.engine.shared`
2. Move `FactorParameterResolver` to `experiment.engine.shared`
3. Move `FactorValuesResolver` to `experiment.engine.shared`
4. Move `FactorValuesInitializer` to `experiment.engine.shared`
5. Make package-private (not nested private)

**Files Created**:
```
experiment/engine/shared/CaptorParameterResolver.java
experiment/engine/shared/FactorParameterResolver.java
experiment/engine/shared/FactorValuesResolver.java
experiment/engine/shared/FactorValuesInitializer.java
```

**Tests**: Covered by existing integration tests

**Estimated Effort**: ~1 hour

---

### Phase 7: Clean Up ExperimentExtension

**Goal**: Remove dead code and verify thin coordinator.

**Tasks**:
1. Remove mode-specific methods from `ExperimentExtension`
2. Remove nested record/class definitions
3. Remove the "god record" `ExperimentConfig` (replaced by sealed interface)
4. Verify extension is ~200-300 lines
5. Update Javadoc

**Verification**:
- `ExperimentExtension` contains no switch/if on mode
- All mode-specific logic is in strategy packages
- Full test suite passes

**Estimated Effort**: ~1 hour

---

## Summary

| Phase | Description | New Files | Est. Effort |
|-------|-------------|-----------|-------------|
| 1 | Package structure and interfaces | 6 | 1 hour |
| 2 | Extract MeasureStrategy | 5 | 3 hours |
| 3 | Extract ExploreStrategy | 4 | 3 hours |
| 4 | Align OptimizeStrategy | 3 | 2 hours |
| 5 | Extract shared helpers | 4 | 3 hours |
| 6 | Extract parameter resolvers | 4 | 1 hour |
| 7 | Clean up ExperimentExtension | 0 | 1 hour |
| **Total** | | **~26** | **~14 hours** |

## Expected Final State

| Metric | Before | After |
|--------|--------|-------|
| Lines in ExperimentExtension | 1,575 | ~200-300 |
| Mode-specific branching in coordinator | 5+ locations | 0 |
| Nested classes in ExperimentExtension | 8 | 0 |
| Mode packages | 1 (optimize) | 3 (measure, explore, optimize) |
| Testable strategy classes | 0 | 3 |

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing experiment behavior | Run full test suite after each phase |
| Store key conflicts between strategies | Document store keys; use strategy-prefixed keys if needed |
| Parameter resolver ordering | Strategies control their own `getAdditionalExtensions()` order |
| Shared helper dependencies | Extract helpers after strategies are working |

## Execution Strategy

1. **One phase per commit/PR** - small, reviewable changes
2. **Tests pass after each phase** - no broken windows
3. **Strategies working before helpers extracted** - prove the pattern first
4. **ExperimentExtension shrinks incrementally** - visible progress

---

## Appendix: Current Mode Branching Points

For reference, these are the locations in the current `ExperimentExtension` that branch on mode:

| Line | Code | Issue |
|------|------|-------|
| 135-139 | `switch (config.mode())` | Dispatches to mode-specific context providers |
| 740-744 | `if (mode == EXPLORE)` | Dispatches to mode-specific interceptors |
| 145-163 | `ExperimentConfig` record | Union type with null fields per mode |
| 247-255 | `switch (config.mode())` | Computes total samples differently |
| 1325-1395 | Invocation context records | Mode-specific but not in mode packages |

All of these will be eliminated by the strategy pattern.
