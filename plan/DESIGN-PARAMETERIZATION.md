# Design: Test Parameterization for Experiments and Probabilistic Tests

This document captures the design for clarifying and refactoring how measure experiments and probabilistic tests are parameterized with test inputs.

## Motivation

### The Semantic Confusion

Currently, PUnit uses `@Factor` for two conceptually different things:

1. **Use case configuration** — model, temperature, systemPrompt (legitimate)
2. **Experiment and test inputs** — instruction, query data (semantic stretch)

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
- Misses an opportunity for a cleaner, dedicated mechanism

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

### 2. Test Inputs Use `@InputSource`

A dedicated PUnit annotation provides test input data to experiments and probabilistic tests. This avoids the complexity of composing JUnit's `@ParameterizedTest` (which is also a TestTemplateInvocationContextProvider) with PUnit's experiment extensions.

### 3. Remove @Factor on Method Parameters

The ability to place `@Factor` on experiment method parameters will be removed. Test inputs should use `@InputSource` instead.

## The `@InputSource` Annotation

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InputSource {
    /**
     * Name of a static method that provides input values.
     * The method should return Stream<T>, Iterable<T>, or T[] where T
     * matches the input parameter type.
     */
    String value() default "";

    /**
     * Classpath resource path to a data file.
     * Supported formats (detected by extension):
     * - .json: JSON array, each element deserialized to input parameter type
     * - .csv: CSV with headers matching record component names
     */
    String file() default "";
}
```

### Type Inference

The framework infers the input type from the method parameter — no explicit `type` attribute needed:

```java
@InputSource(file = "golden/shopping.json")
void measure(ShoppingBasketUseCase useCase, TranslationInput input) {
    //                                       ^^^^^^^^^^^^^^^^
    //                                       Framework inspects this parameter's type
    //                                       and deserializes JSON to TranslationInput
}
```

### File Format Detection

The file extension determines the parsing strategy:

| Extension | Format           | Parser               |
|-----------|------------------|----------------------|
| `.json`   | JSON array       | Jackson ObjectMapper |
| `.csv`    | CSV with headers | Jackson CsvMapper    |

## Usage Patterns

### Pattern 1: Simple String Input (Method Source)

For single-value inputs without expected values:

```java
@MeasureExperiment(samples = 1000)
@InputSource("instructions")
void measureBaseline(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction);
}

static Stream<String> instructions() {
    return Stream.of(
        "add milk to my basket",
        "remove the bread",
        "I need 6 eggs",
        "clear my cart"
    );
}
```

### Pattern 2: Record Input (Method Source)

For multiple fields and/or expected values, define a record:

```java
record TranslationInput(String instruction, String expected) {}

@MeasureExperiment(samples = 1000)
@InputSource("goldenInputs")
void measureWithGolden(ShoppingBasketUseCase useCase, TranslationInput input) {
    useCase.translateInstruction(input.instruction(), input.expected());
}

static Stream<TranslationInput> goldenInputs() {
    return Stream.of(
        new TranslationInput("add milk", """{"action":"addItem","product":"milk"}"""),
        new TranslationInput("remove bread", """{"action":"removeItem","product":"bread"}"""),
        new TranslationInput("I need 6 eggs", """{"action":"addItem","product":"eggs","quantity":6}""")
    );
}
```

### Pattern 3: JSON File Source

Load inputs from a JSON file on the classpath:

```java
record TranslationInput(String instruction, String expected) {}

@MeasureExperiment(samples = 1000)
@InputSource(file = "golden/shopping.json")
void measureFromFile(ShoppingBasketUseCase useCase, TranslationInput input) {
    useCase.translateInstruction(input.instruction(), input.expected());
}
```

**`golden/shopping.json`:**
```json
[
  {"instruction": "add milk", "expected": "{\"action\":\"addItem\",\"product\":\"milk\"}"},
  {"instruction": "remove bread", "expected": "{\"action\":\"removeItem\",\"product\":\"bread\"}"},
  {"instruction": "I need 6 eggs", "expected": "{\"action\":\"addItem\",\"product\":\"eggs\",\"quantity\":6}"}
]
```

### Pattern 4: CSV File Source

Load inputs from a CSV file — headers map to record component names:

```java
record TranslationInput(String instruction, String expected) {}

@MeasureExperiment(samples = 1000)
@InputSource(file = "golden/shopping.csv")
void measureFromCsv(ShoppingBasketUseCase useCase, TranslationInput input) {
    useCase.translateInstruction(input.instruction(), input.expected());
}
```

**`golden/shopping.csv`:**
```csv
instruction,expected
"add milk","{""action"":""addItem"",""product"":""milk""}"
"remove bread","{""action"":""removeItem"",""product"":""bread""}"
"I need 6 eggs","{""action"":""addItem"",""product"":""eggs"",""quantity"":6}"
```

### Pattern 5: Complex Multi-Field Input

For use cases with multiple input parameters, the record captures all of them:

```java
record OrderInput(String customerId, String productId, int quantity, String expectedResponse) {}

@MeasureExperiment(samples = 500)
@InputSource(file = "orders/test-orders.json")
void measureOrderProcessing(OrderUseCase useCase, OrderInput input) {
    useCase.processOrder(input.customerId(), input.productId(), input.quantity(), input.expectedResponse());
}
```

### Pattern 6: With Probabilistic Tests

Works identically with `@ProbabilisticTest`:

```java
record TranslationInput(String instruction, String expected) {}

@ProbabilisticTest(samples = 100, minPassRate = 0.85)
@InputSource("goldenInputs")
void testTranslation(ShoppingBasketUseCase useCase, TranslationInput input) {
    var outcome = useCase.translateInstruction(input.instruction(), input.expected());
    assertThat(outcome.fullySatisfied()).isTrue();
}

static Stream<TranslationInput> goldenInputs() {
    return Stream.of(
        new TranslationInput("add milk", """{"action":"addItem","product":"milk"}"""),
        new TranslationInput("remove bread", """{"action":"removeItem","product":"bread"}""")
    );
}
```

## The Record Pattern

Records are the **recommended approach** for test inputs because they:

| Benefit              | Description                                          |
|----------------------|------------------------------------------------------|
| **Self-documenting** | Record components clearly define the input structure |
| **Type-safe**        | Compile-time checking, refactor-friendly             |
| **JSON-compatible**  | Jackson deserializes JSON directly to records        |
| **CSV-compatible**   | Headers map to component names automatically         |
| **Single parameter** | One parameter regardless of input complexity         |

### When to Use Each Pattern

| Scenario                          | Recommended Pattern                     |
|-----------------------------------|-----------------------------------------|
| Single string input, no expected  | `Stream<String>` method source          |
| Multiple fields or expected value | Record with method source               |
| Large dataset or shared test data | Record with file source (.json or .csv) |

## Sample Distribution Rule

When combining `@InputSource` with experiments or probabilistic tests:

- **samples** = total samples to collect
- **input count** = number of inputs from source
- **samples per input** = samples / input count

### Example

```java
@MeasureExperiment(samples = 1000)
@InputSource("inputs")  // 100 inputs
void measure(UseCase useCase, TestInput input) { ... }
```

Result: 1000 samples / 100 inputs = **10 samples per input**

This distributes the sampling effort across the input space, giving a representative baseline across diverse inputs.

### Rounding Behavior

If samples don't divide evenly:
- 1000 samples / 17 inputs = 58 samples per input (with remainder distributed to early inputs)
- Total samples collected will equal the requested amount

## Impact on Experiment Types

### MEASURE

```java
record TranslationInput(String instruction, String expected) {}

@MeasureExperiment(samples = 1000)
@InputSource("goldenInputs")
void measureBaseline(ShoppingBasketUseCase useCase, TranslationInput input) {
    useCase.translateInstruction(input.instruction(), input.expected());
}
```

Output aggregates across all inputs:

```yaml
execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  inputsCount: 50           # Number of distinct inputs
  samplesPerInput: 20       # Samples per input

statistics:
  # Aggregate across all inputs
  observed: 0.9200
  standardError: 0.0086
```

### EXPLORE

Factors vary configuration; inputs vary test data:

```java
record TranslationInput(String instruction) {}

@ExploreExperiment(samples = 20)
@InputSource("testInputs")
void exploreModels(
        ShoppingBasketUseCase useCase,  // Factors injected: model, temperature
        TranslationInput input) {
    useCase.translateInstruction(input.instruction());
}
```

This creates a cross-product:
- Each factor combination (model × temperature)
- Tested with each input
- 20 samples total per factor combination, distributed across inputs

### @ProbabilisticTest

```java
record TranslationInput(String instruction, String expected) {}

@ProbabilisticTest(samples = 100, minPassRate = 0.85)
@InputSource("goldenInputs")
void testTranslation(ShoppingBasketUseCase useCase, TranslationInput input) {
    var outcome = useCase.translateInstruction(input.instruction(), input.expected());
    assertThat(outcome.fullySatisfied()).isTrue();
}
```

## Technical Implementation

### InputSourceResolver

A new component that resolves `@InputSource` to a list of input values:

```java
class InputSourceResolver {

    /**
     * Resolves inputs from method source or file source.
     *
     * @param annotation the @InputSource annotation
     * @param testClass the test class (for method lookup)
     * @param inputType the input parameter type (for deserialization)
     * @return list of input values
     */
    List<Object> resolve(InputSource annotation, Class<?> testClass, Class<?> inputType) {
        if (!annotation.value().isEmpty()) {
            return resolveMethodSource(annotation.value(), testClass);
        } else if (!annotation.file().isEmpty()) {
            return resolveFileSource(annotation.file(), inputType);
        }
        throw new IllegalArgumentException("@InputSource requires either value() or file()");
    }

    private List<Object> resolveFileSource(String path, Class<?> inputType) {
        if (path.endsWith(".json")) {
            return loadJson(path, inputType);
        } else if (path.endsWith(".csv")) {
            return loadCsv(path, inputType);
        }
        throw new IllegalArgumentException("Unsupported file format: " + path);
    }
}
```

### Integration Points

The experiment extensions detect `@InputSource` and:
1. Resolve all inputs at the start of the experiment
2. Calculate samples per input
3. Iterate through inputs, running the appropriate number of samples for each
4. Aggregate statistics across all inputs

### Parameter Resolution Order

For a method like:
```java
void measure(ShoppingBasketUseCase useCase, TranslationInput input)
```

1. `useCase` — resolved by PUnit's use case parameter resolver
2. `input` — resolved from `@InputSource`

The framework identifies the input parameter as the first parameter that:
- Is not a use case type
- Is not annotated with `@Factor`

## Migration from @Factor on Method Parameters

### Before

```java
@MeasureExperiment(samples = 1000)
void measure(UseCase useCase, @Factor String instruction) { ... }
```

### After

```java
@MeasureExperiment(samples = 1000)
@InputSource("instructions")
void measureBaseline(ShoppingBasketUseCase useCase, String instruction) {
    useCase.translateInstruction(instruction);
}

static Stream<String> instructions() {
    return Stream.of("add milk", "remove bread", ...);
}
```

## Implementation Plan

### Phase 1: Core Infrastructure

1. Create `@InputSource` annotation in `org.javai.punit.api`
2. Implement `InputSourceResolver` for method and file sources
3. Add Jackson CSV dependency for CSV parsing
4. Create unit tests for resolver

### Phase 2: Extension Integration

1. Integrate `InputSourceResolver` with `MeasureStrategy`
2. Integrate with `ExploreStrategy`
3. Integrate with `ProbabilisticTestExtension`
4. Implement sample distribution logic
5. Remove support for `@Factor` on method parameters
6. Create integration tests

### Phase 3: Documentation and Examples

1. Update existing examples to use `@InputSource`
2. Add golden dataset examples (JSON and CSV)

## Success Criteria

- [ ] `@InputSource` annotation created with `value()` and `file()` attributes
- [ ] Method source resolution works with `Stream<T>`, `Iterable<T>`, and arrays
- [ ] JSON file source works with automatic type inference
- [ ] CSV file source works with header-to-component mapping
- [ ] Sample distribution across inputs works correctly
- [ ] Integration with `@MeasureExperiment` complete
- [ ] Integration with `@ExploreExperiment` complete
- [ ] Integration with `@ProbabilisticTest` complete
- [ ] `@Factor` on method parameters removed
- [ ] Output includes input count and samples-per-input metrics

## Summary

| Before                             | After                                     |
|------------------------------------|-------------------------------------------|
| `@Factor` for configuration + data | `@Factor` for configuration only          |
| Semantic confusion                 | Clear separation of concerns              |
| No file loading support            | Built-in JSON and CSV file sources        |
| Positional parameter binding       | Type-safe record pattern                  |

The refactoring achieves:
- **Clarity** — Factors configure, `@InputSource` provides test data
- **Type Safety** — Records define input structure with compile-time checking
- **Flexibility** — Method source, JSON files, or CSV files
- **Simplicity** — Type inference eliminates boilerplate
- **Composability** — Works with all experiment types and probabilistic tests

---

*Related: [Instance Match Design](./DESIGN-INSTANCE-MATCH.md) | [Use Case Outcome Design](./DESIGN-USE-CASE-OUTCOME.md)*
