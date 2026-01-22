# Contract System Migration Plan

## Overview

This document outlines the plan to consolidate the duplicate type systems in PUnit:
- **Old system** (`org.javai.punit.model`): `CriterionOutcome`, `UseCaseCriteria`, `UseCaseOutcome`
- **New system** (`org.javai.punit.contract`): `PostconditionResult`, `PostconditionEvaluator<R>`, `UseCaseOutcome<R>`

The goal is to eliminate redundancy by adopting the new typed contract system as the canonical approach.

## Current State

### Old Types (model package) - TO BE REMOVED

```
CriterionOutcome (sealed interface)
├── Passed(description)
├── Failed(description, reason)
├── Errored(description, cause)
└── NotEvaluated(description)

UseCaseCriteria (interface)
├── entries(): List<Entry<String, Supplier<Boolean>>>
├── evaluate(): List<CriterionOutcome>
└── allPassed(): boolean

UseCaseOutcome (record)
├── result: UseCaseResult
└── criteria: UseCaseCriteria

UseCaseContract (interface) - DEAD, DELETE
```

### New Types (contract package) - CANONICAL

```
PostconditionResult (record) - SIMPLIFIED
├── description: String
└── outcome: Outcome<?>              ← leverages existing Outcome type

PostconditionEvaluator<R> (interface)
├── evaluate(R result): List<PostconditionResult>
└── postconditionCount(): int

UseCaseOutcome<R> (record)
├── result: R                        ← typed!
├── executionTime: Duration
├── metadata: Map<String, Object>
└── postconditionEvaluator: PostconditionEvaluator<R>

UseCaseResult (record) - MOVE FROM MODEL
├── values: Map<String, Object>
├── metadata: Map<String, Object>
├── timestamp: Instant
└── executionTime: Duration
```

## Design Decisions

### 1. PostconditionResult Simplification

**Decision**: Simplify `PostconditionResult` to use `Outcome<?>` internally.

```java
// Before: sealed interface with 3 variants
sealed interface PostconditionResult {
    record Passed(String description) ...
    record Failed(String description, String reason) ...
    record Skipped(String description, String reason) ...
}

// After: single record wrapping Outcome
record PostconditionResult(String description, Outcome<?> outcome) {
    boolean passed() { return outcome.isSuccess(); }
    boolean failed() { return outcome.isFailure(); }
}
```

**Rationale**: The `Outcome<?>` type already provides:
- Success/failure semantics
- Failure reason via `failureMessage()`
- Optional result value for derivations

The "Skipped" case becomes a failure with reason "Derivation 'X' failed" - no special handling needed.

### 2. UseCaseResult Location

**Decision**: Move `UseCaseResult` from `model` package to `contract` package.

`UseCaseResult` (Map-based result container) is useful for:
- Storing arbitrary named values when a typed result isn't appropriate
- Metadata storage
- Execution timing

It coexists with `UseCaseOutcome<R>` for different use cases:
- `UseCaseOutcome<R>` - when you have a typed result
- `UseCaseResult` - when you need a bag of named values

### 3. UseCaseCriteria Replacement

**Decision**: Replace `UseCaseCriteria` with `PostconditionEvaluator<R>`.

Key improvement: Postconditions are defined as `Predicate<R>` that receive the result as a parameter, rather than `Supplier<Boolean>` that close over external state.

```java
// Old: Criteria close over result via closure
UseCaseCriteria criteria = UseCaseCriteria.ordered()
    .criterion("Not empty", () -> result.getString("response", "").length() > 0)
    .build();

// New: Postconditions receive result as parameter
ServiceContract<Input, String> contract = ServiceContract.<Input, String>define()
    .ensure("Not empty", response -> !response.isEmpty())
    .build();
```

### 4. UseCaseContract Interface

**Decision**: Delete. The `ServiceContract<I, R>` class fully replaces it.

## Migration Phases

### Phase 1: Simplify PostconditionResult ✅ COMPLETE

**Goal**: Refactor `PostconditionResult` from sealed interface to record with `Outcome<?>`.

Tasks:
- [x] Replace sealed interface with `record PostconditionResult(String description, Outcome<?> outcome)`
- [x] Add convenience methods: `passed()`, `failed()`, `failureReason()`
- [x] Add factory methods: `passed(description)`, `passed(description, value)`, `failed(description, reason)`
- [x] Update all usages in contract package (Postcondition, Derivation, etc.)
- [x] Update tests
- [x] Added `Outcomes.okVoid()` for void success results

Files affected:
- `src/main/java/org/javai/punit/contract/PostconditionResult.java`
- `src/main/java/org/javai/punit/contract/Postcondition.java`
- `src/main/java/org/javai/punit/contract/Derivation.java`
- `src/main/java/org/javai/punit/contract/Outcomes.java`
- `src/test/java/org/javai/punit/contract/PostconditionResultTest.java`

### Phase 2: Migrate CriteriaOutcomeAggregator ✅ COMPLETE

**Goal**: Update aggregator to use `PostconditionResult` instead of `CriterionOutcome`.

Tasks:
- [x] Created `PostconditionAggregator` with `record(List<PostconditionResult>)` method
- [x] Created `PostconditionStats` inner class (renamed from `CriterionStats`)
- [x] Added deprecated bridge method for `UseCaseCriteria` compatibility
- [x] Updated `ExperimentResultAggregator` to use `PostconditionAggregator`
- [x] Updated `CriteriaReporter` with new methods for `PostconditionResult`
- [x] Created tests for `PostconditionAggregator`

Files affected:
- `src/main/java/org/javai/punit/spec/criteria/PostconditionAggregator.java` (NEW)
- `src/main/java/org/javai/punit/spec/criteria/CriteriaReporter.java`
- `src/main/java/org/javai/punit/experiment/engine/ExperimentResultAggregator.java`
- `src/test/java/org/javai/punit/spec/criteria/PostconditionAggregatorTest.java` (NEW)

### Phase 3: Migrate ExperimentResultAggregator ✅ COMPLETE

**Goal**: Update to use `PostconditionEvaluator<R>` instead of `UseCaseCriteria`.

Tasks:
- [x] Added `recordPostconditions(List<PostconditionResult>)` method
- [x] Added `getPostconditionAggregator()`, `hasPostconditionStats()`, `getPostconditionPassRates()`
- [x] Deprecated legacy methods: `recordCriteria()`, `getCriteriaAggregator()`, `hasCriteriaStats()`, `getCriteriaPassRates()`
- [x] Updated `ResultCaptor` with postcondition accessors
- [x] Updated `ResultRecorder` to prioritize postconditions over legacy criteria

Files affected:
- `src/main/java/org/javai/punit/experiment/engine/ExperimentResultAggregator.java`
- `src/main/java/org/javai/punit/api/ResultCaptor.java`
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`

### Phase 4: Migrate ResultCaptor ✅ COMPLETE

**Goal**: Update to work with contract types as primary, legacy types as secondary.

Tasks:
- [x] Created `OutcomeCaptor` as the new primary class
- [x] Made `record(contract.UseCaseOutcome<R>)` the primary method
- [x] Deprecated `ResultCaptor` (now extends `OutcomeCaptor` for backward compatibility)
- [x] Deprecated legacy methods: `recordContract()`, `record(model.UseCaseOutcome)`, `record(UseCaseResult)`, `recordCriteria()`
- [x] Added new methods: `hasContractOutcome()`, `getPostconditionResults()`, `allPostconditionsPassed()`
- [x] Updated tests with deprecation suppressions

Files affected:
- `src/main/java/org/javai/punit/api/OutcomeCaptor.java` (NEW)
- `src/main/java/org/javai/punit/api/ResultCaptor.java` (deprecated alias)
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`

### Phase 5: Migrate ResultRecorder ✅ COMPLETE

**Goal**: Update to determine success from `PostconditionResult` instead of `CriterionOutcome`.

Tasks:
- [x] Updated `recordResult()` to prioritize contract postconditions
- [x] Added `determineFailureCategoryFromPostconditions()` method
- [x] Uses `OutcomeCaptor` as parameter type
- [x] Created comprehensive `ResultRecorderTest`
- [x] Fixed `OutcomeCaptor.hasResult()` to distinguish results from exceptions

Files affected:
- `src/main/java/org/javai/punit/experiment/engine/shared/ResultRecorder.java`
- `src/main/java/org/javai/punit/api/OutcomeCaptor.java`
- `src/test/java/org/javai/punit/experiment/engine/shared/ResultRecorderTest.java` (NEW)

### Phase 6: Migrate Criteria Infrastructure ✅ COMPLETE

**Goal**: Update or remove criteria-related classes that depend on old types.

**Assessment**: All four classes were UNUSED in production code:
- `CriteriaInvoker` - designed for old `criteria()` method pattern, not used
- `CriteriaResolver` - designed to resolve `criteria()` methods, not used
- `CriteriaOutcomeAggregator` - superseded by `PostconditionAggregator`, not used
- `CriteriaReporter` - formatting utilities never instantiated

Tasks:
- [x] Evaluated `CriteriaInvoker` - DELETE (not used, old pattern)
- [x] Evaluated `CriteriaResolver` - DELETE (not used, old pattern)
- [x] Evaluated `CriteriaOutcomeAggregator` - DELETE (superseded by `PostconditionAggregator`)
- [x] Evaluated `CriteriaReporter` - DELETE (not used)
- [x] Deleted all four classes and their tests
- [x] Verified build and tests pass

Files deleted:
- `src/main/java/org/javai/punit/spec/criteria/CriteriaInvoker.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaResolver.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaOutcomeAggregator.java`
- `src/main/java/org/javai/punit/spec/criteria/CriteriaReporter.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaInvokerTest.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaResolverTest.java`
- `src/test/java/org/javai/punit/spec/criteria/CriteriaOutcomeAggregatorTest.java`

### Phase 7: Delete UseCaseContract Interface ✅ COMPLETE

**Goal**: Remove the dead interface.

Tasks:
- [x] Deleted `UseCaseContract.java`
- [x] Removed `implements UseCaseContract` from `ShoppingBasketUseCase`
- [x] Removed `implements UseCaseContract` from `PaymentGatewayUseCase`
- [x] Updated `package-info.java` in examples/usecases
- [x] Updated `USER-GUIDE.md` to remove `UseCaseContract` references
- [x] Verified build and tests pass

Files deleted:
- `src/main/java/org/javai/punit/api/UseCaseContract.java`

Files modified:
- `src/test/java/org/javai/punit/examples/usecases/ShoppingBasketUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/PaymentGatewayUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/package-info.java`
- `docs/USER-GUIDE.md`

### Phase 8: Migrate Optimize Strategy Classes ✅ COMPLETE (via bridge)

**Goal**: Update optimization infrastructure to use contract types.

**Assessment**: The optimize infrastructure already works with contract-based outcomes
through the `OutcomeCaptor` bridge:

1. When a use case returns `contract.UseCaseOutcome<R>`, it's recorded via `OutcomeCaptor.record()`
2. `OutcomeCaptor` converts the contract outcome to `UseCaseResult` + `UseCaseCriteria`
3. `OptimizeStrategy.intercept()` reads from the captor and constructs `model.UseCaseOutcome`
4. The rest of the optimize pipeline works unchanged

This bridge approach means:
- [x] Optimize experiments work with BOTH old and new use case patterns
- [x] No changes needed to optimize code for Phase 9 (migrate use cases)
- [ ] Full migration to contract types deferred to Phase 11 (when deleting old types)

The optimize code will be updated to directly use `contract.UseCaseOutcome<?>` when
we delete `model.UseCaseOutcome` in Phase 11, forcing the refactoring at that point.

### Phase 9: Migrate Example Use Cases ✅ COMPLETE

**Goal**: Update example use cases to demonstrate the new contract pattern.

Tasks:
- [x] Migrated `ShoppingBasketUseCase` to use `ServiceContract` and `contract.UseCaseOutcome<TranslationResult>`
  - Created `TranslationResult` record as typed result
  - Replaced `UseCaseCriteria.ordered()` with `ServiceContract.define()`
  - Uses method references for postconditions: `TranslationResult::isValidJson`
- [x] Migrated `PaymentGatewayUseCase` to use `ServiceContract` and `contract.UseCaseOutcome<PaymentResult>`
  - Uses existing `PaymentResult` record as typed result
  - Single postcondition: `PaymentResult::success`
- [x] Added `assertAll()` method to `contract.UseCaseOutcome` for test compatibility
- [x] All tests pass (experiments use captor bridge for backward compatibility)

Files modified:
- `src/test/java/org/javai/punit/examples/usecases/ShoppingBasketUseCase.java`
- `src/test/java/org/javai/punit/examples/usecases/PaymentGatewayUseCase.java`
- `src/main/java/org/javai/punit/contract/UseCaseOutcome.java`

### Phase 10: Move UseCaseResult to Contract Package ⏭️ DEFERRED

**Goal**: Relocate `UseCaseResult` from model to contract package.

**Assessment**: After review, `UseCaseResult` is NOT part of the user-facing contract API.
It's used entirely by internal infrastructure:
- `OutcomeCaptor` bridge layer
- `ResultRecorder` and experiment aggregation
- Optimize infrastructure
- Spec model classes

The new contract pattern uses typed results (`TranslationResult`, `PaymentResult`, etc.),
not `UseCaseResult`. Moving it to the contract package would be misleading.

**Decision**: Keep `UseCaseResult` in the model package. It serves as internal infrastructure
for backward compatibility and experiment aggregation. A future refactoring could:
1. Rename to something like `ResultBag` to clarify its purpose
2. Make it internal/package-private
3. Replace with a simpler interface if the current implementation is over-engineered

For now, skip this phase and proceed with Phase 11 (delete obsolete types).

### Phase 11: Delete Old Types ⏭️ DEFERRED

**Goal**: Remove the redundant old types from model package.

**Assessment**: After thorough analysis, the old types cannot be safely deleted yet.
They are deeply embedded in the optimize infrastructure:

**Dependencies on old types:**

| Type | Used By | Can Delete? |
|------|---------|-------------|
| `model.UseCaseOutcome` | `OptimizeState`, `OptimizeStrategy`, `OptimizationOutcomeAggregator`, `DefaultOptimizationOutcomeAggregator`, `UseCaseExecutor` | No |
| `UseCaseCriteria` | `OutcomeCaptor`, `ResultRecorder`, `OptimizeStrategy`, `ExperimentResultAggregator`, `PostconditionAggregator` | No |
| `CriterionOutcome` | `ResultRecorder`, `PostconditionResultAdapter`, `ContractCriteriaAdapter` | No |
| `PostconditionResultAdapter` | `ContractCriteriaAdapter` | No |
| `ContractCriteriaAdapter` | `OutcomeCaptor` (bridge) | No |

**The Bridge Pattern:**

The Phase 8 assessment was correct: the `OutcomeCaptor` bridge allows both old and new
patterns to coexist. The adapters (`PostconditionResultAdapter`, `ContractCriteriaAdapter`)
are essential for this bridge:

1. User code uses `contract.UseCaseOutcome<R>` (new pattern)
2. `OutcomeCaptor.record()` calls `ContractCriteriaAdapter.from(outcome)`
3. Adapter converts postconditions to `UseCaseCriteria` interface
4. Optimize/experiment infrastructure works unchanged with legacy types

**Path to Full Migration:**

To eventually delete the old types, the following major refactoring would be needed:

1. **Migrate optimize infrastructure to contract types:**
   - Create `OptimizationPostconditionAggregator` that works with `contract.UseCaseOutcome<?>`
   - Update `OptimizeState` to use `List<contract.UseCaseOutcome<?>>` instead of `List<model.UseCaseOutcome>`
   - Update `OptimizeStrategy` to work directly with contract outcomes
   - Update all scorers and mutators

2. **Migrate experiment infrastructure:**
   - Update `ExperimentResultAggregator` to drop legacy criteria support
   - Update `ResultRecorder` to remove legacy criteria path

3. **Migrate OutcomeCaptor:**
   - Remove `UseCaseCriteria` field and related methods
   - Remove backward compatibility with legacy outcomes

4. **Delete adapters and old types:**
   - Delete `PostconditionResultAdapter`, `ContractCriteriaAdapter`
   - Delete `CriterionOutcome`, `UseCaseCriteria`, `model.UseCaseOutcome`

**Decision**: Defer this phase. The bridge pattern works well and provides backward
compatibility. The old types can be deprecated and eventually removed when:
- The optimize infrastructure is refactored to use contract types directly
- All external consumers have migrated to the new contract pattern

For now, mark the old types and legacy methods as `@Deprecated(forRemoval = true)`.

Tasks (completed for this iteration):
- [x] Analyzed dependencies to understand scope
- [x] Documented the bridge pattern and migration path
- [x] Updated `OptimizeStrategy` with helper method for clarity
- [ ] (Future) Add deprecation annotations to old types
- [ ] (Future) Refactor optimize infrastructure to use contract types
- [ ] (Future) Delete old types when no longer needed

### Phase 12: Documentation and Cleanup ✅ COMPLETE

**Goal**: Update documentation to reflect the new architecture.

Tasks:
- [x] Updated migration plan with assessment of each phase
- [x] Documented bridge pattern and its purpose
- [x] Example use cases demonstrate new contract pattern (`ShoppingBasketUseCase`, `PaymentGatewayUseCase`)
- [x] USER-GUIDE.md updated (Phase 7)
- [x] CLAUDE.md unchanged (no updates needed)

## Risk Mitigation

### Backward Compatibility

The bridge pattern provides full backward compatibility:
- New use cases return `contract.UseCaseOutcome<R>` with typed results
- `OutcomeCaptor` bridges to legacy types via `ContractCriteriaAdapter`
- Optimize infrastructure works unchanged with both patterns
- Legacy use cases (returning `model.UseCaseOutcome`) continue to work

### Testing Strategy

- ✅ Full test suite passes after each phase
- ✅ Each phase was independently committable
- ✅ New tests written for migrated functionality

## Success Criteria (Revised)

1. ✅ All tests pass
2. ✅ Example use cases demonstrate the new contract pattern
3. ✅ Bridge pattern allows gradual migration without breaking changes
4. ⏳ Old types will be removed in future iteration when optimize infrastructure is refactored

## Resolved Questions

1. **~~Errored state~~**: No. Use `Outcome<?>` which captures failure reasons. Exception details go in the failure message.

2. **~~UseCaseResult~~**: Keep in model package. It's internal infrastructure, not part of the contract API.

3. **~~UseCaseContract interface~~**: Deleted. `ServiceContract<I, R>` replaces it.

4. **~~Lazy evaluation~~**: Both systems are lazy (evaluate on demand). No design change needed.

5. **~~Delete old types~~**: Deferred. Bridge pattern provides backward compatibility. Full deletion requires optimize infrastructure refactoring.

## Migration Summary

| Phase | Status | Description |
|-------|--------|-------------|
| 1 | ✅ Complete | Simplify PostconditionResult to use Outcome<?> |
| 2 | ✅ Complete | Migrate CriteriaOutcomeAggregator → PostconditionAggregator |
| 3 | ✅ Complete | Migrate ExperimentResultAggregator |
| 4 | ✅ Complete | Migrate ResultCaptor → OutcomeCaptor |
| 5 | ✅ Complete | Migrate ResultRecorder |
| 6 | ✅ Complete | Delete unused criteria infrastructure |
| 7 | ✅ Complete | Delete UseCaseContract interface |
| 8 | ✅ Complete | Optimize works via bridge (no code changes needed) |
| 9 | ✅ Complete | Migrate example use cases |
| 10 | ⏭️ Deferred | UseCaseResult stays in model (internal infrastructure) |
| 11 | ⏭️ Deferred | Old types deletion (requires optimize refactoring) |
| 12 | ✅ Complete | Documentation and cleanup |

## Open Questions

None currently.
