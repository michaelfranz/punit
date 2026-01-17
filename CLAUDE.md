# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PUnit is a JUnit 5 extension framework for probabilistic unit testing of non-deterministic systems (LLMs, ML models, randomized algorithms). It runs tests multiple times and determines pass/fail based on statistical thresholds rather than binary success/failure.

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "SomeTestClass"

# Run a single test method
./gradlew test --tests "SomeTestClass.someTestMethod"

# Run experiments (MEASURE or EXPLORE mode)
./gradlew exp -Prun=ExperimentClassName
./gradlew exp -Prun=ExperimentClassName.methodName

# Publish to local Maven repository
./gradlew publishLocal

# Generate code coverage report (output: build/reports/jacoco)
./gradlew test jacocoTestReport
```

## Architecture

### Package Structure

```
org.javai.punit
├── api/                    # Public API: annotations (@ProbabilisticTest, @MeasureExperiment, etc.) and interfaces
├── ptest/engine/           # Core probabilistic test execution engine
├── experiment/             # Experimentation framework
│   ├── engine/             # Strategy pattern: ExperimentExtension delegates to mode-specific strategies
│   ├── measure/            # @MeasureExperiment strategy
│   ├── explore/            # @ExploreExperiment strategy
│   └── optimize/           # @OptimizeExperiment strategy
├── spec/                   # Specification management (YAML-based baseline specs)
├── statistics/             # Statistical computation (confidence intervals, threshold derivation)
└── engine/covariate/       # Covariate-aware baseline selection
```

### JUnit 5 Extension Mechanism

Two main extensions implement `TestTemplateInvocationContextProvider` + `InvocationInterceptor`:

1. **`ProbabilisticTestExtension`** - Executes `@ProbabilisticTest` methods N times, records pass/fail without throwing, determines final verdict
2. **`ExperimentExtension`** - Uses Strategy pattern to delegate to `MeasureStrategy`, `ExploreStrategy`, or `OptimizeStrategy`

Key insight: Individual sample failures don't throw exceptions; failures are recorded. The final verdict is determined only after all samples complete (or early termination triggers).

### Configuration Resolution Order

System property → Environment variable → Annotation value → Framework default

Example: `-Dpunit.samples=50` overrides `@ProbabilisticTest(samples=100)`

### Source Sets

- `src/main/java` - Framework code (published artifact)
- `src/test/java` - Unit tests (test subjects under `testsubjects/` are excluded from direct discovery, run via TestKit)
- `src/experiment/java` - Experiment examples (shopping use case)

### Workflow: Experiments → Specs → Tests

```
@MeasureExperiment (1000+ samples)
    ↓ produces
ExecutionSpecification (YAML in specs/ directory)
    ↓ loaded by
@ProbabilisticTest (with spec reference)
    ↓ derives threshold via
ThresholdDeriver (statistical derivation from baseline)
```

### Budget Hierarchy

Budgets are checked in order: Suite → Class → Method. First exhausted budget triggers termination.

### Key Classes

| Class                        | Role                                              |
|------------------------------|---------------------------------------------------|
| `ProbabilisticTestExtension` | Main JUnit 5 extension for @ProbabilisticTest     |
| `ExperimentExtension`        | Main extension for experiment annotations         |
| `SampleResultAggregator`     | Tracks pass/fail outcomes                         |
| `EarlyTerminationEvaluator`  | Detects impossibility or success-guaranteed       |
| `ThresholdDeriver`           | Derives thresholds from baseline using statistics |
| `CostBudgetMonitor`          | Enforces time/token budgets                       |

## Conventions

- Java 21 required
- Compile with `-parameters` flag (configured in build.gradle.kts) for use case argument injection
- Test subjects in `src/test/java/**/testsubjects/**` are executed via JUnit TestKit, not directly
- Specs output to `src/test/resources/punit/specs/` (MEASURE) and `src/test/resources/punit/explorations/` (EXPLORE)
- Construct all test assertions with assertj's `assertThat`
- All non-trivial functionality is rigorously tested using unit tests or, if dependent on resources outside the test's control, integration tests
