# Design: Test Parameterization for Experiments and Probabilistic Tests

This document captures the design for clarifying and refactoring how measure experiments and probabilistic tests are parameterized with test inputs.

## Motivation

### The Semantic Confusion

Currently, PUnit uses `@Factor` for two conceptually different things:

1. **Use case configuration** — model, temperature, systemPrompt (legitimate)
2. **Test inputs** — instruction, query data (semantic stretch)

Example of the current conflation:

```java
@MeasureExperiment(samples = 1000)
void measureBaseline(ShoppingBasketUseCase useCase, @Factor String instruction) {
    useCase.translateInstruction(instruction);
}
```

Here, `instruction` is not a configuration factor — it's test data. Using `@Factor` for this:
- Muddies the conceptual model
- Makes the Factor system do double duty
- Misses an opportunity to use familiar JUnit patterns

### The Clean Distinction

| Concept        | Purpose                  | Examples                          | Injection Point             |
|----------------|--------------------------|-----------------------------------|-----------------------------|
| **Factor**     | Use case *configuration* | model, temperature, systemPrompt  | Use case instance (setters) |
| **Test Input** | Data being *processed*   | instruction, query, expected      | Method parameters           |

Factors configure how the system under test behaves. Test inputs are the data we're testing with. These should use different mechanisms.

## Design Principles

### 1. Factors Are Strictly for Configuration

Factors represent the configuration space of the use case:
- They are injected into the use case instance via `@FactorSetter` methods
- They define what varies about the system under test
- They are appropriate for EXPLORE experiments (comparing configurations)

```java
@FactorSetter
public void setModel(String model) { this.model = model; }

@FactorSetter
public void setTemperature(double temperature) { this.temperature = temperature; }
```

### 2. Test Inputs Use Standard JUnit Parameterization

For varying test data, use JUnit 5's familiar `@ParameterizedTest`:
- `@MethodSource` for complex data or loading from files
- `@CsvSource` for inline tabular data
- `@ValueSource` for simple lists

This is intuitive for Java developers and doesn't require learning PUnit-specific concepts.

### 3. Deprecate @Factor on Method Parameters

The ability to place `@Factor` on experiment method parameters should be deprecated and eventually removed. This forces the clean separation.

## The Proposed Approach

### Before (Current)

```java
@MeasureExperiment(samples = 1000)
void measureBaseline(ShoppingBasketUseCase useCase, @Factor String instruction) {
    useCase.translateInstruction(instruction);
}
```

### After (Proposed)

```java
@MeasureExperiment(samples = 1000)
@ParameterizedTest
@MethodSource("testInstructions")
void measureBaseline(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction);
}

static Stream<String> testInstructions() {
    return Stream.of(
        "add milk to my basket",
        "remove the bread",
        "I need 6 eggs",
        "clear my cart"
    );
}
```

### With Expected Values (Instance Matching)

```java
@MeasureExperiment(samples = 1000)
@ParameterizedTest
@MethodSource("goldenInputs")
void measureWithGoldenData(ShoppingBasketUseCase useCase, String instruction, String expected) {
    useCase.translateInstruction(instruction, expected);  // Uses .expecting() internally
}

static Stream<Arguments> goldenInputs() {
    return Stream.of(
        Arguments.of("add milk", "{\"action\":\"addItem\",\"product\":\"milk\",\"quantity\":1}"),
        Arguments.of("remove bread", "{\"action\":\"removeItem\",\"product\":\"bread\"}"),
        Arguments.of("I need 6 eggs", "{\"action\":\"addItem\",\"product\":\"eggs\",\"quantity\":6}")
    );
}
```

### Loading from Golden Dataset File

```java
static Stream<Arguments> goldenInputs() {
    return GoldenDataset.load("shopping_actions/actions.json")
        .stream()
        .map(entry -> Arguments.of(entry.input(), entry.expected()));
}
```

Where `GoldenDataset` is a simple utility:

```java
public class GoldenDataset {

    public record Entry(String id, String input, String expected) {}

    public static List<Entry> load(String resourcePath) {
        // Load JSON array from classpath, map to Entry records
    }
}
```

## Sample Distribution Rule

When combining `@ParameterizedTest` with experiments or probabilistic tests:

- **samples** = total samples to collect
- **parameter rows** = number of test inputs
- **samples per input** = samples / parameter rows

### Example

```java
@MeasureExperiment(samples = 1000)
@ParameterizedTest
@MethodSource("inputs")  // 100 rows
void measure(UseCase useCase, String input) { ... }
```

Result: 1000 samples / 100 inputs = **10 samples per input**

This distributes the sampling effort across the input space, giving a representative baseline across diverse inputs.

### Rounding Behavior

If samples don't divide evenly:
- 1000 samples / 17 inputs = 58 samples per input (with remainder distributed)
- Total samples collected will equal the requested amount

## Impact on Experiment Types

### MEASURE

Works with parameterized inputs:

```java
@MeasureExperiment(samples = 1000)
@ParameterizedTest
@MethodSource("instructions")
void measureBaseline(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction);
}
```

Output aggregates across all inputs:

```yaml
execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  inputsCount: 50           # NEW: number of distinct inputs
  samplesPerInput: 20       # NEW: samples per input

statistics:
  # Aggregate across all inputs
  observed: 0.9200
  standardError: 0.0086
```

### EXPLORE

Factors vary configuration; inputs vary test data:

```java
@ExploreExperiment(samples = 20)
@ParameterizedTest
@MethodSource("testInputs")
void exploreModels(
        ShoppingBasketUseCase useCase,  // Factors injected: model, temperature
        String instruction) {
    useCase.translateInstruction(instruction);
}
```

This creates a cross-product:
- Each factor combination (model × temperature)
- Tested with each input
- 20 samples total per factor combination, distributed across inputs

### @ProbabilisticTest

Works identically:

```java
@ProbabilisticTest(samples = 100, minPassRate = 0.85)
@ParameterizedTest
@MethodSource("testInputs")
void testTranslation(ShoppingBasketUseCase useCase, String instruction, String expected) {
    var outcome = useCase.translateInstruction(instruction, expected);
    assertThat(outcome.fullySatisfied()).isTrue();
}
```

## Technical Integration

### JUnit Extension Composition

Both `@ParameterizedTest` and `@MeasureExperiment` are JUnit 5 test template providers. The integration requires:

1. `@MeasureExperiment` (or `@ProbabilisticTest`) wraps the parameterized test
2. For each parameter set, the experiment runs its sampling loop
3. Samples are distributed across parameter sets

### Extension Ordering

```
@MeasureExperiment  ─┐
                    ├─→  For each parameter set from @MethodSource
@ParameterizedTest  ─┘       Run samples/paramCount iterations
                             Aggregate results
```

### Detection

The experiment extension detects `@ParameterizedTest` presence and:
- Counts parameter sets to determine distribution
- Iterates through parameters, running samples for each
- Aggregates statistics across all parameter sets

## Breaking Change: @Factor on Method Parameters

### Current Behavior (To Be Deprecated)

```java
@MeasureExperiment(samples = 1000)
void measure(UseCase useCase, @Factor String instruction) { ... }
```

### Migration Path

1. **Phase 1: Deprecate** — Mark `@Factor` on method parameters as `@Deprecated`
2. **Phase 2: Warn** — Log warnings when deprecated usage detected
3. **Phase 3: Remove** — Remove support in next major version

### Migration Example

**Before:**
```java
@MeasureExperiment(samples = 1000)
void measureBaseline(ShoppingBasketUseCase useCase, @Factor String instruction) {
    useCase.translateInstruction(instruction);
}

// Factor values from @FactorSource or similar
```

**After:**
```java
@MeasureExperiment(samples = 1000)
@ParameterizedTest
@MethodSource("instructions")
void measureBaseline(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction);
}

static Stream<String> instructions() {
    return Stream.of("add milk", "remove bread", ...);
}
```

## Golden Dataset Utility

A simple utility for loading test data from JSON files:

```java
public class GoldenDataset {

    public record Entry(String id, String input, String expected) {}

    /**
     * Loads a golden dataset from the classpath.
     *
     * Expected format:
     * [
     *   {"id": "test-1", "instruction": "add milk", "expected": {...}},
     *   {"id": "test-2", "instruction": "remove bread", "expected": {...}}
     * ]
     */
    public static List<Entry> load(String resourcePath) {
        return load(resourcePath, "instruction", "expected");
    }

    public static List<Entry> load(String resourcePath, String inputField, String expectedField) {
        // Load JSON array from classpath
        // Map each element to Entry record
        // Return list
    }

    public Stream<Entry> stream() {
        return entries.stream();
    }
}
```

Usage:

```java
static Stream<Arguments> goldenInputs() {
    return GoldenDataset.load("shopping_actions/actions.json")
        .stream()
        .map(e -> Arguments.of(e.input(), e.expected()));
}
```

This keeps the golden dataset as just a data source — no special framework integration needed beyond standard JUnit parameterization.

## Implementation Plan

### Phase 1: JUnit Integration

1. Ensure `@ParameterizedTest` composes correctly with `@MeasureExperiment`
2. Implement sample distribution across parameter rows
3. Update result aggregation to handle multiple inputs

### Phase 2: Deprecation

1. Mark `@Factor` on method parameters as `@Deprecated`
2. Add compiler/runtime warnings
3. Update documentation with migration guide

### Phase 3: Utilities

1. Implement `GoldenDataset` loader utility
2. Add examples in documentation

### Phase 4: Documentation

1. Document the Factor vs Test Input distinction clearly
2. Update existing examples to use `@ParameterizedTest`
3. Add migration guide for existing code

## Success Criteria

- [ ] `@ParameterizedTest` composes correctly with `@MeasureExperiment`
- [ ] `@ParameterizedTest` composes correctly with `@ExploreExperiment`
- [ ] `@ParameterizedTest` composes correctly with `@ProbabilisticTest`
- [ ] Sample distribution across parameter rows works correctly
- [ ] `@Factor` on method parameters deprecated with warnings
- [ ] `GoldenDataset` loader utility implemented
- [ ] Output includes input count and samples-per-input metrics
- [ ] Documentation updated with Factor vs Input distinction
- [ ] Migration guide for existing `@Factor` on parameters usage

## Summary

| Before                           | After                                |
|----------------------------------|--------------------------------------|
| `@Factor` for configuration + data | `@Factor` for configuration only   |
| PUnit-specific parameter mechanism | Standard JUnit `@ParameterizedTest` |
| New concept to learn             | Familiar JUnit patterns              |
| Semantic confusion               | Clear separation of concerns         |

The refactoring achieves:
- **Clarity** — Factors configure, parameters provide test data
- **Familiarity** — Standard JUnit patterns for Java developers
- **Simplicity** — No new PUnit-specific concepts for test inputs
- **Composability** — Works with all experiment types and probabilistic tests

---

*Related: [Instance Match Design](./DESIGN-INSTANCE-MATCH.md) | [Use Case Outcome Design](./DESIGN-USE-CASE-OUTCOME.md)*
