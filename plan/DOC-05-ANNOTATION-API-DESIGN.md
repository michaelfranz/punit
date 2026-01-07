# Annotation & API Design

This section defines the annotations and API classes introduced by the experiment extension.

## 4.1 New Annotations

### @UseCase

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    /** Unique identifier for this use case. */
    String value();
    
    /** Human-readable description. */
    String description() default "";
}
```

### @Experiment

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    String useCase();
    int samples() default 100;
    long timeBudgetMs() default 0;
    long tokenBudget() default 0;
    String baselineOutputDir() default "punit/baselines";
    boolean overwriteBaseline() default false;
}
```

### @ExperimentContext

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExperimentContexts.class)
public @interface ExperimentContext {
    String backend();
    String[] template() default {};
    @Deprecated String[] parameters() default {};
}
```

## 4.2 Extended @ProbabilisticTest

The `@ProbabilisticTest` annotation supports **three mutually exclusive operational approaches** for configuring test thresholds. At any given time, you control two of the three variables (sample size, confidence, threshold); the third is determined by statistics.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {
    
    // ═══════════════════════════════════════════════════════════════════
    // CORE CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════
    
    /** Reference to an execution specification. */
    String spec() default "";
    
    /** The use case ID to execute. */
    String useCase() default "";
    
    // ═══════════════════════════════════════════════════════════════════
    // APPROACH 1: SAMPLE-SIZE-FIRST (Cost-Driven)
    // Specify samples and thresholdConfidence → framework computes threshold
    // ═══════════════════════════════════════════════════════════════════
    
    /** Number of samples to execute. Used in Approach 1 and 3. */
    int samples() default 100;
    
    /** 
     * Confidence level for threshold derivation (0.0 to 1.0).
     * Used in Approach 1: combined with samples to compute minPassRate.
     * Default: 0.95 (95% confidence).
     */
    double thresholdConfidence() default 0.95;
    
    // ═══════════════════════════════════════════════════════════════════
    // APPROACH 2: CONFIDENCE-FIRST (Quality-Driven)
    // Specify confidence, minDetectableEffect, power → framework computes samples
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Required confidence level for the test result (0.0 to 1.0).
     * Used in Approach 2: combined with minDetectableEffect and power
     * to compute the required sample size.
     * When set (not NaN), enables Confidence-First mode.
     */
    double confidence() default Double.NaN;
    
    /**
     * Minimum degradation worth detecting (0.0 to 1.0).
     * Example: 0.05 means "detect a 5% drop from baseline".
     * Required when using Approach 2.
     */
    double minDetectableEffect() default Double.NaN;
    
    /**
     * Statistical power: probability of detecting a real degradation (0.0 to 1.0).
     * Example: 0.80 means "80% chance of catching a real degradation".
     * Required when using Approach 2.
     */
    double power() default Double.NaN;
    
    // ═══════════════════════════════════════════════════════════════════
    // APPROACH 3: THRESHOLD-FIRST (Baseline-Anchored)
    // Specify samples and minPassRate → framework computes implied confidence
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Explicit minimum pass rate threshold (0.0 to 1.0).
     * Used in Approach 3: when set explicitly with samples, the framework
     * computes the implied confidence and warns if statistically unsound.
     * Default: NaN (derive from spec or use other approach).
     */
    double minPassRate() default Double.NaN;
    
    // ═══════════════════════════════════════════════════════════════════
    // BUDGET CONTROLS
    // ═══════════════════════════════════════════════════════════════════
    
    /** Maximum time budget in milliseconds (0 = unlimited). */
    long timeBudgetMs() default 0;
    
    /** Maximum token budget (0 = unlimited). */
    long tokenBudget() default 0;
}
```

### Approach Selection Logic

The framework determines which approach is active based on which parameters are set:

| Parameters Set                                      | Approach                    | Framework Computes      |
|-----------------------------------------------------|-----------------------------|-------------------------|
| `samples` + `thresholdConfidence`                   | Sample-Size-First           | `minPassRate`           |
| `confidence` + `minDetectableEffect` + `power`      | Confidence-First            | `samples`               |
| `samples` + `minPassRate`                           | Threshold-First             | implied confidence      |

If conflicting parameters are specified (e.g., both `samples` and `confidence`), the framework issues a warning and falls back to a deterministic priority order.

### Usage Examples

**Approach 1: Sample-Size-First** — "We can afford 100 samples. What confidence does that give us?"

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    samples = 100,
    thresholdConfidence = 0.95
)
void testJsonGeneration() { ... }
```

**Approach 2: Confidence-First** — "We require 99% confidence. How many samples do we need?"

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    confidence = 0.99,
    minDetectableEffect = 0.05,
    power = 0.80
)
void testJsonGeneration() { ... }
```

**Approach 3: Threshold-First** — "We want to use the exact baseline rate as our threshold."

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    samples = 100,
    minPassRate = 0.951
)
void testJsonGeneration() { ... }
```

## 4.3 API Classes

### UseCaseResult

```java
public final class UseCaseResult {
    private final Map<String, Object> values;
    private final Instant timestamp;
    private final Duration executionTime;
    private final Map<String, Object> metadata;
    
    public static Builder builder() { ... }
    public <T> Optional<T> getValue(String key, Class<T> type) { ... }
    public boolean getBoolean(String key, boolean defaultValue) { ... }
    public int getInt(String key, int defaultValue) { ... }
    public double getDouble(String key, double defaultValue) { ... }
    public String getString(String key, String defaultValue) { ... }
    public Map<String, Object> getAllValues() { ... }
    public Map<String, Object> getAllMetadata() { ... }
}
```

### UseCaseContext

```java
public interface UseCaseContext {
    String getBackend();
    <T> Optional<T> getParameter(String key, Class<T> type);
    <T> T getParameter(String key, Class<T> type, T defaultValue);
    Map<String, Object> getAllParameters();
    
    default boolean hasBackend(String backend) {
        return backend.equals(getBackend());
    }
}
```

### SuccessCriteria

```java
public interface SuccessCriteria {
    boolean isSuccess(UseCaseResult result);
    String getDescription();
    
    static SuccessCriteria parse(String expression) { ... }
}
```

Expression syntax supports:
- `"isValid == true"`
- `"score >= 0.8"`
- `"isValid == true && errorCount == 0"`

---

*Previous: [Core Conceptual Artifacts](./DOC-04-CORE-CONCEPTUAL-ARTIFACTS.md)*

*Next: [Data Flow](./DOC-06-DATA-FLOW.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
