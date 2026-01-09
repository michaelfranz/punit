# Execution Model and Context

**Status:** Proposed  
**Created:** 2026-01-09  
**Supersedes:** Previous "omit first sample" flag discussion

## Overview

This document proposes a unified execution model for PUnit experiments and probabilistic tests. Rather than treating execution concerns as separate features, we frame them as **orthogonal configuration axes** that compose naturally.

## Motivation

Real-world use cases for LLM testing have diverse execution requirements:

1. **Rate-limited APIs** - External LLM providers enforce request limits
2. **High-throughput testing** - Mock implementations can run in parallel
3. **JVM warmup effects** - First invocations may be slower (JIT, connection pools)
4. **Observability** - Test code may need to know which sample is executing

The current implementation assumes sequential execution with no pacing control. This limits PUnit's applicability to real-world scenarios.

---

## Experiment Modes: BASELINE vs EXPLORE

Experiments serve two distinct purposes that share execution machinery but have different intents and outputs.

### The Two Modes

```
┌─────────────────────────────────────────────────────────────────┐
│                    @Experiment Modes                             │
├─────────────────────────────────────────────────────────────────┤
│  Mode:     BASELINE (default)      │ EXPLORE                    │
│  Intent:   Precise estimation      │ Factor comparison          │
│  Configs:  1 (implicit)            │ N (from factor source)     │
│  Samples:  1000+ (default: 1000)   │ 1+/config (default: 1)     │
│  Output:   1 baseline file         │ N baseline files           │
│  Decision: "What's the true rate?" │ "Which config is best?"    │
└─────────────────────────────────────────────────────────────────┘
```

### Mode Details

| Aspect | BASELINE (Default) | EXPLORE |
|--------|-------------------|---------|
| **Purpose** | Establish reliable statistics for a single configuration | Understand how factors affect behavior |
| **Configurations** | One (the target/production config) | Many (exploring the space) |
| **Samples** | Large (1000+) for narrow confidence intervals | Default: 1 per config (smoke test); increase for comparison |
| **Anti-pattern** | 10 samples (imprecise) | 100+ samples/config (wasteful exploration) |
| **Output** | Single baseline YAML | Multiple baseline YAMLs (one per config) |
| **Comparison** | N/A | IDE diff tools, or future PUnit comparison tooling |

### Exploration Workflow: Two Phases

The default of **1 sample per configuration** supports a natural two-phase workflow:

**Phase 1: "Which configs work at all?"**
```java
@Experiment(
    mode = ExperimentMode.EXPLORE
    // samplesPerConfig = 1 (default)
)
```
- Fast pass through all configurations
- Filters out completely broken configs (syntax errors, auth failures, etc.)
- With 20 configs: 20 total invocations

**Phase 2: "Which config is best?"**
```java
@Experiment(
    mode = ExperimentMode.EXPLORE,
    samplesPerConfig = 10  // Explicit increase for comparison
)
```
- Conscious decision to invest in statistical comparison
- Enough samples to reveal probabilistic differences
- With 5 finalist configs × 10 samples: 50 total invocations

This design prevents the anti-pattern of running many samples across many configs when you first just want to know what works.

### Why BASELINE is Default

Per the canonical operational flow (see `docs/OPERATIONAL-FLOW.md`), configuration exploration is an **optional** step. The primary use case is baseline derivation for driving probabilistic tests. Therefore:

- `mode = BASELINE` is the default
- Developers must explicitly opt into `mode = EXPLORE`

### BASELINE Mode

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000  // Large sample for precise estimation
)
void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
    // Context configured in @BeforeEach via UseCaseProvider
    captor.record(useCase.searchProducts("headphones", context));
}
```

**Output:**
```
build/punit/baselines/
└── ShoppingUseCase.yaml    # Single baseline file
```

### EXPLORE Mode

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    mode = ExperimentMode.EXPLORE
    // samplesPerConfig = 1 (default) - quick pass to find working configs
)
@FactorSource("configurations")
void exploreBackendConfigurations(
    @Factor("backend") String backend,
    @Factor("temperature") double temperature,
    ShoppingUseCase useCase,
    ResultCaptor captor
) {
    context.setBackend(backend);
    context.setTemperature(temperature);
    captor.record(useCase.searchProducts("headphones", context));
}

static Stream<Arguments> configurations() {
    return Stream.of(
        Arguments.of("gpt-4", 0.0),
        Arguments.of("gpt-4", 0.7),
        Arguments.of("gpt-3.5-turbo", 0.0),
        Arguments.of("gpt-3.5-turbo", 0.7)
    );
}
```

**Output:**
```
build/punit/baselines/
└── ShoppingUseCase/              # Directory for exploration results
    ├── gpt-4_temp-0.0.yaml
    ├── gpt-4_temp-0.7.yaml
    ├── gpt-3.5-turbo_temp-0.0.yaml
    └── gpt-3.5-turbo_temp-0.7.yaml
```

### Unified Output Format

Both modes produce **identical baseline file format**. The only difference is quantity:
- BASELINE: 1 file
- EXPLORE: N files (one per configuration)

This enables comparison using standard tools:
- IDE diff viewers
- Command-line `diff`
- Future PUnit comparison tooling

### Factor Source Options

EXPLORE mode needs a source of configurations. Options (familiar to JUnit users):

```java
// Method source (most flexible)
@FactorSource("configurations")
static Stream<Arguments> configurations() { ... }

// CSV source (simple cases)
@CsvFactorSource({
    "gpt-4, 0.0",
    "gpt-4, 0.7",
    "gpt-3.5-turbo, 0.0"
})

// Enum source (when factors are enums)
@EnumFactorSource(Backend.class)
```

### Guardrails

To prevent accidental misuse:

| Situation | Severity | Message |
|-----------|----------|---------|
| BASELINE with < 100 samples | Warning | "Low sample count may produce imprecise baseline" |
| EXPLORE with > 50 samples/config | Warning | "High sample count for exploration—consider BASELINE mode for chosen config" |
| EXPLORE without factor source | Warning | "EXPLORE without factors is equivalent to BASELINE. Consider adding @FactorSource or using BASELINE mode." |

**Note on EXPLORE without factors:** While semantically redundant (EXPLORE with no factors = BASELINE with different defaults), we allow it to reduce friction for developers learning the EXPLORE workflow. The warning educates without blocking.

**Note on default samples:** The default of 1 sample/config in EXPLORE mode is intentional—it supports fast initial filtering before investing in deeper comparison.

### Configuration Naming

For EXPLORE mode output files, configuration names are derived from factor values:

```java
// Factors: backend="gpt-4", temperature=0.7
// File: ShoppingUseCase/gpt-4_temp-0.7.yaml

// Custom naming via annotation:
@Factor(name = "backend", filePrefix = "be")
@Factor(name = "temperature", filePrefix = "t")
// File: ShoppingUseCase/be-gpt-4_t-0.7.yaml
```

---

## Proposed Execution Model

```
┌─────────────────────────────────────────────────────────────────┐
│                    Execution Configuration                       │
├─────────────────────────────────────────────────────────────────┤
│  Concurrency:  SEQUENTIAL | PARALLEL(maxConcurrency)            │
│  Pacing:       NONE | FIXED_DELAY(ms) | RATE_LIMIT(n/period)    │
│  Warmup:       warmupSamples (executed but excluded from stats) │
│  Context:      SampleInfo injected into method (index, total)   │
└─────────────────────────────────────────────────────────────────┘
```

These axes are largely **orthogonal**:
- Concurrency + Rate Limiting = "Up to N concurrent, max M/minute"
- Sequential + Fixed Delay = Simple rate limiting
- Parallel + No Pacing = Maximum throughput

---

## Axis 1: Sample Context (SampleInfo)

### Problem

Test and experiment methods sometimes need to know their execution context:
- Which sample number is currently executing?
- How many total samples are planned?
- Is this a warmup sample?

This is useful for logging, debugging, and conditional setup—though **not** for varying test behavior (which would defeat statistical sampling).

### Proposed Solution

Inspired by JUnit 5's `RepetitionInfo` for `@RepeatedTest`:

```java
@Experiment(useCase = ShoppingUseCase.class, samples = 1000)
void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor, SampleInfo info) {
    log.debug("Executing sample {}/{}", info.currentSample(), info.totalSamples());
    
    if (info.isWarmup()) {
        log.trace("Warmup sample - results will be excluded");
    }
    
    captor.record(useCase.searchProducts("headphones", context));
}
```

### SampleInfo Interface

```java
public interface SampleInfo {
    
    /** Current sample number (1-based, includes warmup samples). */
    int currentSample();
    
    /** Total planned samples (includes warmup). */
    int totalSamples();
    
    /** Measured samples (excludes warmup). */
    int measuredSamples();
    
    /** True if this is a warmup sample (excluded from statistics). */
    boolean isWarmup();
    
    /** Warmup sample number (1-based), or 0 if not a warmup sample. */
    int warmupNumber();
}
```

### Anti-Pattern Warning

Document clearly that `SampleInfo` is for **observability**, not for varying behavior:

```java
// ❌ ANTI-PATTERN: Don't do this!
void badExperiment(ShoppingUseCase useCase, SampleInfo info) {
    if (info.currentSample() < 100) {
        useCase.searchProducts("easy query");  // Different behavior!
    } else {
        useCase.searchProducts("hard query");
    }
}

// ✅ CORRECT: Use for logging/debugging only
void goodExperiment(ShoppingUseCase useCase, SampleInfo info) {
    log.debug("Sample {}", info.currentSample());
    useCase.searchProducts("consistent query");
}
```

---

## Axis 2: Warmup Samples

### Problem

The first N samples of an experiment may exhibit different behavior due to:
- **JVM JIT compilation** - Code runs slower before optimization
- **Connection pool initialization** - First requests establish connections
- **Cache warming** - In-memory caches are cold
- **LLM provider warmup** - Some APIs have cold-start latency

Previously, we discussed a boolean flag `omitFirstSample` to address this. However, warmup samples is a **superior solution** that:
- Generalizes to any number of warmup iterations
- Makes the intent explicit in configuration
- Integrates cleanly with `SampleInfo`

### Proposed Solution

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000,
    warmupSamples = 10  // First 10 run but don't count
)
void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
    captor.record(useCase.searchProducts("headphones", context));
}
```

### Semantics

| Configuration | Warmup Runs | Measured Runs | Total Runs |
|---------------|-------------|---------------|------------|
| `samples=100, warmupSamples=0` | 0 | 100 | 100 |
| `samples=100, warmupSamples=10` | 10 | 100 | 110 |
| `samples=1000, warmupSamples=5` | 5 | 1000 | 1005 |

Key points:
- `samples` always means **measured samples** (contributing to statistics)
- `warmupSamples` are additional runs that execute but are excluded
- Total executions = `warmupSamples + samples`

### Implementation Notes

1. Warmup samples execute first, sequentially (even if parallel execution is configured)
2. `ResultCaptor.record()` during warmup is a no-op (or records to a separate warmup aggregator)
3. `SampleInfo.isWarmup()` returns `true` during warmup phase
4. Baseline/statistics only include measured samples

---

## Axis 3: Pacing Control

### Problem

External APIs enforce rate limits:
- OpenAI: Requests per minute (RPM) and tokens per minute (TPM)
- Anthropic: Similar rate limiting
- Custom APIs: Various throttling policies

Hitting rate limits causes failures that aren't representative of actual LLM behavior.

### Proposed Solution: Two Modes

#### Mode A: Fixed Delay (Simple)

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 100,
    delayBetweenSamplesMs = 500  // 500ms pause between each sample
)
```

This is a convenience for simple cases. Effective rate: `1000 / delayMs` per second.

#### Mode B: Rate Limit (Flexible)

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000,
    rateLimit = @RateLimit(requests = 60, per = TimeUnit.MINUTES)
)
```

Or using a string format:
```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000,
    rateLimit = "60/minute"  // Alternative: "10/second", "1000/hour"
)
```

### Rate Limiting Algorithms

For `@RateLimit`, implement a **token bucket** or **sliding window** algorithm:

```
Token Bucket (recommended):
- Bucket holds up to N tokens
- Tokens replenish at rate of N per period
- Each request consumes one token
- If bucket is empty, wait for replenishment
```

### Combining with Concurrency

Rate limiting and concurrency can compose:
```java
@Experiment(
    samples = 1000,
    concurrency = 5,                           // Up to 5 parallel
    rateLimit = @RateLimit(60, TimeUnit.MINUTES)  // But max 60/min total
)
```

This means: "Run up to 5 concurrent requests, but collectively never exceed 60 requests per minute."

---

## Axis 4: Concurrent Execution

### Problem

Sequential execution is slow for large sample sizes:
- 1000 samples × 2 seconds each = 33 minutes
- With 10 concurrent threads = 3.3 minutes

Mock implementations and some APIs can handle parallel requests.

### Proposed Solution

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000,
    concurrency = 10  // Up to 10 parallel executions
)
```

Default: `concurrency = 1` (sequential, current behavior)

### Threading Model Options

Different use cases have different threading requirements:

```java
public enum ThreadingModel {
    /** Platform threads (traditional thread pool). Best for CPU-bound work. */
    PLATFORM,
    
    /** Virtual threads (Project Loom). Best for IO-bound work like API calls. */
    VIRTUAL,
    
    /** Let PUnit decide based on Java version and heuristics. */
    AUTO
}

@Experiment(
    concurrency = 10,
    threadingModel = ThreadingModel.VIRTUAL  // Use virtual threads
)
```

### Implementation Considerations

1. **Thread-safe aggregation** - `ExperimentResultAggregator` must use concurrent data structures
2. **Result ordering** - Results may arrive out of order; baseline shouldn't depend on order
3. **Reproducibility** - Random seeds become non-deterministic across runs
4. **Error handling** - One failed sample shouldn't crash other concurrent samples
5. **Resource limits** - Use case instances may have shared state (connections, etc.)

### Use Case Thread Safety

The `UseCaseProvider` should indicate whether use cases are thread-safe:

```java
@BeforeEach
void setUp() {
    provider.register(ShoppingUseCase.class, 
        () -> new ShoppingUseCase(new MockShoppingAssistant()),
        ThreadSafety.PER_THREAD  // Create new instance per thread
    );
}
```

Options:
- `SHARED` - One instance shared across all threads (thread-safe use case)
- `PER_THREAD` - Create new instance per thread (non-thread-safe use case)

---

## Combined Example

```java
@Experiment(
    useCase = ShoppingUseCase.class,
    samples = 1000,
    warmupSamples = 10,
    concurrency = 5,
    rateLimit = @RateLimit(requests = 100, per = TimeUnit.MINUTES),
    threadingModel = ThreadingModel.VIRTUAL
)
void measureRealLLMBaseline(ShoppingUseCase useCase, ResultCaptor captor, SampleInfo info) {
    // Backend configured in @BeforeEach via UseCaseProvider
    if (info.isWarmup()) {
        log.info("Warmup sample {}/{}", info.warmupNumber(), 10);
    } else {
        log.info("Measured sample {}/{}", info.currentSample() - 10, 1000);
    }
    
    captor.record(useCase.searchProducts("wireless headphones", context));
}
```

This experiment:
1. Runs 10 warmup samples (excluded from statistics)
2. Runs 1000 measured samples
3. Uses up to 5 virtual threads concurrently
4. Never exceeds 100 requests per minute
5. Provides sample info for logging

---

## Migration Path

### Phase 1: SampleInfo (Low Risk)
- Add `SampleInfo` interface
- Make it injectable as method parameter
- No breaking changes

### Phase 2: Warmup Samples (Low Risk)
- Add `warmupSamples` attribute to `@Experiment` and `@ProbabilisticTest`
- Default to 0 (current behavior)
- **Deprecate** any existing "omit first sample" flag

### Phase 3: Pacing Control (Medium Risk)
- Add `delayBetweenSamplesMs` for simple cases
- Add `@RateLimit` annotation for flexible rate limiting
- Default to no pacing (current behavior)

### Phase 4: Concurrent Execution (Higher Risk)
- Add `concurrency` attribute
- Add `threadingModel` attribute
- Refactor aggregators for thread safety
- Update `UseCaseProvider` with thread-safety hints
- Default to sequential (current behavior)

---

## Open Questions

1. **Reactive APIs** - How should we handle `Mono<UseCaseResult>` or `CompletableFuture<UseCaseResult>` return types?

2. **Adaptive rate limiting** - Should PUnit automatically back off when it detects rate limit errors (HTTP 429)?

3. **Per-sample timeouts** - Should individual samples have timeouts, separate from the overall time budget?

4. **Progress reporting** - How should concurrent execution report progress? Current sequential reporting won't work.

5. **Baseline determinism** - With concurrent execution, should we capture execution order in the baseline for debugging?

---

## References

- JUnit 5 `@RepeatedTest` and `RepetitionInfo`: https://junit.org/junit5/docs/current/user-guide/#writing-tests-repeated-tests
- Java 21 Virtual Threads: https://openjdk.org/jeps/444
- Token Bucket Algorithm: https://en.wikipedia.org/wiki/Token_bucket
- OpenAI Rate Limits: https://platform.openai.com/docs/guides/rate-limits

