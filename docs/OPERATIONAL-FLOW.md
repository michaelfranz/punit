# Operational Flow: From Use Case to Confidence

## For Teams Building Reliable Systems in the Face of Uncertainty

This document describes the end-to-end workflow for organizations using **PUnit** to manage operational risk when working with non-deterministic systems—systems that don't always produce the same output for the same input.

**Audience**: Engineering leads, quality engineers, and managers who need to understand how empirical testing provides quantifiable confidence in system behavior.

---

## Executive Summary

When a system behaves non-deterministically (as with AI/LLM integrations, distributed systems, or any probabilistic component), traditional pass/fail testing breaks down. A test might pass today and fail tomorrow—not because the system changed, but because of inherent randomness.

**PUnit** provides a disciplined workflow that:

1. **Measures** actual system behavior through controlled experiments
2. **Establishes** empirical baselines from real observations
3. **Derives** statistically-sound thresholds for ongoing testing
4. **Qualifies** every test result with the confidence level it deserves
5. **Surfaces** the cost/confidence trade-offs so organizations can make informed decisions

The result: your CI pipeline reports not just "PASSED" or "FAILED", but "FAILED with 95% confidence"—meaning there's only a 5% chance this failure is a false alarm due to random variation.

---

## The Operational Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│   1. USE CASE       2. EXPERIMENT       3. BASELINE      4. SPECIFICATION   │
│   DEFINITION        EXECUTION           ANALYSIS         APPROVAL           │
│                                                                             │
│   "What behavior    "What does the      "What's the      "What behavior     │
│    do we need?"      system actually     true success     do we commit      │
│                      do?"                rate?"           to?"              │
│                                                                             │
│        │                  │                   │                 │           │
│        ▼                  ▼                   ▼                 ▼           │
│   ┌─────────┐       ┌───────────┐       ┌──────────┐     ┌────────────┐     │
│   │ UseCase │ ───▶  │ Experiment│ ───▶  │ Baseline │ ──▶ │    Spec    │     │
│   │Function │       │  (N=1000) │       │  (Data)  │     │ (Contract) │     │
│   └─────────┘       └───────────┘       └──────────┘     └────────────┘     │
│                                                                 │           │
│                                                                 ▼           │
│   5. PROBABILISTIC TEST           6. CONTINUOUS EXECUTION                   │
│                                                                             │
│   "Test against the               "Run on every commit,                     │
│    approved contract"              report with confidence"                  │
│                                                                             │
│        ┌──────────────────┐            ┌─────────────────────────────┐      │
│        │@ProbabilisticTest│ ─────────▶ │ PASSED / FAILED (95% conf.) │      │
│        │  samples=100     │            │ with qualified explanation  │      │
│        │  confidence=95%  │            └─────────────────────────────┘      │
│        └──────────────────┘                                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Stage 1: Use Case Definition

### What Happens

A **use case** is a repeatable function that invokes your production code and captures the outcome. It's the atomic unit of observation—one invocation, one result.

### Example

```
Use Case: "JSON Generation"
- Input: A customer record
- Action: Call the LLM to generate a JSON representation
- Observation: Was the output valid JSON? Did it contain required fields?
```

### Why It Matters

The use case is **reused** across experiments AND tests. You define it once, and the framework handles execution, repetition, and aggregation. This ensures you're always measuring the same thing, and most importantly that the empirically derived thresholds are used exactly where they are needed.

### For Managers

Think of the use case as the question you're asking: "Can our system do X correctly?" Everything that follows is about answering that question with quantifiable confidence.

---

## Stage 2: Experiment Execution

An **experiment** runs your use case many times—typically hundreds or thousands—to gather empirical data. Experiments serve two distinct purposes, which may occur together or separately:

### 2A. Configuration Discovery (Optional)

**When it applies**: When you have choices about how to configure the non-deterministic system—which model to use, what parameters to set, how to structure prompts.

**When to skip**: When the system is **given**. Many organizations must use a specific non-deterministic component as-is. The model is mandated, the parameters are fixed, there are no configuration choices to explore. In these cases, skip directly to Stage 2B.

#### Example: LLM-Based Systems

LLM integrations typically offer many configuration choices:

| Factor         | Possible Values                 |
|----------------|---------------------------------|
| Model          | gpt-4, gpt-4-turbo, claude-3    |
| Temperature    | 0.0, 0.2, 0.5, 0.7              |
| System Prompt  | Variant A, Variant B, Variant C |

The experiment systematically (or adaptively) explores combinations until it finds configurations that meet your goals.

#### Example: Given Systems

Other non-deterministic systems offer no such choices:

- A third-party API with fixed behavior
- A hardware sensor with inherent measurement noise
- A distributed system with unavoidable timing variations
- A legacy component you cannot modify

For these systems, there is nothing to "discover"—the configuration is what it is. You proceed directly to measurement.

---

### 2B. Success Rate Measurement (Always Required)

**This step is never optional.** Regardless of whether you explored configurations or accepted a given system, you must measure how the system actually performs.

#### The Goal

Run sufficient samples to obtain a **precise estimate** of the true success rate. This estimate becomes the foundation for all subsequent testing.

#### Sample Size and Precision

Running more samples gives you a more precise estimate:

| Samples | Precision (95% CI) | Interpretation                                   |
|---------|--------------------|--------------------------------------------------|
| 100     | ±7-10%             | Rough estimate, acceptable for early exploration |
| 500     | ±3-4%              | Good estimate, suitable for most use cases       |
| 1000    | ±2-3%              | High precision, recommended for critical systems |

The precision you need depends on how tight your quality margins are. If the difference between acceptable and unacceptable is 5%, you need enough samples to measure with better than ±5% precision.

---

### For Managers

**Configuration discovery** is an investment in finding the best way to use a flexible system. It applies primarily to LLM integrations and other configurable components. Skip it when the system is mandated.

**Success rate measurement** is non-negotiable. Without it, you have no empirical basis for testing thresholds. You're just guessing.

The experiment is where you spend compute resources upfront to understand your system's true behavior. This investment pays off by enabling informed decisions about quality thresholds and testing costs.

---

## Stage 3: Baseline Analysis

### What Happens

After the experiment completes, you have an **empirical baseline**—a machine-generated record of what the system actually did.

### Example Baseline

```
Use Case: json.generation
Configuration: gpt-4, temperature=0.2, prompt-variant-A

Execution:
  samples: 1000
  successes: 951
  failures: 49

Statistics:
  observedSuccessRate: 95.1%
  standardError: 0.68%
  95% Confidence Interval: [93.8%, 96.4%]

Interpretation:
  We are 95% confident the true success rate is between 93.8% and 96.4%.
```

### Key Insight: The Observed Rate Is Not the True Rate

Your experiment observed 95.1%, but the **true** success rate (what you'd see if you ran infinite samples) is somewhere in the confidence interval. With 1000 samples, that interval is narrow (±1.3%). With fewer samples, it's wider.

### Special Case: When All Trials Succeed

Sometimes an experiment completes with **zero failures**—particularly when testing highly reliable third-party APIs or well-tuned systems. This produces an observed rate of 100%.

**What 100% actually means**: It does *not* mean "this system is perfect and will never fail." It means "no failures occurred in N trials." There may still be a small probability of failure that simply wasn't observed during the experiment.

**Example**: An experiment runs 1000 trials against a payment gateway with zero failures. The 100% observed rate tells us the true success rate is very high—but statistical analysis reveals we can only be 95% confident that the true rate is at least 99.73%, not 100%.

**PUnit handles this automatically**: The framework uses proven statistical techniques (specifically, the Wilson score bound) to derive sensible thresholds even when no failures are observed. You don't need to take special action—PUnit will compute appropriate thresholds that account for the uncertainty inherent in observing "perfect" results.

For technical details, see [Statistical Companion: The Perfect Baseline Problem](./STATISTICAL-COMPANION.md#4-the-perfect-baseline-problem-hatp--1).

### For Managers

The baseline tells you: "Under controlled conditions, here's how the system performs." It's not a guarantee—it's an empirical measurement with known precision.

---

## Stage 4: Specification Approval

### What Happens

A human reviews the baseline and **approves** a specification—a contract that says "this is the behavior we commit to maintaining."

### The Approval Decision

The approver considers:
- Is 95.1% success rate acceptable for this use case?
- What are the consequences of failures?
- What's our tolerance for regression?

### Example Specification

The specification is more than a statement of approval—it is a **software artifact** that will be used to configure probabilistic tests. When a test references this specification, the framework uses the `experimentalBasis` data to derive statistically appropriate thresholds.

```yaml
specId: json.generation:v1
useCaseId: json.generation
version: 1

approvedAt: 2026-01-06T14:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: |
  Approved based on 1000-sample experiment.
  95.1% success rate is acceptable for this use case.
  Failures are handled by retry logic in production.

experimentalBasis:
  samples: 1000
  observedRate: 0.951
  
requirements:
  # Threshold will be derived based on test configuration
  successCriteria: "isValidJson == true && hasRequiredFields == true"
```

The `experimentalBasis` section is critical: it provides the empirical data from which test thresholds are computed. When a probabilistic test runs with 100 samples, the framework uses this data to calculate the appropriate threshold (e.g., 91.6% instead of the raw 95.1%).

### For Managers

This is a **governance checkpoint**. A human takes responsibility for saying "this level of quality is acceptable." But it is also a **configuration artifact**—the specification file is checked into source control and directly referenced by probabilistic tests. The framework enforces the approved decision automatically.

---

## Stage 5: Probabilistic Test Development

### What Happens

A **probabilistic test** is a special case of a **regression test**. Its purpose is to regularly evaluate the integrity of your software in the face of indeterminism—to detect degradations whenever they occur.

Degradations don't only come from code changes. They can emerge over time while software is in production:

- **Shifting user demographics**: Input characteristics change as your user base evolves—people may increasingly use dialect, slang, or domain-specific terminology that differs from what the system was originally tuned for
- **Data growth effects**: Database performance degrades as content accumulates; query patterns that once worked well become bottlenecks
- **External service drift**: Third-party APIs and LLM providers update their models, subtly changing behavior
- **Environmental changes**: Infrastructure updates, network conditions, or dependency versions shift

Regular execution of probabilistic tests—whether in CI before deployment or scheduled against production systems—provides ongoing visibility into system health.

What makes it "probabilistic" is how it handles the inherent uncertainty:

- It runs **multiple samples** (not just one) to gather statistical evidence
- It uses **statistically-derived thresholds** computed from empirical baseline data combined with the organization's explicitly stated operational priorities
- It reports results **with confidence levels**, qualifying every verdict

Developers create a probabilistic test that validates the system against the approved specification.

### Referencing the Specification

The probabilistic test explicitly references the specification from Stage 4. This creates a direct link between the approved contract and the ongoing validation:

```java
@ProbabilisticTest(
    spec = "json.generation:v1",    // References the approved specification
    samples = 100,
    thresholdConfidence = 0.95
)
void jsonGenerationMeetsApprovedContract() {
    // This test uses the SAME use case function defined in Stage 1
    UseCaseResult result = jsonGenerationUseCase.execute(testInput);
    
    // The framework evaluates results against the successCriteria
    // defined in the specification: "isValidJson == true && hasRequiredFields == true"
}
```

Notice how the pieces connect:

| Element                       | Comes From              | Purpose                                           |
|-------------------------------|-------------------------|---------------------------------------------------|
| `spec = "json.generation:v1"` | Stage 4: Specification  | Links test to approved contract                   |
| `experimentalBasis` (in spec) | Stage 3: Baseline       | Provides empirical data for threshold calculation |
| `samples = 100`               | Developer choice        | Determines test cost and precision                |
| `thresholdConfidence = 0.95`  | Organizational priority | Controls false positive rate                      |
| `successCriteria` (in spec)   | Stage 4: Specification  | Defines what "success" means                      |
| Use case function             | Stage 1: Use Case       | The actual behavior being tested                  |

The framework reads the specification file, extracts the experimental basis (95.1% from 1000 samples), and computes the appropriate threshold for the configured test parameters.

> **A note on test classification**: If the probabilistic test invokes external resources—an LLM API, a third-party service, a remote database—then it is also technically an **integration test**. In practice, most probabilistic tests fall into this category. This doesn't change how you write or run them, but it does mean they are subject to the usual integration test considerations: network reliability, service availability, and external rate limits.

### The Three Operational Approaches

**A firm but friendly reminder about trade-offs:**

It would be wonderful if we could simultaneously have low testing costs, high confidence, AND tight quality thresholds. Unfortunately, the nature of uncertainty itself—doesn't permit this. It is, quite literally, the way the universe works. At any given time, you can control **two of the three variables**; the third is determined by reality.

| If you fix...      | And you fix...   | Then this is determined by statistics |
|--------------------|------------------|---------------------------------------|
| Sample size (cost) | Confidence level | Threshold (how tight you can be)      |
| Confidence level   | Threshold        | Sample size (what it will cost)       |
| Sample size (cost) | Threshold        | Confidence (how sure you can be)      |

This is not a limitation of PUnit—it is a fundamental property of statistical inference. The framework simply makes these trade-offs explicit and computable, rather than leaving them implicit and confusing.

Each of the three approaches below represents a different choice about which two variables to control:

Organizations can configure their tests based on their priorities:

#### Approach 1: Sample-Size-First (Cost-Driven)

*"We can afford 100 samples per test run. What confidence does that give us?"*

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    samples = 100,
    thresholdConfidence = 0.95
)
```

**Result**: Framework computes threshold (e.g., 91.6%) that provides 95% confidence.

**Best for**: Organizations with fixed testing budgets, CI time constraints, or API rate limits.

#### Approach 2: Confidence-First (Quality-Driven)

*"We require 99% confidence. How many samples do we need?"*

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    confidence = 0.99,
    minDetectableEffect = 0.05,  // Detect 5% degradation
    power = 0.80                  // 80% detection rate
)
```

**Result**: Framework computes required samples (e.g., 250).

**Best for**: Safety-critical systems, healthcare, finance—where cost is secondary to confidence.

#### Approach 3: Threshold-First (Baseline-Anchored)

*"We want to use the exact experimental rate as our threshold."*

```java
@ProbabilisticTest(
    spec = "json.generation:v1",
    samples = 100,
    minPassRate = 0.951  // Raw experimental rate
)
```

**Result**: Framework computes implied confidence and **warns** if false positive rate is high.

**Best for**: Organizations learning the trade-offs, or those deliberately accepting strict thresholds.

### For Managers

This is where **organizational values meet statistics**. You choose:
- How much to spend on testing (samples)
- How much confidence you need (risk tolerance)
- How much degradation you want to detect (quality threshold)

There's no free lunch—more confidence requires more samples, which cost more.

---

## Stage 6: Continuous Execution and Qualified Reporting

### What Happens

The recommended approach is to run probabilistic tests automatically—on every commit, pull request, or scheduled CI job. Each run:

1. Executes the use case N times (e.g., 100 samples)
2. Counts successes and failures
3. Compares observed rate to threshold
4. Reports with statistical qualification

Precisely which option to choose depends on your organization's priorities and budget.

### Understanding the Results

#### When the Test Passes

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.generatesValidJson                     │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                  │
│ Samples: 100 | Successes: 94 | Failures: 6                      │
│ Observed Rate: 94.0%                                            │
│ Threshold: 91.6% (derived from 95.1% baseline at 95% confidence)│
│                                                                 │
│ INTERPRETATION:                                                 │
│   Observed 94.0% ≥ 91.6% threshold.                             │
│   No evidence of degradation from baseline.                     │
└─────────────────────────────────────────────────────────────────┘
```

**What this means**: The system is performing within expected bounds. The 94% observed rate is consistent with the 95.1% baseline, accounting for normal sampling variation.

#### When the Test Fails

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.generatesValidJson                     │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                  │
│ Samples: 100 | Successes: 87 | Failures: 13                     │
│ Observed Rate: 87.0%                                            │
│ Threshold: 91.6%                                                │
│                                                                 │
│ STATISTICAL CONTEXT:                                            │
│   Shortfall: 4.6% below threshold                               │
│   Confidence: 95%                                               │
│                                                                 │
│ INTERPRETATION:                                                 │
│   This result indicates DEGRADATION from the baseline.          │
│   There is a 5% probability this failure is due to sampling     │
│   variance rather than actual system degradation.               │
│                                                                 │
│ RECOMMENDED ACTIONS:                                            │
│   1. Investigate recent changes that may have caused regression │
│   2. Re-run experiment to establish new baseline if intentional │
│   3. If false positive suspected, increase sample size          │
└─────────────────────────────────────────────────────────────────┘
```

### The Critical Qualification

**A "FAILED" result does not mean "definitely broken."**

It means: "The observed behavior is statistically inconsistent with the baseline at the configured confidence level."

With 95% confidence:
- If the system truly has not degraded, then the probability of seeing a failure is ≤ 5% (false alarm rate).
- But the fraction of observed failures that are “real” depends on how often real degradations occur (base rate) and on power (ability to detect degradations of a given size).

In other words, at 95% confidence, if there has been no real degradation, we expect at most a 5% chance of a false alarm on any single run. A failure is evidence of degradation, not absolute proof. Repeated failures strengthen that evidence.

This is the fundamental trade-off of statistical testing. Higher confidence (99%) reduces false positives but requires more samples.

### For Managers

Every failure comes with a probability statement. When reviewing a failure, ask:
- What's the confidence level?
- What's the false positive rate (1 - confidence)?
- Is this a single failure or a pattern?

If you see a 95% confidence failure, there's a 1-in-20 chance it's noise. If you see the same failure three times in a row, the probability of all three being false positives is (0.05)³ = 0.000125, or about 1 in 8,000.

---

## The Cost/Confidence Trade-Off

### The Fundamental Equation

More samples → More cost → Higher confidence → Fewer false positives

| Samples | Approximate Cost | False Positive Rate | False Negatives             |
|---------|------------------|---------------------|-----------------------------|
| 30      | $                | ~15-20%             | Many missed regressions     |
| 100     | $$               | ~5%                 | Some missed regressions     |
| 500     | $$$              | ~1%                 | Few missed regressions      |
| 1000    | $$$$             | ~0.5%               | Very few missed regressions |

### Making the Decision

Organizations must balance:

**Risk tolerance**: What's the cost of a false positive (wasted investigation) vs. false negative (missed regression)?

**Budget constraints**: What can you afford to spend on testing?

**False positive overhead**: What can you afford to spend chasing down false alarms? If your confidence is too low, developers will waste time investigating "failures" that turn out to be noise. Always bear in mind the possibility of a false positive (even if small) and act accordingly when orchestrating a response.

**Detection requirements**: How small a degradation do you need to catch?

### Example Decision Matrix

| Use Case Type           | Recommended Samples | Confidence | Rationale                                     |
|-------------------------|---------------------|------------|-----------------------------------------------|
| Internal tool           | 50                  | 90%        | Low stakes, can tolerate some false positives |
| Customer-facing feature | 100                 | 95%        | Balance of cost and reliability               |
| Payment processing      | 500                 | 99%        | High stakes, minimize false negatives         |
| Safety-critical         | 1000+               | 99.9%      | Cannot afford to miss regressions             |

---

## Handling Uncertainty in Practice

### When to Re-Run Experiments

- **Major model updates**: New LLM version, significant prompt changes
- **Baseline staleness**: Every 3-6 months for actively changing systems
- **After production incidents**: To recalibrate expectations

### When to Adjust Thresholds

- **Never arbitrarily**: Thresholds should always derive from empirical data
- **Through new experiments**: If you need a different threshold, run a new experiment
- **With approval**: Human review required for any specification changes

### When to Increase Sample Size

- **Persistent false positives**: If tests fail frequently but investigations find nothing wrong
- **Tight margins**: If observed rates are consistently close to threshold
- **High-stakes deployments**: Before major releases

---

## Glossary for Managers

| Term                  | Plain English                                             |
|-----------------------|-----------------------------------------------------------|
| **Sample**            | One execution of the use case                             |
| **Baseline**          | Measured performance from a controlled experiment         |
| **Threshold**         | The minimum acceptable pass rate for a test               |
| **Confidence level**  | How sure we are that a failure indicates real degradation |
| **False positive**    | A test failure when the system is actually fine           |
| **False negative**    | A test pass when the system has actually degraded         |
| **Specification**     | The approved contract defining expected behavior          |
| **Sampling variance** | Natural fluctuation in results due to randomness          |

---

## Summary: The Operational Integrity Promise

When you adopt punit's workflow, you get:

1. **Empirical grounding**: Every threshold comes from real measurements, not guesswork
2. **Quantified confidence**: Every result includes its statistical reliability
3. **Explicit trade-offs**: Cost and confidence decisions are visible and deliberate
4. **Audit trail**: Baseline → Specification → Test forms a traceable chain
5. **Qualified verdicts**: No more "PASSED" or "FAILED" without context

The goal is not to eliminate uncertainty—that's impossible with non-deterministic systems. The goal is to **measure, quantify, and communicate** uncertainty so your organization can make informed decisions about risk.

---

## Next Steps

1. **Identify your first use case**: What non-deterministic behavior needs testing?
2. **Run a baseline experiment**: Gather empirical data on actual performance
3. **Decide your operational priorities**: Cost-first, confidence-first, or threshold-first?
4. **Create your first probabilistic test**: Use the empirical baseline to set thresholds
5. **Integrate into CI**: Run tests on every commit with qualified reporting

---

*For technical implementation details, see the [PLAN-EXP.md](./PLAN-EXP.md) design document.*

*For formal statistical foundations, see the [Statistical Companion Document](./STATISTICAL-COMPANION.md).*

