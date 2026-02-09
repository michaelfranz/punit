# Design: Intent-Governed Verdicts

Detailed design for implementing `plan/REQ-VERDICT.md`. Each phase is self-contained: the codebase compiles, all tests pass, and no existing behaviour changes until the gate is wired in (Phase 3).

**Decision log** (from `VERDICT-QUESTIONNAIRE.md`):

| Decision                        | Resolution                                                                    |
|---------------------------------|-------------------------------------------------------------------------------|
| Default intent                  | VERIFICATION                                                                  |
| Default α (thresholdConfidence) | 0.05 (confidence 0.95)                                                        |
| Feasibility α source            | Coupled to test's `thresholdConfidence`                                       |
| MDE/power in feasibility        | Out of scope for this iteration                                               |
| Verdict categories              | PASS and FAIL only; failure *reason* varies in message body                   |
| SMOKE early termination         | Yes, same as VERIFICATION                                                     |
| Normative branching             | Via `isNormative()` only; never branch on individual `ThresholdOrigin` values |
| MISCONFIGURED vs INFEASIBLE     | Same parent (FAIL), distinct message wording                                  |
| Transparent stats auto-enable   | No; remains opt-in                                                            |

---

## Phase 1 — Intent declaration

Pure additions. No behavioural change. All existing tests continue to pass because `VERIFICATION` is the default and no gate enforces it yet.

### 1.1 `TestIntent` enum

- [ ] Create `src/main/java/org/javai/punit/api/TestIntent.java`

```
VERIFICATION  — evidential; PUnit enforces statistical feasibility
SMOKE         — sentinel; PUnit permits undersized configurations
```

Two values only. No methods beyond what the enum provides. Javadoc must state the epistemic distinction (Req 1, Req 21 asymmetry).

Thoroughly document the intents in the API reference.

### 1.2 `@ProbabilisticTest` annotation

- [ ] Add `TestIntent intent() default TestIntent.VERIFICATION` attribute

Place it in a new section between THRESHOLD PROVENANCE and the end of the annotation, with a section header comment `// INTENT DECLARATION`. Javadoc references Req 2 (default VERIFICATION) and Req 3 (SMOKE escape hatch).

### 1.3 `ResolvedConfiguration` record

- [ ] Add `TestIntent intent` field to `ResolvedConfiguration` in `ConfigurationResolver.java`
- [ ] Add `double resolvedConfidence` field — the α used for feasibility (resolved from `thresholdConfidence` or framework default 0.95)
- [ ] Update both constructors (full and backward-compat)
- [ ] Add convenience: `boolean isSmoke()` and `boolean isVerification()`
- [ ] Update `ConfigurationResolver.resolve()` to populate both new fields

**Confidence resolution logic** (in `ConfigurationResolver`):

```
1. annotation.thresholdConfidence()  — if not NaN, use it
2. framework default: 0.95
```

Store as `resolvedConfidence` (the confidence level, not α). The feasibility evaluator computes `α = 1 - resolvedConfidence` internally.

### 1.4 `FinalConfigurationLogger`

- [ ] Add intent to `ConfigurationData` record
- [ ] Show intent line in configuration banner: `Intent: VERIFICATION` or `Intent: SMOKE`
- [ ] Update `FinalConfigurationLoggerTest`

### 1.5 Tests

- [ ] `TestIntentTest` — enum values, `valueOf`, `values()`
- [ ] `ConfigurationResolverTest` — intent resolution (default, explicit SMOKE, explicit VERIFICATION)
- [ ] `ConfigurationResolverTest` — resolvedConfidence resolution (NaN → 0.95, explicit value preserved)
- [ ] `FinalConfigurationLoggerTest` — intent appears in banner for both SMOKE and VERIFICATION

---

## Phase 2 — Verification feasibility evaluator

A new, isolated, pure function in the statistics package. No callers yet. This is Req 12b — "central to the credibility of the PUnit framework."

### 2.1 `VerificationFeasibilityEvaluator`

- [ ] Create `src/main/java/org/javai/punit/statistics/VerificationFeasibilityEvaluator.java`

**Public API:**

```java
public static FeasibilityResult evaluate(int samples, double target, double confidence)
```

**`FeasibilityResult`** (nested record):

```java
public record FeasibilityResult(
    boolean feasible,         // true if N is sufficient
    int minimumSamples,       // N_min for this target/confidence
    double configuredAlpha,   // 1 - confidence
    double target,            // p₀
    int configuredSamples,    // N as configured
    String criterion          // human-readable: "Wilson score one-sided lower bound"
)
```

**Algorithm** (Wilson score lower bound, same as existing `ComplianceEvidenceEvaluator`):

Given `α = 1 - confidence`, compute `z = Φ⁻¹(1 - α)`. For a perfect observation (k = n), the Wilson lower bound simplifies to `n / (n + z²)`. The sample is feasible if this bound ≥ target.

`minimumSamples` is computed by solving `n / (n + z²) ≥ p₀` → `n ≥ p₀ · z² / (1 - p₀)`, rounded up.

**Guard rails:**

- `samples <= 0` → throw `IllegalArgumentException`
- `target <= 0.0 || target >= 1.0` → throw `IllegalArgumentException`
- `confidence <= 0.0 || confidence >= 1.0` → throw `IllegalArgumentException`

### 2.2 Tests

- [ ] Create `src/test/java/org/javai/punit/statistics/VerificationFeasibilityEvaluatorTest.java`

Each test is fully self-contained with worked values in the test name and comments. Organised by `@Nested` groups:

**Feasibility at default confidence (0.95, α = 0.05, z ≈ 1.645):**

| Scenario                        | p₀     | N   | N_min   | Feasible? |
|---------------------------------|--------|-----|---------|-----------|
| Low target, small N             | 0.50   | 5   | 3       | yes       |
| Moderate target, sufficient N   | 0.90   | 30  | 25      | yes       |
| Moderate target, insufficient N | 0.90   | 20  | 25      | no        |
| High target, sufficient N       | 0.95   | 55  | 52      | yes       |
| High target, insufficient N     | 0.95   | 40  | 52      | no        |
| Extreme target                  | 0.9999 | 100 | ~27,055 | no        |

**Feasibility at stricter confidence (0.99, α = 0.01, z ≈ 2.326):**

| Scenario                      | p₀   | N  | N_min | Feasible? |
|-------------------------------|------|----|-------|-----------|
| Moderate target               | 0.90 | 50 | 49    | yes       |
| Moderate target, insufficient | 0.90 | 45 | 49    | no        |

**Edge cases:**

- [ ] `target` near 0.0 (e.g. 0.01) → very small N_min
- [ ] `target` near 1.0 (e.g. 0.999) → very large N_min
- [ ] `samples = 1` — feasible only for very low targets
- [ ] Invalid inputs throw `IllegalArgumentException`

**N_min computation:**

- [ ] Verify `minimumSamples` matches `⌈p₀ · z² / (1 - p₀)⌉` for each scenario
- [ ] Verify `evaluate(minimumSamples, target, confidence).feasible() == true`
- [ ] Verify `evaluate(minimumSamples - 1, target, confidence).feasible() == false`

### 2.3 `ComplianceEvidenceEvaluator` disposition

- [ ] Keep `ComplianceEvidenceEvaluator` — it serves a distinct role (compliance context detection via `hasComplianceContext`)
- [ ] Change `DEFAULT_ALPHA` from `0.001` to `0.05` (aligns with new framework default)
- [ ] Update `ComplianceEvidenceEvaluatorTest` for new default

---

## Phase 3 — Pre-execution feasibility gate

Wire the evaluator into the test lifecycle. This is the behavioural change: VERIFICATION + infeasible → hard fail *before any samples execute*.

### 3.1 Feasibility check in `ProbabilisticTestExtension`

- [ ] Add feasibility check after configuration is resolved and baseline is selected (in `validateTestConfiguration()` or adjacent)
- [ ] Timing: after `minPassRate` is known (may come from baseline spec), before first sample

**Logic:**

```
if intent == VERIFICATION:
    result = VerificationFeasibilityEvaluator.evaluate(samples, minPassRate, resolvedConfidence)
    if !result.feasible:
        throw ExtensionConfigurationException with infeasibility message
```

The exception is `ExtensionConfigurationException` (JUnit 5), which aborts the test without attributing failure to the SUT. This maps to Req 6 (non-ignorable in CI) and Req 7 (distinct from SUT failure).

### 3.2 Infeasibility failure message (Req 14)

- [ ] Create `InfeasibilityMessageBuilder` (or a static method in `VerificationFeasibilityEvaluator`)

The message must include (Req 14):

```
═ INFEASIBLE VERIFICATION ═══════════════════════════════════════════ PUnit ═

  Test:              methodName
  Intent:            VERIFICATION

  The configured sample size (N=50) is insufficient for verification
  at the declared confidence level.

  Configuration:
    Target (p₀):     0.9500
    Confidence:       0.95 (α = 0.05)
    Samples:          50

  Feasibility:
    Criterion:        Wilson score one-sided lower bound
    Minimum N:        52
    Assumption:       i.i.d. Bernoulli trials

  Remediation:
    - Increase samples to at least 52, or
    - Set intent = SMOKE to run as a sentinel test
```

### 3.3 Tests

- [ ] TestKit-based integration test: VERIFICATION + undersized → `ExtensionConfigurationException`
- [ ] TestKit-based integration test: VERIFICATION + sized → test executes normally
- [ ] TestKit-based integration test: SMOKE + undersized → test executes (no gate)
- [ ] TestKit-based integration test: SMOKE + sized → test executes with hint (Phase 4)
- [ ] Verify the failure message contains all Req 14 elements

---

## Phase 4 — Intent-aware output

Update all output paths to reflect intent in verdicts, caveats, and language.

### 4.1 `ResultPublisher` — summary mode

- [ ] Add `intent` to `PublishContext` record
- [ ] Show intent in verdict banner header: `═ VERDICT: PASS (VERIFICATION) ═` or `═ VERDICT: PASS (SMOKE) ═`
- [ ] SMOKE + normative + undersized → append "sample not sized for verification" note
- [ ] SMOKE + normative + sized → append "sample is sized for verification, consider setting intent = VERIFICATION"
- [ ] SMOKE → replace compliance language per Req 11 (e.g. "inconsistent with target" instead of "not meeting SLA obligation")
- [ ] Budget exhaustion wording: when a correctly-configured test terminates early due to budget, use caveat tone (not infeasibility tone)

### 4.2 `StatisticalExplanationBuilder` — verbose mode

- [ ] Add `TestIntent` parameter to `build()` and `buildWithInlineThreshold()`
- [ ] Intent-aware hypothesis framing:
  - VERIFICATION + normative: "system meets SLA requirement" (existing)
  - SMOKE + normative: "observed rate consistent with target" (softened)
  - SMOKE + non-normative: "observed rate meets threshold" (neutral)
- [ ] Intent-aware caveats:
  - SMOKE + normative + undersized: "sample not sized for verification" caveat
  - SMOKE + normative + sized: hint caveat
  - SMOKE: never emit "not meeting SLA obligation" or similar compliance language
- [ ] Rename/soften existing compliance caveat from `ComplianceEvidenceEvaluator.SIZING_NOTE` to align with new wording

### 4.3 `ConsoleExplanationRenderer`

- [ ] Render intent in HYPOTHESIS TEST section header or as a separate INTENT section
- [ ] No structural changes needed — caveats and hypothesis text changes flow from builder

### 4.4 `FinalConfigurationLogger`

- [ ] Already updated in Phase 1; verify intent displays correctly for all mode combinations (spec-driven, normative, explicit threshold)

### 4.5 Tests

- [ ] `ResultPublisherTest` — SMOKE verdict shows "(SMOKE)" qualifier
- [ ] `ResultPublisherTest` — SMOKE + normative + undersized shows sizing note
- [ ] `ResultPublisherTest` — SMOKE + normative + sized shows hint
- [ ] `ResultPublisherTest` — SMOKE avoids compliance language
- [ ] `StatisticalExplanationBuilderTest` — SMOKE hypothesis text is softened
- [ ] `StatisticalExplanationBuilderTest` — SMOKE caveats include sizing note when applicable
- [ ] `ConsoleExplanationRendererTest` — renders intent-aware explanation correctly

---

## Phase 5 — Parameter validation hardening

Strengthen existing validation to reject operationally invalid values (Req 7b).

### 5.1 Confidence bounds

- [ ] `ConfigurationResolver`: reject `thresholdConfidence` and `confidence` at <=0.0 or >=1.0
  - Current: `ProportionEstimate` and `DerivationContext` already reject (0,1) exclusive — but this is deep in the statistics layer. Validate early in `ConfigurationResolver` with a clear message.
  - Message: `"thresholdConfidence = 1.0 is invalid: α = 0 makes finite-sample inference vacuous. Use a value in (0, 1), e.g. 0.95."`
  - Message: `"thresholdConfidence = 0.0 is invalid: α = 1 renders the test meaningless. Use a value in (0, 1), e.g. 0.95."`

### 5.2 MDE bounds (future-proofing)

- [ ] If `minDetectableEffect` is set (not NaN), validate: `0 < MDE < p₀`
  - This requires `p₀` to be known, which may not be the case at annotation resolution time (spec-driven tests derive it later). Defer to the point where `minPassRate` is resolved.
  - Message: `"minDetectableEffect = 0.15 is invalid for target p₀ = 0.10: MDE must be less than the target pass rate."`

### 5.3 Tests

- [ ] `ConfigurationResolverTest` — `thresholdConfidence = 0.0` throws with clear message
- [ ] `ConfigurationResolverTest` — `thresholdConfidence = 1.0` throws with clear message
- [ ] `ConfigurationResolverTest` — `thresholdConfidence = 0.5` accepted
- [ ] `ConfigurationResolverTest` — `confidence = 0.0` and `1.0` rejected similarly

---

## Phase 6 — Documentation and verdict catalog

### 6.1 New `VerdictCatalogueTest` scenarios

- [ ] Add test methods to `VerdictCatalogueTest` for the new verdict shapes:

| Scenario                                  | Intent       | Origin      | Sized? | Expected output                                              |
|-------------------------------------------|--------------|-------------|--------|--------------------------------------------------------------|
| `verificationInfeasibleHardFail`          | VERIFICATION | UNSPECIFIED | no     | Infeasibility message (Phase 3 format)                       |
| `verificationNormativeInfeasibleHardFail` | VERIFICATION | SLA         | no     | Infeasibility message with normative context                 |
| `smokeUndersizedNormative`                | SMOKE        | SLA         | no     | PASS/FAIL (SMOKE) + "sample not sized for verification"      |
| `smokeSizedNormativeHint`                 | SMOKE        | SLA         | yes    | PASS/FAIL (SMOKE) + "consider setting intent = VERIFICATION" |
| `smokeNonNormative`                       | SMOKE        | EMPIRICAL   | no     | PASS/FAIL (SMOKE), no compliance language                    |
| `verificationPassSized`                   | VERIFICATION | SLA         | yes    | PASS (VERIFICATION) with full compliance framing             |

Note: infeasibility hard-fails produce `ExtensionConfigurationException`, not a verdict. Hard fails must be documented in the user guide, not in the verdict catalog.

### 6.2 Regenerate `docs/VERDICT-CATALOG.md`

- [ ] Run `./gradlew generateVerdictCatalog`
- [ ] Verify new sections appear in the generated document
- [ ] Update `GenerateVerdictCatalog.kt` section definitions if new sections are needed

### 6.3 Update `docs/STATISTICAL-COMPANION.md`

- [ ] Document the intent system (VERIFICATION vs SMOKE)
- [ ] Document the feasibility criterion (Wilson lower bound)
- [ ] Document the default α = 0.05 (confidence 0.95) and its implications
- [ ] Include the N_min table for common targets at α = 0.05
- [ ] Document the FAIL asymmetry for SMOKE: "FAIL is a *directional signal* warranting investigation, not a strong statistical conclusion at small N"

### 6.4 Update user guides (if applicable)

- [ ] Add intent examples to any existing user guide
- [ ] Document migration: existing tests with normative origins and small N should add `intent = SMOKE`

---

## File change summary

| File                                                        | Phase | Change                                                                                                                        |
|-------------------------------------------------------------|-------|-------------------------------------------------------------------------------------------------------------------------------|
| `api/TestIntent.java`                                       | 1     | **New** — enum                                                                                                                |
| `api/ProbabilisticTest.java`                                | 1     | Add `intent` attribute                                                                                                        |
| `ptest/engine/ConfigurationResolver.java`                   | 1     | Resolve intent + resolvedConfidence                                                                                           |
| `ptest/engine/ConfigurationResolver.ResolvedConfiguration`  | 1     | Add `intent`, `resolvedConfidence` fields                                                                                     |
| `ptest/engine/FinalConfigurationLogger.java`                | 1     | Show intent in config banner                                                                                                  |
| `statistics/VerificationFeasibilityEvaluator.java`          | 2     | **New** — feasibility function                                                                                                |
| `statistics/ComplianceEvidenceEvaluator.java`               | 2     | Change DEFAULT_ALPHA to 0.05                                                                                                  |
| `ptest/engine/ProbabilisticTestExtension.java`              | 3     | Pre-execution feasibility gate                                                                                                |
| `ptest/engine/ResultPublisher.java`                         | 4     | Intent-aware verdict output                                                                                                   |
| `statistics/transparent/StatisticalExplanationBuilder.java` | 4     | Intent-aware caveats and language                                                                                             |
| `statistics/transparent/ConsoleExplanationRenderer.java`    | 4     | Render intent                                                                                                                 |
| `ptest/engine/ConfigurationResolver.java`                   | 5     | Validate confidence bounds                                                                                                    |
| `examples/tests/VerdictCatalogueTest.java`                  | 6     | New scenario methods                                                                                                          |
| `scripts/.../GenerateVerdictCatalog.kt`                     | 6     | New section definitions                                                                                                       |
| `docs/STATISTICAL-COMPANION.md`                             | 6     | Intent + feasibility documentation; emphasise PUnit's awareness of what SLA/SLO/Policy compliance means terms of sample sizes |
| `docs/VERDICT-CATALOG.md`                                   | 6     | Regenerated                                                                                                                   |

---

## Dependency graph

```
Phase 1 ──→ Phase 3 ──→ Phase 4
   │                       │
   └──→ Phase 2 ──────────┘──→ Phase 6

Phase 5 (independent, can run in parallel with 3/4)
```

Phases 1 and 2 have no dependencies on each other and can be developed in parallel. Phase 3 requires both. Phase 4 requires Phase 3. Phase 5 is independent. Phase 6 requires Phases 3 and 4.
