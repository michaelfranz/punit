# Shopping Assistant Spec-Driven Example Implementation Plan

This document details the implementation plan for creating `ShoppingAssistantSpecExamplesTest`—a realistic, spec-driven probabilistic test that demonstrates the complete PUnit workflow from experiment to approved specification to regression test.

---

## Overview

### Goal

Create a working example that demonstrates:

1. **Experiment Phase**: Run 1000 samples to establish empirical baseline
2. **Approval Phase**: Use `punitApprove` Gradle task to create specification
3. **Test Phase**: Run 30-sample spec-driven probabilistic test

### Key Design Decisions

| Decision             | Value                     | Rationale                                        |
|----------------------|---------------------------|--------------------------------------------------|
| Experiment samples   | 1000                      | Large sample for statistically reliable baseline |
| Test samples         | 30                        | Realistic CI budget constraint                   |
| Failure rate         | ~5%                       | Realistic LLM stochastic behavior                |
| Starting experiments | 1                         | Start simple, expand later                       |
| Target use case      | `usecase.shopping.search` | Basic JSON validity check                        |

---

## Phase 1: Configure Mock for Varied, Realistic LLM Failure Modes

### Design Philosophy

Real LLMs fail in varied and interesting ways. A realistic mock should simulate:

| Failure Mode             | Description                          | Example                                   |
|--------------------------|--------------------------------------|-------------------------------------------|
| **Malformed JSON**       | Syntax errors, unclosed braces       | `{ "products": [`                         |
| **Hallucinated Fields**  | Unexpected or misspelled field names | `"prodcuts"` instead of `"products"`      |
| **Invalid Field Values** | Wrong types, out of range, nonsense  | `"price": "expensive"` instead of `29.99` |

### Current State

The `MockShoppingAssistant` has a `MockConfiguration` record with failure rates, but the failure modes are limited. We need to expand the failure variety.

### Required Changes

#### 1.1 Define Failure Mode Enum

Create an enum to categorize failure types:

```java
/**
 * Categories of LLM response failures.
 * 
 * <p>Real LLMs fail in varied ways. This enum categorizes the main
 * failure modes to enable realistic simulation.
 */
public enum FailureMode {
    /** Syntactically invalid JSON (unclosed braces, missing quotes, etc.) */
    MALFORMED_JSON,
    
    /** Valid JSON but with unexpected/misspelled field names */
    HALLUCINATED_FIELDS,
    
    /** Valid JSON structure but field values are wrong type or nonsensical */
    INVALID_VALUES,
    
    /** Required fields are missing entirely */
    MISSING_FIELDS,
    
    /** No failure - response is valid */
    NONE
}
```

#### 1.2 Add Failure Mode Selection Logic

When generating a response, first determine IF it fails, then HOW it fails:

```java
private FailureMode selectFailureMode() {
    double roll = random.nextDouble();
    
    // ~5% total failure rate, distributed across modes:
    // - 2% malformed JSON (syntax errors)
    // - 1.5% hallucinated fields (wrong field names)
    // - 1% invalid values (wrong types/nonsense)
    // - 0.5% missing fields
    
    if (roll < 0.02) {
        return FailureMode.MALFORMED_JSON;
    } else if (roll < 0.035) {
        return FailureMode.HALLUCINATED_FIELDS;
    } else if (roll < 0.045) {
        return FailureMode.INVALID_VALUES;
    } else if (roll < 0.05) {
        return FailureMode.MISSING_FIELDS;
    }
    return FailureMode.NONE;
}
```

#### 1.3 Implement Varied Failure Generators

```java
private String generateMalformedJson() {
    // Randomly select a malformation type
    return switch (random.nextInt(4)) {
        case 0 -> "{ \"products\": [";                    // Unclosed brace
        case 1 -> "{ products: [] }";                     // Missing quotes on key
        case 2 -> "{ \"products\": [], \"query\": }";     // Missing value
        default -> "not json at all";                     // Complete garbage
    };
}

private String generateHallucinatedFieldsJson(String query, List<Product> products) {
    // Valid JSON structure but with wrong field names
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    
    // Hallucinate field names (misspellings, creative alternatives)
    String[] fieldVariants = {"prodcuts", "items", "results", "productList"};
    String[] queryVariants = {"search_query", "q", "searchTerm", "user_input"};
    
    sb.append("  \"").append(queryVariants[random.nextInt(queryVariants.length)])
      .append("\": \"").append(query).append("\",\n");
    sb.append("  \"").append(fieldVariants[random.nextInt(fieldVariants.length)])
      .append("\": [],\n");
    sb.append("  \"count\": ").append(products.size()).append("\n");  // Wrong field name
    sb.append("}");
    
    return sb.toString();
}

private String generateInvalidValuesJson(String query, List<Product> products) {
    // Valid JSON structure but field values are wrong types
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"query\": \"").append(query).append("\",\n");
    sb.append("  \"totalResults\": \"").append(products.size()).append("\",\n"); // String instead of int
    sb.append("  \"products\": [\n");
    
    for (int i = 0; i < Math.min(products.size(), 3); i++) {
        sb.append("    {\n");
        // Invalid value types
        sb.append("      \"name\": ").append(random.nextInt(1000)).append(",\n");  // Number instead of string
        sb.append("      \"price\": \"expensive\",\n");                             // String instead of number
        sb.append("      \"category\": null,\n");                                   // Null instead of string
        sb.append("      \"relevanceScore\": \"high\"\n");                          // String instead of number
        sb.append("    }");
        if (i < Math.min(products.size(), 3) - 1) sb.append(",");
        sb.append("\n");
    }
    
    sb.append("  ]\n");
    sb.append("}");
    
    return sb.toString();
}
```

#### 1.4 Update Response Generation Flow

```java
private LlmResponse generateResponse(String query, Double maxPrice, String category, int maxResults) {
    int tokensUsed = 200 + random.nextInt(300);
    
    // First, determine if and how this response fails
    FailureMode failureMode = selectFailureMode();
    
    // Generate products (needed for some failure modes)
    List<Product> products = generateProducts(query, maxPrice, category, maxResults);
    
    return switch (failureMode) {
        case MALFORMED_JSON -> LlmResponse.builder()
            .rawJson(generateMalformedJson())
            .validJson(false)
            .tokensUsed(tokensUsed)
            .failureMode(failureMode)
            .build();
            
        case HALLUCINATED_FIELDS -> LlmResponse.builder()
            .rawJson(generateHallucinatedFieldsJson(query, products))
            .validJson(true)  // Syntactically valid JSON
            .tokensUsed(tokensUsed)
            .failureMode(failureMode)
            .build();
            
        case INVALID_VALUES -> LlmResponse.builder()
            .rawJson(generateInvalidValuesJson(query, products))
            .validJson(true)  // Syntactically valid JSON
            .tokensUsed(tokensUsed)
            .failureMode(failureMode)
            .build();
            
        case MISSING_FIELDS -> LlmResponse.builder()
            .rawJson(generateMissingFieldsJson(query, products))
            .validJson(true)
            .tokensUsed(tokensUsed)
            .failureMode(failureMode)
            .build();
            
        case NONE -> generateValidResponse(query, products, tokensUsed);
    };
}
```

#### 1.5 Update LlmResponse to Track Failure Mode

Add `failureMode` field for observability:

```java
public record LlmResponse(
    String rawJson,
    boolean validJson,
    int tokensUsed,
    String query,
    List<Product> products,
    Integer totalResults,
    Map<String, Boolean> presentFields,
    FailureMode failureMode  // NEW: Track how this response failed (if at all)
) {
    // ... builder pattern
}
```

#### 1.6 Add `experimentRealistic()` Configuration

Create a configuration that uses the new varied failure modes:

```java
/**
 * Configuration simulating realistic LLM behavior with ~95% success rate
 * and varied failure modes.
 * 
 * <p>Failure distribution (~5% total):
 * <ul>
 *   <li>2.0% - Malformed JSON (syntax errors)</li>
 *   <li>1.5% - Hallucinated field names</li>
 *   <li>1.0% - Invalid field values</li>
 *   <li>0.5% - Missing required fields</li>
 * </ul>
 */
public static MockConfiguration experimentRealistic() {
    return new MockConfiguration(
        0.02,   // 2% malformed JSON
        0.005,  // 0.5% missing fields (reduced - now a separate failure mode)
        0.01,   // 1% products with missing attributes
        0.01,   // 1% price violations
        0.005,  // 0.5% category violations
        0.02,   // 2% low relevance scores
        0.005,  // 0.5% result count violations
        true    // useVariedFailureModes flag
    );
}
```

### Files Modified

- `src/experiment/java/org/javai/punit/examples/shopping/usecase/MockShoppingAssistant.java`
- `src/experiment/java/org/javai/punit/examples/shopping/usecase/LlmResponse.java`

### Acceptance Criteria

- [ ] `FailureMode` enum exists with 4 failure categories
- [ ] Mock generates varied failures (not just malformed JSON)
- [ ] Each failure mode produces distinctly different output
- [ ] `experimentRealistic()` configuration produces ~95% success rate
- [ ] Failed responses include `failureMode` for observability
- [ ] Running 100 samples shows a mix of failure types in logs

---

## Phase 2: Configure ShoppingExperiment for 1000 Samples

### Current State

The `ShoppingExperiment.measureBasicSearchReliability()` method is configured with:

```java
@Experiment(
    useCase = "usecase.shopping.search",
    samples = 100,  // Too few for reliable baseline
    tokenBudget = 50000,
    timeBudgetMs = 120000,
    experimentId = "shopping-search-baseline"
)
@ExperimentContext(
    backend = "mock",
    parameters = {
        "simulatedReliability = default",  // Wrong config
        "query = wireless headphones"
    }
)
void measureBasicSearchReliability() {
    // Method body is optional
}
```

### Required Changes

#### 2.1 Update Experiment Configuration

```java
@Experiment(
    useCase = "usecase.shopping.search",
    samples = 1000,                              // CHANGED: 1000 samples
    tokenBudget = 500000,                        // CHANGED: Increased budget
    timeBudgetMs = 600000,                       // CHANGED: 10 minutes
    experimentId = "shopping-search-baseline-v1"
)
@ExperimentContext(
    backend = "mock",
    parameters = {
        "simulatedReliability = realistic",      // CHANGED: Use realistic config
        "query = wireless headphones"
    }
)
void measureBasicSearchReliability() {
    // Method body is optional—execution is driven by the use case
}
```

#### 2.2 Update ExperimentExtension to Use Named Config

The `ExperimentExtension` needs to pass the `simulatedReliability` parameter to the `MockShoppingAssistant` constructor. This requires:

1. Update `ShoppingExperiment` constructor to accept config name
2. Or: Update the context parameter resolution in `ExperimentExtension`

**Option A: Parameterized Constructor in ShoppingExperiment**

```java
public class ShoppingExperiment extends ShoppingUseCase {
    
    public ShoppingExperiment() {
        // Default: will be overridden by context parameters
        super(new MockShoppingAssistant());
    }
    
    // ExperimentExtension will need to inject the config somehow
}
```

**Option B: Use @BeforeEach with Context Injection** (Preferred)

The experiment uses an instance of `ShoppingUseCase` with a fixed `MockShoppingAssistant`. We need the mock to be configured based on the `@ExperimentContext` parameters.

This requires changes to `ExperimentExtension` to pass context to the test instance.

**Decision**: For simplicity in this phase, we will:
1. Create a dedicated experiment method with hardcoded realistic config
2. Defer generic config injection to a later enhancement

```java
/**
 * Experiment: Measure basic product search reliability with realistic LLM behavior.
 *
 * <p>Uses 1000 samples with ~5% JSON failure rate to establish a statistically
 * reliable baseline for the basic search use case.
 */
@Experiment(
    useCase = "usecase.shopping.search",
    samples = 1000,
    tokenBudget = 500000,
    timeBudgetMs = 600000,
    experimentId = "shopping-search-realistic-v1"
)
@ExperimentContext(
    backend = "mock",
    parameters = {
        "query = wireless headphones"
    }
)
void measureRealisticSearchReliability() {
    // Uses realistic config - see constructor
}
```

And update the constructor:

```java
public ShoppingExperiment() {
    super(new MockShoppingAssistant(
        new Random(), 
        MockShoppingAssistant.MockConfiguration.experimentRealistic()
    ));
}
```

### Files Modified

- `src/experiment/java/org/javai/punit/examples/shopping/experiment/ShoppingExperiment.java`
- `src/experiment/java/org/javai/punit/examples/shopping/usecase/MockShoppingAssistant.java` (if not already done in Phase 1)

### Acceptance Criteria

- [ ] `ShoppingExperiment` has a 1000-sample experiment method
- [ ] Experiment uses `experimentRealistic()` configuration
- [ ] Running `./gradlew experimentTests --tests "ShoppingExperiment.measureRealisticSearchReliability"` completes successfully

---

## Phase 3: Run Experiment and Generate Baseline

### Actions

1. Run the experiment:
   ```bash
   ./gradlew experimentTests --tests "ShoppingExperiment.measureRealisticSearchReliability"
   ```

2. Verify baseline file is generated at:
   ```
   src/test/resources/punit/baselines/usecase-shopping-search.yaml
   ```

3. Review baseline content for expected structure:
   ```yaml
   useCaseId: usecase.shopping.search
   experimentId: shopping-search-realistic-v1
   generatedAt: 2026-01-08T...
   
   execution:
     samplesPlanned: 1000
     samplesExecuted: 1000
     terminationReason: COMPLETED
   
   statistics:
     successRate:
       observed: 0.9510  # Expected ~95% with realistic config
       standardError: 0.0068
       confidenceInterval95: [0.937, 0.963]
     successes: 951
     failures: 49
   
   cost:
     totalTimeMs: ...
     avgTimePerSampleMs: ...
     totalTokens: ...
   ```

### Expected Outcome

A baseline file showing approximately:
- **Observed success rate**: ~95% (±2%)
- **Failures**: ~50 (±20)
- **Samples executed**: 1000

### Acceptance Criteria

- [ ] Baseline file exists at expected path
- [ ] Observed success rate is between 93% and 97%
- [ ] All 1000 samples were executed
- [ ] Baseline YAML is well-formed and readable

---

## Phase 4: Create `punitApprove` Gradle Task

### Design (from PLAN-99-DEVELOPMENT-PLAN.md Phase D.2)

Non-interactive CLI-driven task:

```bash
./gradlew punitApprove --useCase=usecase.shopping.search
./gradlew punitApprove --useCase=usecase.shopping.search --notes="Initial baseline"
./gradlew punitApprove --useCase=usecase.shopping.search --force  # New version
```

### Implementation

#### 4.1 Create `SpecificationGenerator` Class

Location: `src/main/java/org/javai/punit/spec/generator/SpecificationGenerator.java`

```java
package org.javai.punit.spec.generator;

import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.spec.model.ExecutionSpecification;
import java.time.Instant;

/**
 * Generates execution specifications from empirical baselines.
 */
public class SpecificationGenerator {
    
    /**
     * Creates a specification from a baseline with approval metadata.
     */
    public ExecutionSpecification generate(
            EmpiricalBaseline baseline,
            String approvedBy,
            String approvalNotes,
            int version) {
        
        return ExecutionSpecification.builder()
            .specId(baseline.getUseCaseId() + ":v" + version)
            .useCaseId(baseline.getUseCaseId())
            .version(version)
            .approvedAt(Instant.now())
            .approvedBy(approvedBy)
            .approvalNotes(approvalNotes)
            .sourceBaselines(List.of(baseline.getExperimentId()))
            .baselineData(
                baseline.getStatistics().successes() + baseline.getStatistics().failures(),
                baseline.getStatistics().successes(),
                baseline.getGeneratedAt()
            )
            .requirements(
                baseline.getStatistics().observedSuccessRate(),
                "isValidJson == true"
            )
            .build();
    }
}
```

#### 4.2 Create `SpecificationWriter` Class

Location: `src/main/java/org/javai/punit/spec/generator/SpecificationWriter.java`

Writes specifications to YAML format:

```yaml
# Execution Specification for usecase.shopping.search
# Approved from baseline: shopping-search-realistic-v1

specId: usecase.shopping.search:v1
useCaseId: usecase.shopping.search
version: 1

approvedAt: 2026-01-08T15:30:00Z
approvedBy: mike.mannion
approvalNotes: "Initial baseline from 1000-sample experiment"

sourceBaselines:
  - shopping-search-realistic-v1

baselineData:
  samples: 1000
  successes: 951
  generatedAt: 2026-01-08T14:00:00Z

requirements:
  minPassRate: 0.951
  successCriteria: "isValidJson == true"
```

#### 4.3 Create `BaselineLoader` Class

Location: `src/main/java/org/javai/punit/experiment/engine/BaselineLoader.java`

Loads baselines from YAML files (inverse of `BaselineWriter`).

#### 4.4 Add Gradle Task to `build.gradle.kts`

```kotlin
// Task: punitApprove - Create specification from baseline
tasks.register("punitApprove") {
    group = "punit"
    description = "Creates an execution specification from an empirical baseline"
    
    doLast {
        val useCase = project.findProperty("useCase") as String?
            ?: throw GradleException("--useCase parameter required")
        
        val notes = project.findProperty("notes") as String? ?: "Approved via Gradle task"
        val force = project.hasProperty("force")
        val approver = System.getProperty("user.name") ?: "unknown"
        
        // Load baseline
        val baselinePath = file("src/test/resources/punit/baselines/${useCase.replace('.', '-')}.yaml")
        if (!baselinePath.exists()) {
            throw GradleException("Baseline not found: $baselinePath")
        }
        
        // Generate spec (would call Java classes via JavaExec or buildSrc)
        // For now, print instructions
        println("Would approve baseline: $baselinePath")
        println("Approver: $approver")
        println("Notes: $notes")
    }
}
```

**Note**: Full implementation requires either:
- A `buildSrc` project with access to PUnit classes
- A separate CLI tool invoked via `JavaExec`
- Integration into the experiment source set

For this plan, we'll implement a **minimal CLI tool** that can be invoked from Gradle.

#### 4.5 Alternative: Manual Spec Creation

Given the complexity of the Gradle task, we can alternatively:

1. Run the experiment to generate the baseline
2. Manually create the spec file based on the baseline values
3. Document the manual process

This is acceptable for the initial example and defers full automation to later.

### Files Created

- `src/main/java/org/javai/punit/spec/generator/SpecificationGenerator.java`
- `src/main/java/org/javai/punit/spec/generator/SpecificationWriter.java`
- `src/main/java/org/javai/punit/experiment/engine/BaselineLoader.java`
- Updates to `build.gradle.kts`

### Acceptance Criteria

- [ ] `SpecificationGenerator` class exists and compiles
- [ ] `SpecificationWriter` can write YAML spec files
- [ ] `BaselineLoader` can read baseline YAML files
- [ ] Either: Gradle task works, OR: manual spec creation process is documented

---

## Phase 5: Create Specification File

### Actions

**Option A: Using Gradle Task (if implemented)**

```bash
./gradlew punitApprove \
    --useCase=usecase.shopping.search \
    --notes="Initial baseline from 1000-sample experiment with ~5% failure rate"
```

**Option B: Manual Creation**

Create file: `src/test/resources/punit/specs/usecase-shopping-search.yaml`

```yaml
# Execution Specification for usecase.shopping.search
# Created from baseline: shopping-search-realistic-v1

specId: usecase.shopping.search:v1
useCaseId: usecase.shopping.search
version: 1

approvedAt: 2026-01-08T15:30:00Z
approvedBy: mike.mannion
approvalNotes: "Initial baseline from 1000-sample experiment with ~5% JSON failure rate"

sourceBaselines:
  - shopping-search-realistic-v1

baselineData:
  samples: 1000
  successes: 951        # Actual value from baseline
  generatedAt: 2026-01-08T14:00:00Z

requirements:
  minPassRate: 0.951    # From baseline observedSuccessRate
  successCriteria: "isValidJson == true"

costEnvelope:
  maxTimePerSampleMs: 100
  maxTokensPerSample: 500
  totalTokenBudget: 15000
```

### Acceptance Criteria

- [ ] Spec file exists at `src/test/resources/punit/specs/usecase-shopping-search.yaml`
- [ ] Spec file contains valid approval metadata
- [ ] BaselineData matches the generated baseline
- [ ] Spec can be loaded by `SpecificationLoader`

---

## Phase 6: Create ShoppingAssistantSpecExamplesTest

### Design

The test will:
1. Reference the spec by ID: `usecase.shopping.search:v1`
2. Run 30 samples (realistic CI budget)
3. Use the same mock configuration as the experiment
4. Assert on `isValidJson` observation

### Implementation

Location: `src/test/java/org/javai/punit/examples/ShoppingAssistantSpecExamplesTest.java`

```java
package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

/**
 * Spec-driven probabilistic tests for the shopping assistant.
 *
 * <p>These tests demonstrate the complete PUnit workflow:
 * <ol>
 *   <li>Experiments run with 1000 samples to establish baseline</li>
 *   <li>Baselines are approved to create specifications</li>
 *   <li>Tests reference specs and run with smaller sample counts</li>
 * </ol>
 *
 * <p>Unlike {@link ShoppingAssistantExamplesTest}, these tests derive their
 * pass/fail thresholds from approved specifications, not hardcoded values.
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Run: ./gradlew experimentTests --tests "ShoppingExperiment.measureRealisticSearchReliability"
 * 2. Approve: ./gradlew punitApprove --useCase=usecase.shopping.search
 * 3. Test: ./gradlew test --tests "ShoppingAssistantSpecExamplesTest"
 * </pre>
 *
 * @see org.javai.punit.examples.shopping.experiment.ShoppingExperiment
 */
@Disabled("Example - requires spec file: src/test/resources/punit/specs/usecase-shopping-search.yaml")
@ProbabilisticTestBudget(
    tokenBudget = 15000,
    timeBudgetMs = 30000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
@DisplayName("Shopping Assistant Spec-Driven Tests")
class ShoppingAssistantSpecExamplesTest {

    private ShoppingUseCase useCase;
    private UseCaseContext context;

    @BeforeEach
    void setUp() {
        // Use the same mock configuration as the experiment
        useCase = new ShoppingUseCase(
            new MockShoppingAssistant(
                new java.util.Random(),
                MockShoppingAssistant.MockConfiguration.experimentRealistic()
            )
        );
        context = DefaultUseCaseContext.builder()
            .backend("mock")
            .parameter("query", "wireless headphones")
            .build();
    }

    /**
     * Tests that responses are valid JSON, using spec-derived threshold.
     *
     * <p>This test references the specification created from the 1000-sample
     * experiment. The threshold is derived at runtime using the Sample-Size-First
     * approach:
     * <ul>
     *   <li>Baseline: 951/1000 (95.1%)</li>
     *   <li>Test samples: 30</li>
     *   <li>Confidence: 95%</li>
     *   <li>Derived threshold: ~87% (accounts for sampling variance)</li>
     * </ul>
     */
    @ProbabilisticTest(
        spec = "usecase.shopping.search:v1",
        samples = 30,
        confidence = 0.95
    )
    @DisplayName("Should return valid JSON (spec-driven)")
    void shouldReturnValidJson(TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("wireless headphones", context);

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }
}
```

### Key Differences from ShoppingAssistantExamplesTest

| Aspect | ShoppingAssistantExamplesTest | ShoppingAssistantSpecExamplesTest |
|--------|-------------------------------|-----------------------------------|
| Threshold source | Hardcoded `minPassRate = 0.90` | Derived from spec baseline |
| Samples | 30 (arbitrary) | 30 (spec-informed) |
| Confidence | Implicit | Explicit `confidence = 0.95` |
| Provenance | None | Links to baseline & spec |
| Audit trail | None | Full traceability |

### Files Created

- `src/test/java/org/javai/punit/examples/ShoppingAssistantSpecExamplesTest.java`

### Acceptance Criteria

- [ ] Test class compiles
- [ ] Test references spec via `spec = "usecase.shopping.search:v1"`
- [ ] Test uses `samples = 30` and `confidence = 0.95`
- [ ] Test uses same mock configuration as experiment
- [ ] When spec file exists and `@Disabled` removed, test runs successfully

---

## Phase 7: Verify End-to-End Flow

### Actions

1. **Enable the test** (remove `@Disabled`)

2. **Run the test**:
   ```bash
   ./gradlew test --tests "ShoppingAssistantSpecExamplesTest"
   ```

3. **Verify output** includes:
   - Statistical qualification in console summary
   - Reference to spec ID
   - Derived threshold (should be ~87-90% for 30 samples at 95% confidence)
   - Pass/fail based on spec-derived threshold

### Expected Output

```
═══════════════════════════════════════════════════════════════
PUnit PASSED: shouldReturnValidJson()
  Spec: usecase.shopping.search:v1
  Observed pass rate: 93.3% (28/30) >= threshold: 87.2%
  Threshold derived from baseline: 951/1000 (95.1%)
  Confidence: 95%
  Termination: All samples completed
  Elapsed: 45ms
═══════════════════════════════════════════════════════════════
```

### Acceptance Criteria

- [ ] Test executes and produces console output
- [ ] Output includes spec reference
- [ ] Output shows derived threshold
- [ ] Test passes when mock produces ~95% valid JSON

---

## Summary

| Phase | Description | Estimated Effort |
|-------|-------------|------------------|
| 1 | Configure mock for ~5% failure rate | 30 min |
| 2 | Configure ShoppingExperiment for 1000 samples | 30 min |
| 3 | Run experiment and verify baseline | 15 min |
| 4 | Create punitApprove infrastructure | 2-3 hours |
| 5 | Create specification file | 15 min |
| 6 | Create ShoppingAssistantSpecExamplesTest | 1 hour |
| 7 | Verify end-to-end flow | 30 min |

**Total estimated effort**: 5-6 hours

---

## Dependencies

This plan depends on:

1. **Existing infrastructure**:
   - `ExperimentExtension` and baseline generation (Phase E2 ✅)
   - `SpecificationLoader` (Phase E3 ✅)
   - `@ProbabilisticTest` with `spec` attribute (Phase E4 ✅)

2. **Required enhancements**:
   - `BaselineLoader` (inverse of `BaselineWriter`) — Phase 4
   - Threshold derivation integration — depends on Phase C (statistics engine)

### Note on Phase C Dependency

The full spec-driven threshold derivation (Phase C from PLAN-99-DEVELOPMENT-PLAN.md) is not yet implemented. For this example:

**Option A**: Implement minimal threshold derivation inline  
**Option B**: Use `minPassRate` from spec as explicit threshold (deferred derivation)

We will use **Option B** for initial implementation, with the understanding that Phase C will add proper statistical derivation later.

---

## Open Questions

1. **Should Phase 4 (punitApprove) be fully automated or manual for v1?**
   - Recommendation: Manual for v1, automated in Phase D

2. **How should the test handle missing spec files?**
   - Current: `@Disabled` with note
   - Alternative: Fail fast with clear error message

3. **Should we add multiple test methods to the example, or keep it minimal?**
   - Recommendation: Start with one, expand based on feedback

---

*Created: 2026-01-08*  
*Author: Claude (AI Assistant)*  
*Status: Draft - Awaiting Review*

