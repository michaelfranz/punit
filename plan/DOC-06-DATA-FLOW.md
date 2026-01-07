# Data Flow

This section describes how data flows through the punit experiment extension.

## 5.0 Canonical Flow Overview

```
┌───────────────────────────────────────────┐
│   Use Case                                │  Application code invoking
│   (invokes stochastic behavior)           │  non-deterministic behavior
└───────────────────┬───────────────────────┘
                    ↓
    ┌───────────────────────────────────────────────────────┐
    │  EXPLORATION MODE (optional)                          │
    │                                                       │
    │  ┌─────────────────────────────────────────────────┐  │
    │  │ ExperimentDesign (Factors + Levels)             │  │
    │  │ Traverse configurations to find satisfactory one│  │
    │  └───────────────────┬─────────────────────────────┘  │
    │                      ↓                                │
    │  ┌─────────────────────────────────────────────────┐  │
    │  │ ExperimentConfig (chosen configuration)         │  │
    │  └─────────────────────────────────────────────────┘  │
    │                                                       │
    │  Skip this step if configuration is already known     │
    └───────────────────────┬───────────────────────────────┘
                            ↓
    ┌───────────────────────────────────────────────────────┐
    │  BASELINE DERIVATION MODE (required)                  │
    │                                                       │
    │  Run use case many times (e.g., 1000×) with fixed     │
    │  configuration to measure empirical pass/fail rate    │
    └───────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────┐
│   Empirical Baseline                      │  Machine-generated record
│   (observed success rate, statistics)     │  of actual behavior
└───────────────────┬───────────────────────┘
                    │ (human reviews and approves)
                    ↓
┌───────────────────────────────────────────┐
│   Execution Specification                 │  Human-approved contract
└───────────────────┬───────────────────────┘
                    ↓
┌───────────────────────────────────────────┐
│   Probabilistic Conformance Tests         │  CI-gated validation
│   → PASS / FAIL (with confidence)         │
└───────────────────────────────────────────┘
```

## 5.1 Experiment Flow

Experiments operate in one of two modes, depending on whether configuration discovery is needed.

### 5.1.1 Exploration Mode (Optional)

Use this mode when the optimal configuration is unknown—for example, when choosing between LLM models, temperatures, or prompt variants.

1. **Resolve use case** by ID
2. **Parse ExperimentDesign** (factors/levels)
3. **For each ExperimentConfig** in the design:
   - Build context from config (factor→level)
   - Execute use case N times
   - Aggregate results and compute statistics
   - Generate per-config baseline
   - Check goal for early termination
4. **Generate SUMMARY** (aggregated comparison report)
5. **Select satisfactory configuration** for baseline derivation

### 5.1.2 Baseline Derivation Mode (Required)

Use this mode with a fixed configuration to measure the true success rate. **This step is never optional.**

1. **Resolve use case** by ID
2. **Apply known configuration** (from exploration or predetermined)
3. **Execute use case many times** (e.g., 1000 samples)
4. **Aggregate results** and compute statistics:
   - Observed success rate
   - Standard error
   - Confidence interval
   - Failure distribution
5. **Generate Empirical Baseline** (machine-readable file)
6. **Publish via TestReporter**

### When to Skip Exploration

Skip exploration (5.1.1) and proceed directly to baseline derivation (5.1.2) when:

- The system is **given** (e.g., a third-party API with fixed behavior)
- The configuration is **mandated** (e.g., organizational policy dictates model choice)
- You're **re-baselining** an existing configuration after changes

## 5.2 Specification Creation Flow (Manual)

1. **Empirical Baseline** (generated file)
2. Developer reviews results
3. **Decision Point**: Is the success rate acceptable? What threshold?
4. **Create Spec File**: Set minPassRate, budgets, version, approval
5. **Commit specification** to VCS

## 5.3 Probabilistic Test Flow: Spec → Verdict

1. `@ProbabilisticTest(spec = "usecase.x:v3")`
2. `SpecificationRegistry.resolve("usecase.x:v3")`
3. Load `ExecutionSpecification` from file
4. Resolve `@UseCase` by useCaseId
5. Apply configuration from spec
6. Build `SuccessCriteria` from spec expression
7. **For each sample (1..N)**:
   - Invoke use case
   - Get `UseCaseResult`
   - Evaluate criteria → success/fail recorded
8. `FinalVerdictDecider.isPassing()` → **PASS** or **FAIL**

## 5.4 Specification → Production Configuration Flow

Specifications can inform production configuration **without** involving use cases:

1. **Execution Specification** (file)
2. Build/deploy process reads and extracts configuration
3. **Production Configuration** (no use case involvement)

This flow is **out of scope** for punit itself but enabled by the specification format.

---

*Previous: [Annotation & API Design](./DOC-05-ANNOTATION-API-DESIGN.md)*

*Next: [Governance & Safety Mechanisms](./DOC-07-GOVERNANCE-SAFETY.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
