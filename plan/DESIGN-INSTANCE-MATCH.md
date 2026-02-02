# Design: Instance Conformance via Expected Results

This document captures the design for adding expected result comparison to `UseCaseOutcome`, enabling instance conformance checking across all PUnit experiment types and probabilistic tests.

## Motivation

### The Two Levels of Correctness

| Level                | Question                                     | Mechanism                       |
|----------------------|----------------------------------------------|---------------------------------|
| Contract conformance | Does the output satisfy structural rules?    | Service contract postconditions |
| Instance conformance | Does the output match the expected instance? | Expected result comparison      |

Contract conformance is necessary but not sufficient. A structurally valid response may still be semantically wrong:

```
Input: "add milk to my basket"
Output: {"action":"addItem","product":"whole milk","quantity":2}

Contract: ✓ Valid JSON, has required fields, positive quantity
Expected: ✗ Product should be "milk", quantity should be 1
```

### Current State

PUnit currently supports only contract conformance:

- **MEASURE** — Measures contract pass rate over 1000+ samples
- **EXPLORE** — Compares contract pass rate across configurations
- **OPTIMIZE** — Optimizes for contract pass rate
- **@ProbabilisticTest** — Passes if contract satisfied at threshold rate

### The Insight

Instead of creating a new experiment type for verification, we enhance `UseCaseOutcome` to optionally capture an expected result. This gives instance conformance checking to **all** existing experiment types and tests with minimal infrastructure change.

## Design Principles

### 1. Enhance, Don't Duplicate

The expected result comparison integrates into the existing `UseCaseOutcome` builder. No new experiment type, no parallel infrastructure.

### 2. Optional and Additive

Expected result is optional. Existing use cases work unchanged. When provided, the outcome tracks both contract and instance conformance.

### 3. Comparison Logic Is Pluggable

Different scenarios require different comparison strategies. PUnit provides a default JSON matcher; users can supply their own.

### 4. Lean on Existing Libraries

For JSON comparison, we use [zjsonpatch](https://github.com/flipkart-incubator/zjsonpatch) which returns structured diffs as RFC 6902 JSON Patch operations.

## The Enhanced API

### Before (Contract Conformance Only)

```java
public UseCaseOutcome<ChatResponse> translateInstruction(String instruction) {
    return UseCaseOutcome
        .withContract(CONTRACT)
        .input(new ServiceInput(systemPrompt, instruction, model, temperature))
        .execute(this::executeTranslation)
        .withResult((response, meta) -> meta
            .meta("tokensUsed", response.totalTokens()))
        .build();
}
```

### After (Contract + Instance Conformance)

```java
public UseCaseOutcome<ChatResponse> translateInstruction(String instruction, String expected) {
    return UseCaseOutcome
        .withContract(CONTRACT)
        .expecting(expected, ChatResponse::content)   // NEW: instance conformance
        .input(new ServiceInput(systemPrompt, instruction, model, temperature))
        .execute(this::executeTranslation)
        .withResult((response, meta) -> meta
            .meta("tokensUsed", response.totalTokens()))
        .build();
}
```

The `expecting()` clause is optional. When omitted, behavior is unchanged.

### With Custom Matcher

```java
.expecting(expected, ChatResponse::content, JsonMatcher.builder()
    .ignoreFields("timestamp", "requestId")
    .build())
```

## Core Abstractions

### VerificationMatcher<T>

Compares actual vs expected, returning match status and diff.

```java
public interface VerificationMatcher<T> {

    MatchResult compare(T actual, T expected);

    record MatchResult(boolean matches, String diff) {

        public static MatchResult match() {
            return new MatchResult(true, "");
        }

        public static MatchResult mismatch(String diff) {
            return new MatchResult(false, diff);
        }

        public boolean mismatches() {
            return !matches;
        }
    }
}
```

### JsonMatcher (Default Implementation)

Uses zjsonpatch for semantic JSON comparison with readable diff output.

```java
public class JsonMatcher implements VerificationMatcher<String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> ignoredFields;

    @Override
    public MatchResult compare(String actual, String expected) {
        try {
            JsonNode actualNode = mapper.readTree(actual);
            JsonNode expectedNode = mapper.readTree(expected);

            JsonNode patch = JsonDiff.asJson(expectedNode, actualNode);

            if (patch.isEmpty()) {
                return MatchResult.match();
            }
            return MatchResult.mismatch(formatDiff(patch));

        } catch (JsonProcessingException e) {
            return MatchResult.mismatch("JSON parse error: " + e.getMessage());
        }
    }

    private String formatDiff(JsonNode patch) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode op : patch) {
            String operation = op.get("op").asText();
            String path = op.get("path").asText();

            switch (operation) {
                case "replace" -> sb.append(String.format(
                    "%s: expected %s, got %s%n", path,
                    op.get("value"), "(different)"));
                case "add" -> sb.append(String.format(
                    "%s: unexpected value %s%n", path, op.get("value")));
                case "remove" -> sb.append(String.format(
                    "%s: missing expected value%n", path));
            }
        }
        return sb.toString().trim();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Set<String> ignoredFields = Set.of();

        public Builder ignoreFields(String... fields) {
            this.ignoredFields = Set.of(fields);
            return this;
        }

        public JsonMatcher build() {
            return new JsonMatcher(ignoredFields);
        }
    }
}
```

### ResultExtractor<R, T>

Extracts the comparable value from the use case result type.

```java
@FunctionalInterface
public interface ResultExtractor<R, T> {
    T extract(R result);
}
```

The framework needs to know how to extract the comparable content from the result. For `ChatResponse`, this is `ChatResponse::content`.

```java
.expecting(expected, ChatResponse::content)
// or with custom matcher
.expecting(expected, ChatResponse::content, customMatcher)
```

## Enhanced UseCaseOutcome

### New Fields

```java
public class UseCaseOutcome<R> {

    // Existing
    private final R result;
    private final List<PostconditionResult> postconditionResults;
    private final boolean contractSatisfied;

    // New
    private final Object expectedValue;              // null if not specified
    private final MatchResult instanceMatchResult;   // null if no expected

    // New accessors
    public boolean hasExpectedValue() {
        return expectedValue != null;
    }

    public boolean matchesExpected() {
        // True if no expected specified, or if matches
        return instanceMatchResult == null || instanceMatchResult.matches();
    }

    public Optional<MatchResult> instanceMatchResult() {
        return Optional.ofNullable(instanceMatchResult);
    }

    // Combined assessment
    public boolean fullySatisfied() {
        return contractSatisfied() && matchesExpected();
    }
}
```

### Builder Changes

```java
public class UseCaseOutcomeBuilder<I, R> {

    private Object expectedValue;
    private ResultExtractor<R, ?> resultExtractor;
    private VerificationMatcher<?> matcher = new JsonMatcher();

    // New method
    public <T> UseCaseOutcomeBuilder<I, R> expecting(
            T expected,
            ResultExtractor<R, T> extractor) {
        this.expectedValue = expected;
        this.resultExtractor = extractor;
        return this;
    }

    // With custom matcher
    public <T> UseCaseOutcomeBuilder<I, R> expecting(
            T expected,
            ResultExtractor<R, T> extractor,
            VerificationMatcher<T> matcher) {
        this.expectedValue = expected;
        this.resultExtractor = extractor;
        this.matcher = matcher;
        return this;
    }

    public UseCaseOutcome<R> build() {
        // ... existing build logic ...

        // New: evaluate instance match if expected provided
        MatchResult matchResult = null;
        if (expectedValue != null && result != null) {
            Object actual = resultExtractor.extract(result);
            matchResult = matcher.compare(actual, expectedValue);
        }

        return new UseCaseOutcome<>(
            result,
            postconditionResults,
            contractSatisfied,
            expectedValue,
            matchResult
        );
    }
}
```

## Impact on Experiment Types

### MEASURE

Output now includes both metrics:

```yaml
statistics:
  contractConformance:
    observed: 0.9200
    standardError: 0.0086
    confidenceInterval95: [0.9032, 0.9368]
  instanceConformance:          # NEW - only if expected values provided
    observed: 0.8500
    standardError: 0.0113
    confidenceInterval95: [0.8279, 0.8721]
  successes: 850                # Both contract AND instance satisfied
  contractOnlySuccesses: 70     # Contract satisfied, instance mismatch
  failures: 80                  # Contract failed
```

### EXPLORE

Comparative output includes both dimensions:

```yaml
configurations:
  - model: gpt-4o
    temperature: 0.3
    statistics:
      contractPassRate: 0.9000
      instanceMatchRate: 0.7500    # NEW

  - model: gpt-4o-mini
    temperature: 0.5
    statistics:
      contractPassRate: 0.8500
      instanceMatchRate: 0.6000    # NEW
```

### OPTIMIZE

Optimization can target either metric:

```java
@OptimizeExperiment(
    objective = Objective.MAXIMIZE,
    scorer = InstanceMatchRateScorer.class,  // Optimize for golden match rate
    controlFactor = "systemPrompt"
)
```

Or a weighted combination:

```java
scorer = WeightedScorer.of(
    0.3, ContractPassRateScorer.class,
    0.7, InstanceMatchRateScorer.class
)
```

### @ProbabilisticTest

Tests can assert on either or both:

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.85)
void testTranslation(ShoppingBasketUseCase useCase) {
    var outcome = useCase.translateInstruction("add milk", expectedJson);

    // Option 1: Assert contract only (existing behavior)
    assertThat(outcome.contractSatisfied()).isTrue();

    // Option 2: Assert instance match
    assertThat(outcome.matchesExpected()).isTrue();

    // Option 3: Assert both
    assertThat(outcome.fullySatisfied()).isTrue();
}
```

## File Structure

Minimal additions to existing packages:

```
org.javai.punit
├── api/
│   ├── VerificationMatcher.java      # Interface
│   └── ResultExtractor.java          # Functional interface
│
├── experiment/engine/
│   └── matcher/
│       ├── JsonMatcher.java          # Default implementation
│       └── StringMatcher.java        # Simple string comparison
│
└── (existing packages unchanged)
```

## Dependency Management

zjsonpatch is optional:

```kotlin
// build.gradle.kts
compileOnly("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
```

`JsonMatcher` fails fast with helpful message if dependency missing.

## Implementation Plan

### Phase 1: Core Matcher Infrastructure

1. `VerificationMatcher` interface with `MatchResult`
2. `JsonMatcher` using zjsonpatch
3. `StringMatcher` for simple cases
4. `ResultExtractor` functional interface

### Phase 2: UseCaseOutcome Enhancement

1. Add `expecting()` methods to builder
2. Add instance match fields to `UseCaseOutcome`
3. Evaluate match during `build()`
4. Update `UseCaseOutcome` accessors

### Phase 3: Output Writer Updates

1. Update `MeasureOutputWriter` to include instance conformance stats
2. Update `ExploreOutputWriter` to include match rate per config
3. Update `OptimizeOutputWriter` to include match rate per iteration
4. Add `InstanceMatchRateScorer` for optimization

### Phase 4: Documentation and Examples

1. Update ShoppingBasketUseCase example
2. Document the two-level conformance model

## Success Criteria

- [ ] `VerificationMatcher` interface with `MatchResult` record
- [ ] `JsonMatcher` with zjsonpatch, readable diff output
- [ ] `ResultExtractor` functional interface
- [ ] `UseCaseOutcome.expecting()` builder methods
- [ ] `UseCaseOutcome.matchesExpected()` and `fullySatisfied()` accessors
- [ ] MEASURE output includes instance conformance stats when applicable
- [ ] EXPLORE output includes match rate per configuration
- [ ] OPTIMIZE can score by instance match rate
- [ ] @ProbabilisticTest can assert on instance match
- [ ] Optional zjsonpatch dependency
- [ ] Tests for all new functionality

---

*Related: [Use Case Outcome Design](./DESIGN-USE-CASE-OUTCOME.md) | [Parameterization Design](./DESIGN-PARAMETERIZATION.md)*
