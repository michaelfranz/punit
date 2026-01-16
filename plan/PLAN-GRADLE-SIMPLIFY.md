# Gradle build config simplification

**STATUS: COMPLETED** ✅

## Summary

Unified the `measure` and `explore` Gradle tasks into a single `experiment` task.

## Usage

```bash
./gradlew experiment --tests "<experiment name>"
# or shorthand:
./gradlew exp --tests "<experiment name>"
```

The experiment mode (MEASURE or EXPLORE) is determined from the `@Experiment` annotation's `mode` property.

## Output Directories

- **MEASURE mode**: `src/test/resources/punit/specs/{UseCaseId}.yaml`
- **EXPLORE mode**: `src/test/resources/punit/explorations/{UseCaseId}/{config}.yaml`

---

## Original Problem

The gradle build config defined two tasks where one should suffice.

There are two types of 'experiment' in PUnit: MEASURE and EXPLORE.

A developer must express which one to use. This is done using the mode property of the Experiment annotation.

For historical reasons two different gradle tasks were used:

```bash
./gradlew clean measure --tests "<measure experiment name>"
./gradlew clean explore --tests "<explore experiment name>"
```

Since the mode can be discovered from the annotation on the experiment method, a single task suffices.
