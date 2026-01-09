# PUnit Development Plan

This document is the source of truth for PUnit's phased development. It consolidates the core probabilistic testing framework and the experiment extension into a single comprehensive plan.

---

## Overview

PUnit development is organized into three major tracks:

| Track | Description | Phases |
|-------|-------------|--------|
| **Core Framework** | Foundational `@ProbabilisticTest` infrastructure | C1–C8 |
| **Experiment Extension** | `@Experiment`, baselines, specifications | E1–E8 |
| **Enhancements** | Statistical improvements, prompt refinement, tooling | A, B, C, D |

---

## Track 1: Core Probabilistic Testing Framework (C1–C8)

These phases establish the foundational `@ProbabilisticTest` annotation and supporting infrastructure.

### Phase C1: Core Framework (MVP)

**Status**: ✅ Complete

**Deliverables**:
1. `@ProbabilisticTest` annotation with `samples` and `minPassRate`
2. `ProbabilisticTestExtension` implementing `TestTemplateInvocationContextProvider`
3. `SampleResultAggregator` with basic pass/fail counting
4. `InvocationInterceptor` to catch and record assertion failures
5. `FinalVerdictDecider` with simple pass rate comparison
6. Basic failure message with statistics
7. `TestReporter` integration for structured output

**Estimated Effort**: 2-3 days

---

### Phase C2: Early Termination

**Status**: ✅ Complete

**Deliverables**:
1. `EarlyTerminationEvaluator` with impossibility check
2. Stream short-circuiting in invocation provider
3. `TerminationReason` enum and reporting integration
4. Update failure message to include termination cause

**Estimated Effort**: 1-2 days

---

### Phase C3: Configuration System

**Status**: ✅ Complete

**Deliverables**:
1. `ConfigurationResolver` with precedence logic
2. System property support (`punit.samples`, `punit.minPassRate`)
3. Environment variable support
4. `punit.samplesMultiplier` for scaling
5. Validation and error messages for invalid configurations

**Estimated Effort**: 1 day

---

### Phase C4: Method-Level Cost Budget & Time Limits

**Status**: ✅ Complete

**Deliverables**:
1. `CostBudgetMonitor` implementation (wall-clock time + token accumulation)
2. `timeBudgetMs` annotation parameter
3. `tokenCharge` and `tokenBudget` annotation parameters
4. `TokenChargeRecorder` interface and `DefaultTokenChargeRecorder` implementation
5. JUnit 5 `ParameterResolver` for injecting `TokenChargeRecorder` into test methods
6. `onBudgetExhausted` behavior options
7. Static mode: Pre-sample budget checks integrated into sample loop
8. Dynamic mode: Post-sample token accumulation with recorder
9. Mode detection logic (check for TokenChargeRecorder parameter)
10. Reporting integration for budget-related termination

**Estimated Effort**: 3-4 days

---

### Phase C5: Budget Scopes (Class and Suite Levels)

**Status**: ✅ Complete

**Deliverables**:
1. `@ProbabilisticTestBudget` class-level annotation
2. `ProbabilisticTestBudgetExtension` for class-level budget management
3. `SharedBudgetMonitor` thread-safe implementation for shared budgets
4. `SuiteBudgetManager` singleton for suite-level state
5. Suite-level system property parsing (`punit.suite.*`)
6. Budget scope precedence logic (suite → class → method)
7. Consumption propagation to all active scopes
8. Extended `TerminationReason` enum with scoped values
9. Reporting integration for scoped budgets
10. Thread-safe budget tracking for parallel test execution

**Estimated Effort**: 3-4 days

---

### Phase C6: Enhanced Reporting & Diagnostics

**Status**: ✅ Complete

**Deliverables**:
1. `maxExampleFailures` parameter
2. Example failure collection in aggregator
3. Suppressed exceptions on final `AssertionError`
4. Enhanced failure message with example failures
5. Per-sample display names (e.g., "Sample 3/100")

**Estimated Effort**: 1 day

---

### Phase C7: Exception Handling & Edge Cases

**Status**: ✅ Complete

**Deliverables**:
1. `onException` parameter and handling logic
2. Parameter validation at discovery time
3. Edge case handling (samples=1, minPassRate=1.0, etc.)
4. Documentation of rounding behavior

**Estimated Effort**: 1 day

---

### Phase C8: Documentation & Polish

**Status**: ✅ Complete

**Deliverables**:
1. Javadoc for all public API
2. README with quick start guide
3. Examples in test sources
4. Migration guide (for teams adopting PUnit)

**Estimated Effort**: 1-2 days

---

**Track 1 Total**: 13-21 days (Complete)

---

## Track 2: Experiment Extension (E1–E8)

These phases extend PUnit with experiment support, baselines, and specifications.

### Phase E1: Core Use Case and Result Abstractions

**Status**: ✅ Complete

**Goals**: Establish foundational abstractions for use cases and results

**Deliverables**:
- `@UseCase` annotation
- `UseCaseResult` class with builder pattern
- `UseCaseContext` interface
- `UseCaseRegistry` for discovery and caching

**Dependencies**: None (greenfield)

**Estimated Effort**: 2-3 days

---

### Phase E2: Single-Config Experiment Mode

**Status**: ✅ Complete

**Goals**: Enable exploratory execution via `@Experiment`

**Deliverables**:
- `@Experiment` and `@ExperimentContext` annotations
- `ExperimentExtension` (JUnit 5 extension)
- `ExperimentResultAggregator`
- `EmpiricalBaselineGenerator`
- YAML serialization/deserialization

**Dependencies**: Phase E1

**Estimated Effort**: 4-5 days

---

### Phase E2b: Multi-Config Experiments

**Status**: ✅ Complete

**Goals**: Enable experiments with explicit `ExperimentConfig` lists

**Deliverables**:
- `@ExperimentDesign`, `@Config`, `@ExperimentGoal` annotations
- Sequential config execution with shared budget
- Goal-based early termination
- Aggregated `SUMMARY.yaml` report generation

**Dependencies**: Phase E2

**Estimated Effort**: 4-5 days

---

### Phase E2c: EXPLORE Mode (formerly Adaptive Experiments)

**Status**: 🔄 Superseded by PLAN-EXECUTION.md

**Original Goals**: Enable dynamic level generation for adaptive factors

**Superseded By**: The adaptive factors concept has been replaced by a simpler, JUnit-style parameterized approach:
- **EXPLORE mode**: Experiment mode for comparing multiple configurations
- **FactorSource**: JUnit-style `@MethodSource` for defining factor combinations
- **Two-phase workflow**: Quick pass (1 sample/config) → detailed comparison (N samples/config)

See `plan/PLAN-EXECUTION.md` for the current design.

**Dependencies**: Phase E2b

**Estimated Effort**: 3-4 days (simpler than original adaptive approach)

---

### Phase E3: Specification Representation and Registry

**Status**: ✅ Complete

**Goals**: Define specification data model and resolution

**Deliverables**:
- `ExecutionSpecification` model class
- `SpecificationRegistry` for loading and resolving specs
- YAML format for specifications
- `SuccessCriteria` expression parsing

**Dependencies**: Phase E1

**Estimated Effort**: 3-4 days

---

### Phase E4: Spec-Driven Probabilistic Tests

**Status**: ✅ Complete

**Goals**: Extend `@ProbabilisticTest` to consume specifications

**Deliverables**:
- Add `spec` and `useCase` attributes
- `SuccessCriteria` evaluation integration
- Baseline-spec drift detection
- Provenance reporting

**Dependencies**: Phases E1, E2, E3

**Estimated Effort**: 4-5 days

---

### Phase E5: Pluggable Backend Infrastructure

**Status**: ✅ Complete

**Goals**: Establish SPI for experiment backends

**Deliverables**:
- `ExperimentBackend` SPI interface
- `ExperimentBackendRegistry` with ServiceLoader discovery
- Generic backend implementation

**Dependencies**: Phase E2

**Estimated Effort**: 2-3 days

---

### Phase E6: LLM Backend Extension (llmx)

**Status**: ✅ Complete

**Goals**: Implement LLM-specific backend as reference implementation

**Deliverables**:
- `org.javai.punit.llmx` package
- `LlmExperimentBackend`, `LlmUseCaseContext`
- Common model/temperature presets
- EXPLORE mode factor sources for LLM configurations (model, temperature, prompt variants)

**Dependencies**: Phases E5, E2c

**Estimated Effort**: 4-5 days

---

### Phase E7: Canonical Flow Examples

**Status**: ✅ Complete

**Goals**: Demonstrate complete canonical flow through working examples

**Deliverables**:
- End-to-end examples in test suite
- Single-config, multi-config, and adaptive experiment examples
- Mock LLM client for testing

**Dependencies**: Phases E4, E6, E2c

**Estimated Effort**: 3-4 days

---

### Phase E8: Documentation, Migration, and Guardrails

**Status**: ✅ Complete

**Goals**: Complete documentation and governance mechanisms

**Deliverables**:
- Javadoc for all public APIs
- User guide and migration guide
- Complete governance warnings/errors
- Edge case testing

**Dependencies**: Phases E1-E7

**Estimated Effort**: 3-4 days

---

**Track 2 Total**: 35-44 days (Complete)

---

## Track 3: Enhancements (Phases A, B, C)

These phases introduce advanced capabilities for cost tracking, prompt refinement, and statistical threshold derivation.

### Phase A: Core Statistical and Cost Enhancements

**Status**: 📋 Planned

**Priority**: P1

**Goals**: Improve cost tracking and statistical guidance

**Deliverables**:

#### A.1 Input/Output Token Split

Enhanced `CostSummary` record:

```java
public record CostSummary(
    long totalTimeMs,
    long avgTimePerSampleMs,
    long totalTokens,
    long avgTokensPerSample,
    long totalInputTokens,          // NEW
    long totalOutputTokens,         // NEW
    long avgInputTokensPerSample,   // NEW
    long avgOutputTokensPerSample,  // NEW
    long apiCallCount               // NEW
) {}
```

#### A.2 Token Estimation

```java
public interface TokenEstimator {
    long estimateInputTokens(String text);
    long estimateOutputTokens(String text);
    
    static TokenEstimator forModel(String modelName) { ... }
}
```

With `BasicCostEstimator` fallback using word-count heuristics (~1.3 tokens per word).

#### A.3 Sample Size Advisory

Baseline output includes guidance on samples needed for various precision levels.

#### A.4 Stability-Based Early Termination

```java
@Experiment(
    useCase = "usecase.json.generation",
    samples = 1000,
    stabilityThreshold = 0.02  // Stop when CI width < 2%
)
```

**Dependencies**: Phase E2

**Estimated Effort**: 3-4 days

---

### Phase B: Adaptive Prompt Refinement

**Status**: 📋 Planned

**Priority**: P2

**Goals**: Enable automated prompt improvement through LLM-assisted refinement

**Deliverables**:

#### B.1 PromptContributor Interface

```java
public interface PromptContributor {
    String getSystemMessage();
    default List<Example> getExamples() { return List.of(); }
    default Optional<String> getUserMessageTemplate() { return Optional.empty(); }
    
    record Example(String userMessage, String assistantResponse) {}
}
```

#### B.2 Failure Categorization SPI

```java
@FunctionalInterface
public interface FailureCategorizer {
    String categorize(UseCaseResult result);
}
```

#### B.3 @AdaptivePromptExperiment Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdaptivePromptExperiment {
    Class<? extends PromptContributor> promptContributor();
    int samplesPerIteration() default 50;
    int maxIterations() default 10;
    double targetSuccessRate() default Double.NaN;
    Class<? extends FailureCategorizer> failureCategorizer();
    String refinementModel() default "";
    String refinementPromptTemplate() default "";
    String[] refinableComponents() default {};
}
```

#### B.4 Refinement Loop Orchestration

1. Call PromptContributor for initial components
2. Run N samples, collect results
3. Apply failure categorization
4. Check goal; if not met, refine via LLM
5. Loop until goal met or limit reached

**Dependencies**: Phases E2c, E6, A

**Estimated Effort**: 5-6 days

---

### Phase C: Statistics Engine and Operational Approaches

**Status**: 📋 Planned

**Priority**: P1

**Goals**: 
- Implement an isolated statistics engine with professional-grade nomenclature
- Support all three operational approaches for threshold derivation
- Integrate statistics into the probabilistic test driver for pass/fail determination
- Produce qualified test reports with full statistical context

---

#### C.1 Problem Statement

An experiment runs 1000 samples and observes 95.1% success rate. If the regression test runs only 100 samples with a 95.1% threshold, normal sampling variance will cause false failures ~50% of the time.

**Solution**: A statistics engine that:
1. Derives thresholds accounting for test sample variance
2. Computes required sample sizes for desired power
3. Reports confidence levels for observed results

---

#### C.2 punit-statistics Module

Per Design Principle 1.6, an isolated module with:
- **No dependencies** on punit-core, punit-experiment, or JUnit
- **Only dependencies**: Java standard library + Apache Commons Statistics
- **Comprehensive unit tests** with worked examples using real-world variable names
- **Code readable by professional statisticians** (standard terminology)

**Package structure**:

```
org.javai.punit.statistics/
├── BinomialProportionEstimator.java    # Point estimates and CIs
├── ThresholdDeriver.java               # Derive threshold from baseline
├── SampleSizeCalculator.java           # Power analysis for sample size
├── ConfidenceCalculator.java           # Implied confidence for given threshold
├── TestVerdictEvaluator.java           # Pass/fail with statistical context
└── model/
    ├── ProportionEstimate.java         # p̂, n, CI
    ├── DerivedThreshold.java           # Threshold with derivation metadata
    ├── SampleSizeRequirement.java      # Required n with power context
    ├── VerdictWithConfidence.java      # Pass/fail + statistical qualification
    └── OperationalApproach.java        # Enum: SAMPLE_SIZE_FIRST, CONFIDENCE_FIRST, THRESHOLD_FIRST
```

---

#### C.3 Core Statistical Components

**C.3.1 BinomialProportionEstimator**

Wraps Apache Commons Statistics for binomial proportion analysis:

```java
public class BinomialProportionEstimator {
    
    /**
     * Compute point estimate and confidence interval for a proportion.
     * Uses Wilson score interval (preferred for all sample sizes).
     */
    public ProportionEstimate estimate(int successes, int trials, double confidenceLevel) {
        double pHat = (double) successes / trials;
        double z = zScoreForConfidence(confidenceLevel);
        
        // Wilson score interval
        double denominator = 1 + z * z / trials;
        double center = (pHat + z * z / (2 * trials)) / denominator;
        double margin = z * Math.sqrt(pHat * (1 - pHat) / trials + z * z / (4 * trials * trials)) / denominator;
        
        return new ProportionEstimate(pHat, trials, center - margin, center + margin, confidenceLevel);
    }
    
    /**
     * Compute one-sided lower bound (for threshold derivation).
     * Critical for detecting degradation without false alarms.
     */
    public double lowerBound(int successes, int trials, double confidenceLevel) {
        // One-sided: use z for (1 - alpha) not (1 - alpha/2)
        // Special handling for p̂ = 1 (zero failures case)
        ...
    }
    
    private double zScoreForConfidence(double confidence) {
        // Uses Apache Commons Statistics NormalDistribution
        return NormalDistribution.of(0, 1).inverseCumulativeProbability(confidence);
    }
}
```

**C.3.2 ThresholdDeriver**

Derives pass/fail threshold for a given operational approach:

```java
public class ThresholdDeriver {
    
    private final BinomialProportionEstimator estimator;
    
    /**
     * Sample-Size-First: Given test samples and desired confidence,
     * derive the threshold that achieves that confidence.
     */
    public DerivedThreshold deriveSampleSizeFirst(
            int baselineSamples,
            int baselineSuccesses,
            int testSamples,
            double thresholdConfidence) {
        
        double baselineRate = (double) baselineSuccesses / baselineSamples;
        double threshold = estimator.lowerBound(baselineSuccesses, baselineSamples, thresholdConfidence);
        
        // Adjust for test sample variance if baseline much larger than test
        // (more sophisticated: use predictive interval)
        
        return new DerivedThreshold(
            threshold,
            OperationalApproach.SAMPLE_SIZE_FIRST,
            new DerivationContext(baselineRate, baselineSamples, testSamples, thresholdConfidence)
        );
    }
    
    /**
     * Threshold-First: Given explicit threshold, compute implied confidence.
     * Warns if confidence is too low (high false positive risk).
     */
    public DerivedThreshold deriveThresholdFirst(
            int baselineSamples,
            int baselineSuccesses,
            int testSamples,
            double explicitThreshold) {
        
        // Compute what confidence level this threshold implies
        double impliedConfidence = computeImpliedConfidence(
            baselineSuccesses, baselineSamples, testSamples, explicitThreshold);
        
        boolean isStatisticallySound = impliedConfidence >= 0.80;
        
        return new DerivedThreshold(
            explicitThreshold,
            OperationalApproach.THRESHOLD_FIRST,
            new DerivationContext(baselineRate, baselineSamples, testSamples, impliedConfidence),
            isStatisticallySound
        );
    }
}
```

**C.3.3 SampleSizeCalculator**

Power analysis for Confidence-First approach:

```java
public class SampleSizeCalculator {
    
    /**
     * Confidence-First: Given desired confidence, effect size, and power,
     * compute required sample size.
     * 
     * @param baselineRate Observed success rate from experiment (p₀)
     * @param minDetectableEffect Degradation to detect (e.g., 0.05 = 5% drop)
     * @param confidence Desired confidence level (1 - α)
     * @param power Desired power (1 - β)
     * @return Required sample size
     */
    public SampleSizeRequirement calculateForPower(
            double baselineRate,
            double minDetectableEffect,
            double confidence,
            double power) {
        
        double p0 = baselineRate;                      // Null hypothesis (no degradation)
        double p1 = baselineRate - minDetectableEffect; // Alternative (degraded)
        
        double zAlpha = zScoreForConfidence(confidence);
        double zBeta = zScoreForConfidence(power);
        
        // Sample size formula for one-sided binomial test
        double numerator = Math.pow(zAlpha * Math.sqrt(p0 * (1 - p0)) 
                                  + zBeta * Math.sqrt(p1 * (1 - p1)), 2);
        double denominator = Math.pow(p0 - p1, 2);
        
        int requiredSamples = (int) Math.ceil(numerator / denominator);
        
        return new SampleSizeRequirement(
            requiredSamples,
            confidence,
            power,
            minDetectableEffect,
            p0,
            p1
        );
    }
}
```

**C.3.4 TestVerdictEvaluator**

Determines pass/fail with full statistical context:

```java
public class TestVerdictEvaluator {
    
    /**
     * Evaluate test results against threshold, producing qualified verdict.
     */
    public VerdictWithConfidence evaluate(
            int testSuccesses,
            int testSamples,
            DerivedThreshold threshold) {
        
        double observedRate = (double) testSuccesses / testSamples;
        boolean passed = observedRate >= threshold.value();
        
        // Compute false positive probability if test failed
        double falsePositiveProbability = passed ? 0.0 
            : computeFalsePositiveProbability(threshold);
        
        return new VerdictWithConfidence(
            passed,
            observedRate,
            threshold,
            falsePositiveProbability,
            generateInterpretation(passed, observedRate, threshold)
        );
    }
    
    private String generateInterpretation(boolean passed, double observed, DerivedThreshold threshold) {
        if (passed) {
            return String.format(
                "Observed %.1f%% ≥ %.1f%% threshold. No evidence of degradation.",
                observed * 100, threshold.value() * 100);
        } else {
            return String.format(
                "Observed %.1f%% < %.1f%% threshold. " +
                "This indicates degradation with %.0f%% confidence. " +
                "There is a %.1f%% probability this is a false positive.",
                observed * 100, threshold.value() * 100,
                threshold.context().confidence() * 100,
                (1 - threshold.context().confidence()) * 100);
        }
    }
}
```

---

#### C.4 Integration with ProbabilisticTestExtension

The existing `ProbabilisticTestExtension` must be enhanced to:

1. **Load specification** (raw baseline data)
2. **Read annotation parameters** (operational approach inputs)
3. **Determine operational approach** based on which parameters are set
4. **Invoke statistics engine** to derive runtime threshold
5. **Execute samples** and aggregate results
6. **Invoke verdict evaluator** for qualified pass/fail
7. **Report with statistical context**

```java
public class ProbabilisticTestExtension implements TestTemplateInvocationContextProvider {
    
    private final ThresholdDeriver thresholdDeriver;
    private final SampleSizeCalculator sampleSizeCalculator;
    private final TestVerdictEvaluator verdictEvaluator;
    
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(...) {
        
        // 1. Load spec if present
        ExecutionSpecification spec = loadSpec(annotation.spec());
        
        // 2. Determine operational approach and derive threshold
        DerivedThreshold threshold = deriveThreshold(spec, annotation);
        
        // 3. Determine sample count (may be computed for Confidence-First)
        int sampleCount = determineSampleCount(spec, annotation, threshold);
        
        // 4. Generate invocation contexts for each sample
        return IntStream.range(0, sampleCount)
            .mapToObj(i -> createInvocationContext(i, threshold, spec));
    }
    
    private DerivedThreshold deriveThreshold(ExecutionSpecification spec, ProbabilisticTest annotation) {
        OperationalApproach approach = determineApproach(annotation);
        
        return switch (approach) {
            case SAMPLE_SIZE_FIRST -> thresholdDeriver.deriveSampleSizeFirst(
                spec.getBaselineSamples(),
                spec.getBaselineSuccesses(),
                annotation.samples(),
                annotation.thresholdConfidence()
            );
            
            case CONFIDENCE_FIRST -> {
                SampleSizeRequirement req = sampleSizeCalculator.calculateForPower(
                    spec.getObservedRate(),
                    annotation.minDetectableEffect(),
                    annotation.confidence(),
                    annotation.power()
                );
                yield thresholdDeriver.deriveSampleSizeFirst(
                    spec.getBaselineSamples(),
                    spec.getBaselineSuccesses(),
                    req.requiredSamples(),
                    annotation.confidence()
                );
            }
            
            case THRESHOLD_FIRST -> thresholdDeriver.deriveThresholdFirst(
                spec.getBaselineSamples(),
                spec.getBaselineSuccesses(),
                annotation.samples(),
                annotation.minPassRate()
            );
        };
    }
    
    // After all samples complete:
    private void evaluateAndReport(SampleResultAggregator aggregator, DerivedThreshold threshold) {
        VerdictWithConfidence verdict = verdictEvaluator.evaluate(
            aggregator.getSuccessCount(),
            aggregator.getTotalCount(),
            threshold
        );
        
        if (!verdict.passed()) {
            // Report qualified failure
            reportQualifiedFailure(verdict);
        }
    }
}
```

---

#### C.5 Qualified Test Reporting

Test reports include full statistical context:

**Pass Report**:
```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.generatesValidJson                     │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                  │
│ Approach: Sample-Size-First (100 samples, 95% confidence)       │
├─────────────────────────────────────────────────────────────────┤
│ Observed: 94/100 = 94.0%                                        │
│ Threshold: 91.6% (derived from 95.1% baseline via Wilson bound) │
├─────────────────────────────────────────────────────────────────┤
│ INTERPRETATION:                                                 │
│   Observed 94.0% ≥ 91.6% threshold.                             │
│   No evidence of degradation from baseline.                     │
└─────────────────────────────────────────────────────────────────┘
```

**Fail Report**:
```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.generatesValidJson                     │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                  │
│ Approach: Sample-Size-First (100 samples, 95% confidence)       │
├─────────────────────────────────────────────────────────────────┤
│ Observed: 87/100 = 87.0%                                        │
│ Threshold: 91.6% (derived from 95.1% baseline via Wilson bound) │
│ Shortfall: 4.6% below threshold                                 │
├─────────────────────────────────────────────────────────────────┤
│ STATISTICAL CONTEXT:                                            │
│   Method: Wilson one-sided lower bound                          │
│   Confidence: 95%                                               │
│   False positive probability: 5%                                │
├─────────────────────────────────────────────────────────────────┤
│ INTERPRETATION:                                                 │
│   This result indicates DEGRADATION from the baseline.          │
│   There is a 5% probability this failure is due to sampling     │
│   variance rather than actual system degradation.               │
└─────────────────────────────────────────────────────────────────┘
```

---

#### C.6 Model Records

```java
public record ProportionEstimate(
    double pointEstimate,    // p̂
    int sampleSize,          // n
    double lowerBound,       // CI lower
    double upperBound,       // CI upper
    double confidenceLevel   // e.g., 0.95
) {}

public record DerivedThreshold(
    double value,                    // The threshold itself
    OperationalApproach approach,    // How it was derived
    DerivationContext context,       // Inputs used
    boolean isStatisticallySound     // Warning flag for Threshold-First
) {}

public record DerivationContext(
    double baselineRate,
    int baselineSamples,
    int testSamples,
    double confidence
) {}

public record SampleSizeRequirement(
    int requiredSamples,
    double confidence,
    double power,
    double minDetectableEffect,
    double nullRate,         // p₀ (baseline)
    double alternativeRate   // p₁ (degraded)
) {}

public record VerdictWithConfidence(
    boolean passed,
    double observedRate,
    DerivedThreshold threshold,
    double falsePositiveProbability,
    String interpretation
) {}

public enum OperationalApproach {
    SAMPLE_SIZE_FIRST,   // Cost-driven: fix samples, derive threshold
    CONFIDENCE_FIRST,    // Quality-driven: fix confidence, derive samples
    THRESHOLD_FIRST      // Baseline-anchored: fix threshold, derive confidence
}
```

---

#### C.7 Unit Tests with Worked Examples

Per Design Principle 1.6, unit tests use real-world variable names:

```java
@Test
@DisplayName("Derive threshold for 100-sample test from 1000-sample baseline at 95% confidence")
void deriveThresholdSampleSizeFirst() {
    int baselineSamples = 1000;
    int baselineSuccesses = 951;
    double baselineRate = 0.951;
    
    int testSamples = 100;
    double thresholdConfidence = 0.95;
    
    DerivedThreshold result = deriver.deriveSampleSizeFirst(
        baselineSamples, baselineSuccesses, testSamples, thresholdConfidence);
    
    // Threshold should be lower than baseline rate to account for sampling variance
    assertThat(result.value()).isLessThan(baselineRate);
    
    // With 95% confidence and 100 samples, expect threshold around 91-92%
    assertThat(result.value()).isBetween(0.90, 0.93);
    
    assertThat(result.approach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
    assertThat(result.isStatisticallySound()).isTrue();
}

@Test
@DisplayName("Calculate required samples for 99% confidence, 5% effect, 80% power")
void calculateSampleSizeConfidenceFirst() {
    double baselineRate = 0.95;
    double minDetectableEffect = 0.05;  // Detect 5% degradation
    double confidence = 0.99;
    double power = 0.80;
    
    SampleSizeRequirement result = calculator.calculateForPower(
        baselineRate, minDetectableEffect, confidence, power);
    
    // With these parameters, expect ~200-300 samples required
    assertThat(result.requiredSamples()).isBetween(150, 350);
    assertThat(result.alternativeRate()).isEqualTo(0.90);  // 95% - 5%
}
```

---

#### C.8 Narrative Test Class: Statistical Companion Validation

**Purpose**: Build trust with users and prospective users by providing a test class that mirrors the worked examples in the [STATISTICAL-COMPANION.md](../docs/STATISTICAL-COMPANION.md) document.

**Rationale**: After a qualified statistician reviews the STATISTICAL-COMPANION document, they can view this test class and see the **exact same logic** expressed in Java code. This one-to-one correspondence between documentation and executable code demonstrates that PUnit's implementation faithfully follows the documented statistical methods.

**Location**: `src/test/java/org/javai/punit/statistics/StatisticalCompanionValidationTest.java`

**Structure**: Three test cases, each written in a "narrative" style with extensive commentary interspersed with code—readable like a Jupyter notebook.

---

**Test Case 1: Baseline Estimation and Threshold Derivation**

*Mirrors: STATISTICAL-COMPANION.md Sections 2 and 3*

```java
/**
 * STATISTICAL COMPANION VALIDATION - TEST CASE 1
 * ===============================================
 * 
 * This test validates the core flow from experimental observation to 
 * test threshold derivation, as documented in Sections 2 and 3 of the
 * Statistical Companion.
 * 
 * SCENARIO: JSON Generation Use Case
 * ----------------------------------
 * A customer service system uses an LLM to generate JSON responses.
 * An experiment with n = 1000 trials yielded k = 951 successes.
 * We need to derive an appropriate threshold for a regression test
 * that will run only 100 samples.
 * 
 * REFERENCE: docs/STATISTICAL-COMPANION.md, Sections 2-3
 */
@Test
@DisplayName("Companion §2-3: Baseline estimation to threshold derivation")
void companionCase1_BaselineToThreshold() {
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 2.1: Point Estimation (Companion §2.1)
    // ══════════════════════════════════════════════════════════════════
    //
    // The experiment observed 951 successes out of 1000 trials.
    // The maximum likelihood estimate (MLE) of the true success rate is:
    //
    //   p̂ = k/n = 951/1000 = 0.951
    //
    
    int experimentTrials = 1000;
    int experimentSuccesses = 951;
    
    double pointEstimate = (double) experimentSuccesses / experimentTrials;
    
    assertThat(pointEstimate)
        .as("Point estimate p̂ = k/n")
        .isEqualTo(0.951);
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 2.2: Standard Error (Companion §2.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // The standard error quantifies uncertainty in our point estimate:
    //
    //   SE = √(p̂(1-p̂)/n) = √(0.951 × 0.049 / 1000) ≈ 0.00683
    //
    
    double standardError = estimator.standardError(experimentSuccesses, experimentTrials);
    
    assertThat(standardError)
        .as("Standard error SE = √(p̂(1-p̂)/n)")
        .isCloseTo(0.00683, within(0.0001));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 2.3: Wilson Score Confidence Interval (Companion §2.3.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // PUnit uses the Wilson score interval, which is more accurate than
    // the normal approximation for all sample sizes.
    //
    // For 95% confidence (z = 1.96):
    //   Lower ≈ 0.937
    //   Upper ≈ 0.963
    //
    
    ProportionEstimate estimate = estimator.estimate(
        experimentSuccesses, experimentTrials, 0.95);
    
    assertThat(estimate.lowerBound())
        .as("Wilson 95% CI lower bound")
        .isCloseTo(0.937, within(0.002));
    
    assertThat(estimate.upperBound())
        .as("Wilson 95% CI upper bound")
        .isCloseTo(0.963, within(0.002));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 3: Threshold Derivation (Companion §3.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // For a test running only 100 samples, we need a threshold that
    // accounts for increased sampling variance. Using the Wilson
    // one-sided lower bound at 95% confidence:
    //
    //   threshold ≈ 0.916
    //
    // This means: if the true rate is still 0.951, a test with 100
    // samples has only a 5% chance of observing a rate below 0.916.
    //
    
    int testSamples = 100;
    double thresholdConfidence = 0.95;
    
    DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
        experimentTrials, experimentSuccesses, testSamples, thresholdConfidence);
    
    assertThat(threshold.value())
        .as("Derived threshold for 100-sample test at 95% confidence")
        .isCloseTo(0.916, within(0.005));
    
    assertThat(threshold.approach())
        .isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
    
    // ══════════════════════════════════════════════════════════════════
    // INTERPRETATION
    // ══════════════════════════════════════════════════════════════════
    //
    // Starting from 951/1000 observed successes (95.1%), we derived a
    // threshold of 91.6% for a 100-sample test at 95% confidence.
    //
    // The 3.5% gap (95.1% → 91.6%) accounts for:
    //   1. Uncertainty in the baseline estimate
    //   2. Increased variance with smaller test sample
    //   3. Desired 95% confidence against false positives
    //
}
```

---

**Test Case 2: The Perfect Baseline Problem**

*Mirrors: STATISTICAL-COMPANION.md Section 4*

```java
/**
 * STATISTICAL COMPANION VALIDATION - TEST CASE 2
 * ===============================================
 * 
 * This test validates PUnit's handling of the "perfect baseline" edge case,
 * where an experiment observes 100% success rate.
 * 
 * SCENARIO: Payment Gateway Integration
 * -------------------------------------
 * Testing a highly reliable payment gateway API. An experiment with
 * n = 1000 trials yields k = 1000 successes (zero failures).
 * 
 * PROBLEM: Standard methods collapse when p̂ = 1:
 *   - Standard error = √(1 × 0 / n) = 0
 *   - Naive threshold = 1.0 (one failure fails the test!)
 * 
 * SOLUTION: Wilson lower bound remains valid for p̂ = 1.
 * 
 * REFERENCE: docs/STATISTICAL-COMPANION.md, Section 4
 */
@Test
@DisplayName("Companion §4: Perfect baseline problem (p̂ = 1)")
void companionCase2_PerfectBaseline() {
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 4.1: The Problem (Companion §4.1)
    // ══════════════════════════════════════════════════════════════════
    //
    // Experiment: 1000 trials, 1000 successes, zero failures.
    // Observed rate: p̂ = 1.0 (100%)
    //
    // This does NOT mean the system is perfect. It means:
    //   "Zero failures occurred in 1000 trials"
    //
    
    int experimentTrials = 1000;
    int experimentSuccesses = 1000;
    
    double observedRate = (double) experimentSuccesses / experimentTrials;
    assertThat(observedRate).isEqualTo(1.0);
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 4.2: Why Standard Methods Fail (Companion §4.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // Standard error collapses to zero:
    //   SE = √(1.0 × 0.0 / 1000) = 0
    //
    // This makes normal-based thresholds degenerate to 1.0,
    // meaning ANY single failure would fail the test.
    //
    
    double naiveStandardError = Math.sqrt(1.0 * 0.0 / experimentTrials);
    assertThat(naiveStandardError).isEqualTo(0.0);
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 4.4: Wilson Lower Bound (Companion §4.4)
    // ══════════════════════════════════════════════════════════════════
    //
    // The Wilson score interval remains valid when p̂ = 1.
    // For 95% confidence with n = 1000:
    //
    //   p_lower ≈ 0.9963 (i.e., 99.63%)
    //
    // This is sensible: we're 95% confident the true rate is at least
    // 99.63%, even though we observed 100%.
    //
    
    double wilsonLowerBound = estimator.lowerBound(
        experimentSuccesses, experimentTrials, 0.95);
    
    assertThat(wilsonLowerBound)
        .as("Wilson lower bound when p̂ = 1")
        .isCloseTo(0.9963, within(0.001));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 4.4.2: Threshold for Test (Companion §4.4.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // For a 100-sample test at 95% confidence:
    //   threshold ≈ 0.964 (96.4%)
    //
    // This allows up to 3-4 failures in 100 samples without triggering
    // a false positive.
    //
    
    int testSamples = 100;
    
    DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
        experimentTrials, experimentSuccesses, testSamples, 0.95);
    
    assertThat(threshold.value())
        .as("Threshold derived from perfect baseline")
        .isBetween(0.95, 0.98);
    
    // ══════════════════════════════════════════════════════════════════
    // INTERPRETATION
    // ══════════════════════════════════════════════════════════════════
    //
    // Even with a "perfect" 100% baseline, PUnit correctly derives a
    // threshold below 1.0, allowing for sampling variance in the test.
    //
    // Key insight: Observing zero failures doesn't prove failures are
    // impossible—it provides statistical evidence about their rarity.
    //
}
```

---

**Test Case 3: Test Execution and Qualified Verdict**

*Mirrors: STATISTICAL-COMPANION.md Section 6*

```java
/**
 * STATISTICAL COMPANION VALIDATION - TEST CASE 3
 * ===============================================
 * 
 * This test validates the full cycle from threshold to test verdict,
 * including the qualified interpretation of results.
 * 
 * SCENARIO: Regression Test Failure
 * ---------------------------------
 * Baseline: 951/1000 (95.1%)
 * Derived threshold: 91.6% (at 95% confidence for 100 samples)
 * Test result: 87/100 (87.0%)
 * 
 * The test FAILS because 87% < 91.6%.
 * But what does this failure mean statistically?
 * 
 * REFERENCE: docs/STATISTICAL-COMPANION.md, Section 6
 */
@Test
@DisplayName("Companion §6: Test execution and qualified verdict")
void companionCase3_TestVerdictWithQualification() {
    
    // ══════════════════════════════════════════════════════════════════
    // SETUP: Derive threshold from baseline (recap of Case 1)
    // ══════════════════════════════════════════════════════════════════
    
    int baselineTrials = 1000;
    int baselineSuccesses = 951;
    int testSamples = 100;
    double confidenceLevel = 0.95;
    
    DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
        baselineTrials, baselineSuccesses, testSamples, confidenceLevel);
    
    assertThat(threshold.value()).isCloseTo(0.916, within(0.005));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 6.1: Test Execution (Companion §6.1)
    // ══════════════════════════════════════════════════════════════════
    //
    // The test runs 100 samples and observes 87 successes, 13 failures.
    // Observed rate: 87/100 = 87.0%
    //
    
    int testSuccesses = 87;
    int testFailures = 13;
    double observedRate = (double) testSuccesses / testSamples;
    
    assertThat(observedRate).isEqualTo(0.87);
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 6.2: Pass/Fail Decision (Companion §6.2)
    // ══════════════════════════════════════════════════════════════════
    //
    // Compare observed rate to threshold:
    //   87.0% < 91.6%  →  TEST FAILS
    //
    // Shortfall: 4.6% below threshold
    //
    
    VerdictWithConfidence verdict = verdictEvaluator.evaluate(
        testSuccesses, testSamples, threshold);
    
    assertThat(verdict.passed())
        .as("Test should FAIL when observed < threshold")
        .isFalse();
    
    assertThat(verdict.observedRate()).isEqualTo(0.87);
    
    double shortfall = threshold.value() - observedRate;
    assertThat(shortfall).isCloseTo(0.046, within(0.005));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 6.3: Statistical Qualification (Companion §6.3)
    // ══════════════════════════════════════════════════════════════════
    //
    // The failure is reported with statistical context:
    //
    //   "FAILED with 95% confidence"
    //
    // This means: if the true rate has NOT degraded from 95.1%, there
    // is only a 5% probability of observing a rate this low by chance.
    //
    // Conversely: there is a 5% false positive probability.
    //
    
    assertThat(verdict.falsePositiveProbability())
        .as("False positive probability = 1 - confidence")
        .isCloseTo(0.05, within(0.01));
    
    // ══════════════════════════════════════════════════════════════════
    // SECTION 6.4: Interpretation (Companion §6.4)
    // ══════════════════════════════════════════════════════════════════
    //
    // The verdict includes human-readable interpretation:
    //
    //   "This result indicates DEGRADATION from the baseline.
    //    There is a 5% probability this failure is due to sampling
    //    variance rather than actual system degradation."
    //
    
    assertThat(verdict.interpretation())
        .contains("degradation", "5%");
    
    // ══════════════════════════════════════════════════════════════════
    // INTERPRETATION
    // ══════════════════════════════════════════════════════════════════
    //
    // A test failure does NOT mean "definitely broken."
    // It means: "The observed behavior is statistically inconsistent
    // with the baseline at the configured confidence level."
    //
    // With 95% confidence:
    //   - If the system truly has not degraded, we expect at most a 5%
    //     chance of this failure occurring (false positive).
    //   - Repeated failures strengthen the evidence of real degradation.
    //
}
```

---

**Relationship to Unit Tests**

| Test Type | Purpose | Coverage |
|-----------|---------|----------|
| **Unit tests** (per component) | Comprehensive coverage of all edge cases | Every method, boundary condition |
| **Companion validation tests** | Trust building via 1:1 document correspondence | The 3 core worked examples |

Both test types are required. The companion validation tests are **in addition to** the comprehensive unit tests, not a replacement.

---

**Dependencies**: Phases E2, E3, E4, Apache Commons Statistics

**Estimated Effort**: 8-10 days (includes companion validation tests)

---

---

### Phase D: Baseline Review and Approval Workflow

**Status**: 📋 Planned

**Priority**: P1

**Goals**: Provide Gradle tasks for reviewing baselines and creating specifications

**Deliverables**:

#### D.1 `punitReview` Task

Display baseline information with computed statistics:

```bash
./gradlew punitReview --useCase=json.generation     # Display baseline
./gradlew punitReview --list-pending                # List unapproved baselines
```

Features:
- Display current baseline with raw data (samples, successes, rate)
- Compute and display 95% confidence interval on-the-fly
- Show history of previous baseline runs
- Compare current to previous baselines
- Identify pending approvals (baselines without specs)

#### D.2 `punitApprove` Task

Create specification from baseline (non-interactive):

```bash
./gradlew punitApprove --useCase=json.generation
./gradlew punitApprove --useCase=json.generation --notes="Reviewed after fix"
./gradlew punitApprove --useCase=json.generation --force  # New version
```

Features:
- Non-interactive (no stdin prompts) for CI/CD compatibility
- Auto-capture approval metadata (timestamp, user)
- Auto-increment version if spec exists
- Validate baseline exists before approval

#### D.3 Baseline History

Baseline files include history of previous runs:

```yaml
history:
  - generatedAt: 2026-01-05T10:00:00Z
    samples: 1000
    successes: 912
    observedRate: 0.912
  - generatedAt: 2026-01-03T09:15:00Z
    samples: 500
    successes: 471
    observedRate: 0.942
```

Features:
- Auto-append on each experiment run
- Configurable history limit (default 10)
- Enables trend analysis at review time

**Dependencies**: Phase E2

**Estimated Effort**: 3-4 days

---

**Track 3 Total**: 19-24 days (Planned)

---

## Phase Summary

| Track | Phase | Description | Status | Est. Days |
|-------|-------|-------------|--------|-----------|
| Core | C1 | Core Framework (MVP) | ✅ | 2-3 |
| Core | C2 | Early Termination | ✅ | 1-2 |
| Core | C3 | Configuration System | ✅ | 1 |
| Core | C4 | Method-Level Cost Budget | ✅ | 3-4 |
| Core | C5 | Budget Scopes | ✅ | 3-4 |
| Core | C6 | Enhanced Reporting | ✅ | 1 |
| Core | C7 | Exception Handling | ✅ | 1 |
| Core | C8 | Documentation | ✅ | 1-2 |
| Experiment | E1 | Use Case Abstractions | ✅ | 2-3 |
| Experiment | E2 | Single-Config Experiments | ✅ | 4-5 |
| Experiment | E2b | Multi-Config Experiments | ✅ | 4-5 |
| Experiment | E2c | Adaptive Experiments | ✅ | 5-6 |
| Experiment | E3 | Specification Registry | ✅ | 3-4 |
| Experiment | E4 | Spec-Driven Tests | ✅ | 4-5 |
| Experiment | E5 | Backend Infrastructure | ✅ | 2-3 |
| Experiment | E6 | LLM Backend (llmx) | ✅ | 4-5 |
| Experiment | E7 | Canonical Flow Examples | ✅ | 3-4 |
| Experiment | E8 | Documentation | ✅ | 3-4 |
| Enhancement | A | Cost/Statistical Enhancements | 📋 | 3-4 |
| Enhancement | B | Adaptive Prompt Refinement | 📋 | 5-6 |
| Enhancement | C | Statistics Engine & Operational Approaches | 📋 | 8-10 |
| Enhancement | D | Review & Approval Workflow | 📋 | 3-4 |

**Total Estimated Effort**: 67-91 days

---

## Recommended Execution Order

1. ✅ Complete Phases C1-C8 (Core Framework)
2. ✅ Complete Phases E1-E8 (Experiment Extension)
3. 📋 Phase D (review & approval workflow) — enables practical usage
4. 📋 Phase C (isolated statistics module + threshold derivation)
5. 📋 Phase A (cost/statistical enhancements)
6. 📋 Phase B (prompt refinement)

---

## Explicit Exclusions

| Feature | Reason |
|---------|--------|
| Dollar cost calculations | Framework provides tokens/time only |
| Automated config optimization | Decisions remain with humans |
| Cross-experiment state management | Each experiment is independent |
| Visualization/dashboards | Results via JUnit only |
| Retry orchestration | Application's responsibility |

---

## v1 Scope vs v2 Enhancements

### v1 Scope (Complete)

✅ `@ProbabilisticTest` annotation  
✅ `samples` and `minPassRate` parameters  
✅ Sequential sample execution  
✅ Early termination by impossibility  
✅ Wall-clock time budget (`timeBudgetMs`)  
✅ Static token budget (`tokenCharge`, `tokenBudget`)  
✅ Dynamic token charging via injectable `TokenChargeRecorder`  
✅ Budget scopes: method, class (`@ProbabilisticTestBudget`), and suite levels  
✅ System property / env var overrides (including suite-level budgets)  
✅ Structured reporting via `TestReporter`  
✅ Clear failure messages with statistics  
✅ Example failure capture  
✅ Basic exception handling policy  
✅ `@Experiment` and experiment infrastructure  
✅ `@UseCase` and use case abstractions  
✅ Empirical baselines and specifications  
✅ Backend SPI and `llmx` extension

### v2 Enhancements (Future)

⏳ **Parallel sample execution** (`parallelSamples` parameter)  
⏳ **Custom cost models** (SPI for money, API calls, dynamic token measurement)  
⏳ **Warm-up samples** (excluded from statistics)  
⏳ **Per-invocation timeout** (distinct from overall budget)  
⏳ **Retry policy** (retry failed samples N times before counting as failure)  
⏳ **Combined with @ParameterizedTest** (run probabilistic test for each parameter set)  
⏳ **Statistical confidence intervals** (report confidence bounds, not just point estimate)  
⏳ **Adaptive sampling** (stop early when statistically confident of pass/fail)  
⏳ **JUnit XML/HTML report integration** (custom reporter for richer output)  
⏳ **Flakiness tracking** (track pass rates over time across runs)  
⏳ **Gradle/Maven plugin** (configure defaults in build file)  
⏳ **Automatic token measurement** (SPI for intercepting LLM client calls)

---

*See [PLAN-99-DEVELOPMENT-STATUS.md](./PLAN-99-DEVELOPMENT-STATUS.md) for current implementation status.*

*[Back to Table of Contents](./DOC-00-TOC.md)*

