# Governance & Safety Mechanisms

The framework actively guides developers toward empirical threshold selection and prevents misuse.

## 6.1 Discouraging Arbitrary Thresholds

### 6.1.1 Warning on Inline Thresholds Without Prior Baseline

```
⚠️ WARNING: @ProbabilisticTest for 'validateEmail' uses minPassRate=0.95 
   without an empirical baseline. This threshold may be arbitrary.
   
   Recommendation: Run an @Experiment first to establish empirical behavior,
   then create a specification based on observed results.
```

This is a **warning**, not an error. The test still executes.

### 6.1.2 Spec-First Guidance in Documentation

Documentation and examples consistently demonstrate the spec-driven pattern as primary.

## 6.2 Surfacing Insufficient Empirical Data

```
⚠️ WARNING: Specification 'usecase.json.gen:v3' is based on baseline with
   only 50 samples. Confidence interval for success rate is wide: [0.82, 0.98].
   
   Recommendation: Run additional experiments to narrow the confidence interval.
```

## 6.3 Preventing Blind Acceptance of Poor Results

Specifications **require** explicit approval metadata:

```yaml
approvedAt: 2026-01-04T16:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: "Approved after review of Jan 4 experiment results"
```

Missing approval metadata causes an **error** that fails the test.

## 6.4 Baseline-Spec Drift Detection

```
⚠️ WARNING: Observed success rate (0.78) is significantly lower than
   baseline (0.935) for specification 'usecase.json.gen:v3'.
   
   This may indicate:
   - System regression
   - Environment differences
   - Baseline that no longer reflects current behavior
```

## 6.5 Warnings vs Errors Summary

| Condition                                  | Severity | Behavior                             |
|--------------------------------------------|----------|--------------------------------------|
| Inline threshold without baseline          | Warning  | Test executes with warning           |
| Baseline with insufficient samples         | Warning  | Spec loads with warning              |
| Spec without approval metadata             | Error    | Test fails                           |
| Spec references missing baseline           | Error    | Test fails                           |
| Observed rate significantly below baseline | Warning  | Test may still pass if threshold met |
| Use case ID not found                      | Error    | Test fails                           |

## 6.6 Baseline Review and Approval Workflow

PUnit provides Gradle tasks to support the human review process that transforms machine-generated baselines into human-approved specifications.

### 6.6.1 Gradle Tasks

#### `punitReview` — Display Baseline Information

```bash
# List all baselines pending approval
./gradlew punitReview --list-pending

# Review a specific baseline (display only, no changes)
./gradlew punitReview --useCase=json.generation
```

Output:
```
┌─────────────────────────────────────────────────────────────────┐
│ BASELINE REVIEW: json.generation                                │
├─────────────────────────────────────────────────────────────────┤
│ Current:   2026-01-07  |  951/1000 = 95.1%                      │
│ 95% CI:    [93.8%, 96.4%]           (computed on-the-fly)       │
├─────────────────────────────────────────────────────────────────┤
│ History:                                                        │
│   2026-01-05  |  912/1000 = 91.2%                               │
│   2026-01-03  |  471/500  = 94.2%                               │
├─────────────────────────────────────────────────────────────────┤
│ Status: NOT APPROVED                                            │
│                                                                 │
│ To approve: ./gradlew punitApprove --useCase=json.generation    │
└─────────────────────────────────────────────────────────────────┘
```

#### `punitApprove` — Create Specification from Baseline

```bash
# Approve and create spec (non-interactive)
./gradlew punitApprove --useCase=json.generation

# Approve with notes
./gradlew punitApprove --useCase=json.generation \
    --notes="Approved after fixing prompt template"

# Force re-approval (creates new version)
./gradlew punitApprove --useCase=json.generation --force
```

**Design principle**: The approval workflow is **non-interactive** to support:
- CI/CD pipelines
- Headless environments
- IDE Gradle integrations
- Clear audit trails (explicit `--approve` action)

### 6.6.2 What the Specification Contains

The specification stores **raw empirical data**, not computed thresholds. Thresholds are computed at test runtime based on the `@ProbabilisticTest` annotation parameters.

**Rationale**: This separation of concerns means:
- **Spec = approved empirical truth** (what we observed)
- **Annotation = operational preferences** (how we want to test)
- Changing test parameters (e.g., confidence level) doesn't require re-approval

**Specification file structure:**

```yaml
useCaseId: json.generation
version: v1

approval:
  approvedAt: 2026-01-07T15:00:00Z
  approvedBy: jane.engineer
  notes: "Approved after fixing prompt template"

baseline:
  generatedAt: 2026-01-07T14:30:00Z
  samples: 1000
  successes: 951
  failures: 49
  observedRate: 0.951

configuration:
  backend: llm
  model: gpt-4
  temperature: 0.2

successCriteria: "isValidJson == true && hasRequiredFields == true"
```

Note: The specification does **not** store confidence intervals or thresholds. These are computed at test runtime from the raw data combined with annotation parameters.

**At test runtime**, the framework:
1. Reads spec → `observedRate: 0.951`, `baselineSamples: 1000`
2. Reads annotation → `testSamples: 100`, `thresholdConfidence: 0.95`
3. Computes → `minPassRate ≈ 0.916` (using Wilson lower bound)

### 6.6.3 Versioning

| Scenario                           | Behavior                               |
|------------------------------------|----------------------------------------|
| New use case (no existing spec)    | Creates `v1`                           |
| Existing spec, new approval        | Auto-increments version (`v1` → `v2`)  |
| Want to replace without versioning | Delete old spec manually, then approve |

### 6.6.4 File Structure

```
src/test/resources/punit/
├── baselines/                        # Machine-generated (experiment output)
│   └── json.generation.yaml          # Simple name, overwritten each run
│
└── specs/                            # Human-approved (approval output)
    └── json.generation/
        ├── v1.yaml                   # First approval
        └── v2.yaml                   # After re-approval
```

**Baseline files**:
- Simple filename: `{useCaseId}.yaml`
- Overwritten when experiment re-runs
- Timestamp stored **inside** the file
- Git history provides archival

**Specification files**:
- Versioned: `{useCaseId}/v{N}.yaml`
- New version created on each approval
- Old versions retained for reference

### 6.6.5 Baseline File Format

```yaml
useCaseId: json.generation
generatedAt: 2026-01-07T14:30:00Z      # Always present for auditability

execution:
  samples: 1000
  successes: 951
  failures: 49
  observedRate: 0.951
  durationMs: 45230

configuration:                          # From experiment configuration
  model: gpt-4
  temperature: 0.2
  promptVariant: A

failureDistribution:                    # Optional: categorized failures
  invalidJson: 32
  missingFields: 12
  hallucination: 5

history:                                # Previous baseline runs (auto-appended)
  - generatedAt: 2026-01-05T10:00:00Z
    samples: 1000
    successes: 912
    observedRate: 0.912
  - generatedAt: 2026-01-03T09:15:00Z
    samples: 500
    successes: 471
    observedRate: 0.942
```

**Notes:**
- The `history` array is automatically appended when an experiment re-runs
- History is limited to the last N entries (configurable, default 10) to prevent unbounded growth
- Confidence intervals are **not** stored—they are computed on-demand by tooling (e.g., `punitReview`) using the raw data

### 6.6.6 Pending Approval Detection

The `--list-pending` flag identifies baselines without corresponding approved specifications:

```bash
./gradlew punitReview --list-pending
```

```
Pending Approvals (baselines without specs):
  - json.generation       (generated 2026-01-07, 95.1% success)
  - email.validation      (generated 2026-01-05, 98.2% success)

Already Approved:
  - sentiment.analysis    (spec v2, approved 2026-01-03)
```

---

*Previous: [Data Flow](./DOC-06-DATA-FLOW.md)*

*Next: [Execution & Reporting Semantics](./DOC-08-EXECUTION-REPORTING.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
