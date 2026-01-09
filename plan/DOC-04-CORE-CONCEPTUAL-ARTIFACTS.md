# Core Conceptual Artifacts

This section defines the fundamental abstractions in the punit experiment extension.

## 3.1 Use Case (Test/Experiment Harness)

A **use case** is the fundamental unit of observation and testing. It is:

- A **function** that invokes production code
- Identified by a **use case ID** (string identifier, e.g., `usecase.email.validation`)
- Returns a `UseCaseResult` containing observed outcomes
- **Never** called by production code
- Defined in test/experiment space only

### 3.1.1 Use Case Representation

Use cases are represented as annotated methods:

```java
@UseCase("usecase.email.validation")
UseCaseResult validateEmailFormat(String email, UseCaseContext context) {
    // Invoke production code
    ValidationResult result = emailValidator.validate(email);
    
    // Capture observations (not assertions)
    return UseCaseResult.builder()
        .value("isValid", result.isValid())
        .value("errorCode", result.getErrorCode())
        .value("processingTimeMs", result.getProcessingTimeMs())
        .build();
}
```

**Key Design Decisions**:

1. **Use cases are methods, not classes**: Keeps them lightweight and composable.
2. **Use case ID is declared via annotation**: The ID is metadata, not part of the method signature.
3. **UseCaseContext is injected**: Context provides backend-specific configuration.
4. **Return type is always UseCaseResult**: Enforces the pattern that use cases produce observations, not verdicts.

### 3.1.2 Use Case Discovery

The framework discovers use cases via:
1. Classpath scanning for `@UseCase`-annotated methods
2. Explicit registration in test configuration
3. Reference from `@Experiment` or `@ProbabilisticTest(spec=...)` annotations

## 3.2 UseCaseResult

`UseCaseResult` is a neutral container for observed outcomes with:
- `Map<String, Object> values` - primary outputs
- `Map<String, Object> metadata` - contextual information
- `Instant timestamp` and `Duration executionTime`
- `Optional<Throwable> exception` - captured exception, if one occurred

**Design Principles**:
1. **Neutral and descriptive**: Contains key-value data, not judgments
2. **Flexible schema**: `Map<String, Object>` allows domain-specific values
3. **Immutable**: Once constructed, results cannot be modified
4. **Separates values from metadata**
5. **Self-contained**: Captures both normal outcomes and exceptions

### Exception Handling

Use cases may fail with exceptions (e.g., JSON parsing fails on malformed LLM output). The result captures this:

```java
public Optional<Throwable> getException() { ... }
public boolean isExceptional() { return exception != null; }
```

**Behavior**:
- Framework catches unexpected exceptions from use case invocation
- Use case can explicitly set expected exceptions via `builder().exception(e)`
- Exceptional results are treated as failures by default unless `SuccessCriteria` explicitly handles them
- Exception summaries (type, message, count) are serialized to baselines; full stack traces go to logs

## 3.3 Experiment

An experiment repeatedly executes a use case to gather empirical data.

### Experiment Modes

Experiments operate in one of two modes (see `plan/PLAN-EXECUTION.md` for details):

| Mode | Purpose | Configurations | Samples | Output |
|------|---------|----------------|---------|--------|
| **BASELINE** (default) | Precise estimation of one configuration | 1 | 1000+ | Single baseline file |
| **EXPLORE** | Compare multiple configurations | N | 1 per config (default) | N baseline files |

### Experiment Vocabulary

| Term                       | Definition                                                                     |
|----------------------------|--------------------------------------------------------------------------------|
| **BASELINE Mode**          | Default mode: precise estimation with many samples, one configuration          |
| **EXPLORE Mode**           | Comparison mode: multiple configurations, fewer samples each                   |
| **Factor**                 | One independently varied dimension in EXPLORE mode (e.g., `model`, `temperature`) |
| **FactorSource**           | JUnit-style source of factor combinations (e.g., `@MethodSource`)              |
| **ExperimentConfig**       | One concrete combination of factor values—the unit of execution                |

### Key Characteristics

- **No pass/fail**: Experiments never fail (except for infrastructure errors)
- **Produces empirical baseline**: One baseline file per configuration
- **Never gates CI**: Experiment results are informational only
- **EXPLORE uses familiar JUnit patterns**: `@FactorSource`, `@MethodSource` for defining configurations

*For complete details on experiment modes and execution configuration, see `plan/PLAN-EXECUTION.md`.*

## 3.4 Empirical Baseline

The machine-readable output of an experiment containing:
- Use case ID and generation timestamp
- Configuration (backend-specific parameters)
- Statistical observations (success rate, variance, failure distribution)
- Cost metrics (tokens consumed, time elapsed)
- Sample size and confidence metadata

**Properties**: Immutable, Auditable, Descriptive (not normative), YAML by default

## 3.5 Execution Specification

A **human-reviewed and approved contract** derived from empirical baselines. The specification stores **raw empirical data**, not computed thresholds. Thresholds are computed at test runtime based on `@ProbabilisticTest` annotation parameters.

### Design Rationale

| Concern | Where It Lives |
|---------|----------------|
| **Empirical truth** (what we observed) | Specification |
| **Operational preferences** (how we test) | `@ProbabilisticTest` annotation |
| **Threshold computation** | Framework (at runtime) |

This separation means changing test parameters (e.g., confidence level, sample size) doesn't require re-approval of the specification.

### Specification Structure

```yaml
useCaseId: json.generation
version: v3

approval:
  approvedAt: 2026-01-04T16:00:00Z
  approvedBy: jane.engineer@example.com
  notes: "Approved after prompt template improvements"

baseline:
  generatedAt: 2026-01-04T15:30:00Z
  samples: 1000
  successes: 935
  failures: 65
  observedRate: 0.935
  confidenceInterval:
    level: 0.95
    lower: 0.918
    upper: 0.952

configuration:
  backend: llm
  model: gpt-4
  temperature: 0.7

successCriteria: "isValidJson == true && hasRequiredFields == true"
```

### What the Specification Contains

| Field | Purpose |
|-------|---------|
| `useCaseId` | Links spec to use case |
| `version` | Incremented on each approval |
| `approval.*` | Audit trail (who, when, why) |
| `baseline.samples` | Original experiment sample count |
| `baseline.successes/failures` | Raw counts for statistical derivation |
| `baseline.observedRate` | Point estimate from experiment |
| `baseline.confidenceInterval` | Precision of the estimate |
| `configuration` | Configuration used during experiment |
| `successCriteria` | How to evaluate each sample |

### What the Specification Does NOT Contain

- **`minPassRate`**: Computed at runtime from `baseline.*` + annotation parameters
- **Derived thresholds**: These depend on test sample size and confidence level

### Runtime Threshold Derivation

When a `@ProbabilisticTest` runs:

1. Framework loads spec → `observedRate: 0.935`, `baselineSamples: 1000`
2. Framework reads annotation → `testSamples: 100`, `thresholdConfidence: 0.95`
3. Framework computes → `minPassRate ≈ 0.896` (using Wilson lower bound)
4. Test executes and compares observed rate against computed threshold

**Properties**: Raw empirical data, Versioned, Traceable, Approved, YAML by default

## 3.6 Probabilistic Conformance Test

A conformance test validates that system behavior matches an approved specification. The test annotation specifies **operational preferences**; the framework combines these with spec data to compute thresholds.

```java
// Approach 1: Sample-Size-First (cost-driven)
@ProbabilisticTest(
    spec = "json.generation:v3",
    samples = 100,
    thresholdConfidence = 0.95
)
void jsonGenerationMeetsSpec() {
    // Framework loads spec, computes threshold from baseline data,
    // runs 100 samples, evaluates against successCriteria, determines pass/fail
}

// Approach 2: Confidence-First (quality-driven)
@ProbabilisticTest(
    spec = "json.generation:v3",
    confidence = 0.99,
    minDetectableEffect = 0.05,
    power = 0.80
)
void jsonGenerationHighConfidence() {
    // Framework computes required sample size, then runs test
}
```

**Key Design Decisions**:
1. **Spec contains raw data**: Observed rates, sample counts, confidence intervals
2. **Annotation contains operational preferences**: Sample size, confidence level, etc.
3. **Framework computes thresholds at runtime**: Combines spec data with annotation parameters
4. Inline thresholds (without spec) remain supported but generate warnings

---

*Previous: [Architecture Overview](./DOC-03-ARCHITECTURE-OVERVIEW.md)*

*Next: [Annotation & API Design](./DOC-05-ANNOTATION-API-DESIGN.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
