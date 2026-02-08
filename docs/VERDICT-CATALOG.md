# Verdict Catalog

A curated collection of archetypal PUnit verdicts, organized from simplest to most complex.

PUnit verdicts are available at two detail levels:

- **Summary** (default) — compact verdict with pass rate comparison, termination reason, and caveats
- **Verbose** (`-Dpunit.stats.detail=VERBOSE`) — full statistical analysis including hypothesis test formulation, confidence intervals, and inference workings

The numerical values shown below come from actual test runs and will vary between executions due to the probabilistic nature of the tests.

---

## 1. Pass

The simplest passing verdict. PUnit detects that the required number of successes has been reached and terminates early, skipping the remaining samples.

### Summary

```
═ TEST CONFIGURATION FOR: servicePassesComfortably ═══════════════════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.5000 (derived from baseline)
  Samples:          50

```

```
═ VERDICT: PASS ══════════════════════════════════════════════════════ PUnit ═

  servicePassesComfortably(ShoppingBasketUseCase, String)
  Observed pass rate: 0.7353 (25/34) >= min pass rate: 0.5000
  Termination: Required pass rate already achieved
  Details: After 34 samples with 25 successes (0.7353), required min pass rate (25 successes) already met. Skipping 16 remaining samples.
  Elapsed: 15ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: servicePassesComfortablyTransparent(ShoppingB... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5000 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     30
    Successes (k):       25
    Observed rate (p̂):  0.8333
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.83 × 0.17 / 30) = 0.0680
    95% Confidence interval: [0.664, 0.927]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.83 - 0.50) / √(0.50 × 0.50 / 30)
                         z = 3.65
    
    p-value:             P(Z > 3.65) = 0.000
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.8333 meets the required
                         threshold of 0.5000. The test passes.
                         
    Caveat:              With n=30 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 2. Fail with early termination

When PUnit determines that the threshold can no longer be reached — even if every remaining sample succeeds — it terminates early and reports the impossibility analysis.

### Summary

```
═ TEST CONFIGURATION FOR: failsEarlyWhenThresholdUnreachable ═════════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.9500 (derived from baseline)
  Samples:          30

```

```
═ VERDICT: FAIL ══════════════════════════════════════════════════════ PUnit ═

  failsEarlyWhenThresholdUnreachable(ShoppingBasketUseCase, String)
  Observed pass rate: 0.0000 (0/2) < min pass rate: 0.9500
  Termination: Cannot reach required pass rate
  Details: After 2 samples with 0 successes, maximum possible successes (0 + 28 = 28) is less than required (29)
  Analysis: Needed 29 successes, maximum possible is 28
  Elapsed: 2ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: failsEarlyWhenThresholdUnreachableTransparent... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9500 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.9500 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     2
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.00 × 1.00 / 2) = 0.0000
    95% Confidence interval: [0.000, 0.658]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.00 - 0.95) / √(0.95 × 0.05 / 2)
                         z = -6.16
    
    p-value:             P(Z > -6.16) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9500. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Small sample size (n=2). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 3. Fail

A failing verdict. The verbose variant adds the HYPOTHESIS TEST and STATISTICAL INFERENCE workings.

### Summary

```
═ TEST CONFIGURATION FOR: serviceFailsNarrowlyTransparent ════════════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.9500 (derived from baseline)
  Samples:          50

```

```
═ STATISTICAL ANALYSIS FOR: serviceFailsNarrowlyTransparent(ShoppingBaske... ═

  OBSERVED DATA
    Sample size (n):     3
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9500. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Small sample size (n=3). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: serviceFailsNarrowlyTransparent(ShoppingBaske... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9500 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.9500 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     3
    Successes (k):       0
    Observed rate (p̂):  0.0000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9500 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.00 × 1.00 / 3) = 0.0000
    95% Confidence interval: [0.000, 0.561]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.00 - 0.95) / √(0.95 × 0.05 / 3)
                         z = -7.55
    
    p-value:             P(Z > -7.55) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.0000 falls below the
                         required threshold of 0.9500. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Small sample size (n=3). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Zero success rate observed. This indicates a
                         fundamental failure that may warrant investigation
                         before further testing.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 4. Budget exhaustion

When a cost budget (time or tokens) runs out before all samples complete, PUnit reports how many samples were executed and the pass rate at termination. The default budget-exhaustion behaviour is FAIL — the test fails regardless of the partial results.

### Summary

```
═ TEST CONFIGURATION FOR: failsWhenBudgetRunsOut ═════════════════════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.5000 (derived from baseline)
  Samples:          50

```

```
═ VERDICT: FAIL ══════════════════════════════════════════════════════ PUnit ═

  failsWhenBudgetRunsOut(ShoppingBasketUseCase, String)
  Samples executed: 5 of 50 (budget exhausted before completion)
  Pass rate at termination: 0.8000 (4/5), required: 0.5000
  Termination: Method token budget exhausted
  Details: Method token budget exhausted: 1000 tokens >= 1000 budget
  Elapsed: 4ms

```

An alternative budget behaviour, `EVALUATE_PARTIAL`, evaluates the partial results against the threshold as if the test had completed normally. This can produce either a pass or fail depending on the partial results.

```
═ VERDICT: PASS ══════════════════════════════════════════════════════ PUnit ═

  evaluatesPartialResultsOnBudgetPass(ShoppingBasketUseCase, String)
  Observed pass rate: 0.8000 (4/5) >= min pass rate: 0.5000
  Termination: Method token budget exhausted
  Details: Method token budget exhausted: 1000 tokens >= 1000 budget
  Elapsed: 3ms

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: failsWhenBudgetRunsOutTransparent(ShoppingBas... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5000 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     5
    Successes (k):       3
    Observed rate (p̂):  0.6000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.60 × 0.40 / 5) = 0.2191
    95% Confidence interval: [0.231, 0.882]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.60 - 0.50) / √(0.50 × 0.50 / 5)
                         z = 0.45
    
    p-value:             P(Z > 0.45) = 0.327
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.6000 falls below the
                         required threshold of 0.5000. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Small sample size (n=5). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Using inline threshold (no baseline spec). For
                         statistically-derived thresholds with confidence
                         intervals, run a MEASURE experiment first.

```

---

## 5. SLA compliance with contract provenance

When a threshold originates from an SLA, SLO, or policy, PUnit frames the hypothesis test accordingly and includes a THRESHOLD PROVENANCE section tracing the threshold back to its source. The hypothesis text adapts to the threshold origin (e.g. "system meets SLA requirement" vs "system meets SLO target" vs "system meets policy requirement").

### Summary

```
═ TEST CONFIGURATION FOR: slaPassShowsComplianceHypothesis ═══════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.5000 (SLA)
  Contract:         Acme Payment SLA v3.2 §4.1
  Samples:          50

```

```
═ STATISTICAL ANALYSIS FOR: slaPassShowsComplianceHypothesis(ShoppingBask... ═

  OBSERVED DATA
    Sample size (n):     33
    Successes (k):       25
    Observed rate (p̂):  0.7576
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.7576 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=33 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: slaPassShowsComplianceHypothesis(ShoppingBask... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5000 (system meets SLA requirement)
    H₁ (alternative): True success rate π < 0.5000 (system violates SLA)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     42
    Successes (k):       25
    Observed rate (p̂):  0.5952
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.5000 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.60 × 0.40 / 42) = 0.0757
    95% Confidence interval: [0.445, 0.730]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.60 - 0.50) / √(0.50 × 0.50 / 42)
                         z = 1.23
    
    p-value:             P(Z > 1.23) = 0.109
  
  VERDICT
    Result:              PASS
    Interpretation:      The observed success rate of 0.5952 meets the required
                         threshold of 0.5000. The system meets its SLA
                         requirement.
                         
    Caveat:              With n=42 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

---

## 6. Compliance undersized

When the sample size is too small to provide meaningful statistical evidence of compliance with a high-reliability SLA target, PUnit warns that a passing result is only a smoke-test-level observation. A failing result remains a reliable indication of non-conformance.

### Summary

```
═ TEST CONFIGURATION FOR: complianceUndersizedSmokeTestOnly ══════════ PUnit ═

  Mode:             SLA-DRIVEN
  Use Case:         ShoppingBasketUseCase
  Threshold:        0.9999 (SLA)
  Contract:         Acme Payment SLA v3.2 §4.1
  Samples:          50

```

```
═ STATISTICAL ANALYSIS FOR: complianceUndersizedSmokeTestOnly(ShoppingBas... ═

  OBSERVED DATA
    Sample size (n):     5
    Successes (k):       4
    Observed rate (p̂):  0.8000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9999 (Threshold specified directly in @ProbabilisticTest annotation)
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.8000 falls below the
                         required threshold of 0.9999. This indicates the
                         system is not meeting its SLA obligation.
                         
    Caveat:              Small sample size (n=5). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=5 and target of 0.9999, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.001). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: complianceUndersizedSmokeTestOnly(ShoppingBas... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.9999 (system meets SLA requirement)
    H₁ (alternative): True success rate π < 0.9999 (system violates SLA)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     2
    Successes (k):       1
    Observed rate (p̂):  0.5000
  
  BASELINE REFERENCE
    Source:              (inline threshold)
    Threshold:           0.9999 (Threshold specified directly in @ProbabilisticTest annotation)
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.50 × 0.50 / 2) = 0.3536
    95% Confidence interval: [0.095, 0.905]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.50 - 1.00) / √(1.00 × 0.00 / 2)
                         z = -70.70
    
    p-value:             P(Z > -70.70) = 1.000
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.5000 falls below the
                         required threshold of 0.9999. This indicates the
                         system is not meeting its SLA obligation.
                         
    Caveat:              Small sample size (n=2). Statistical conclusions
                         should be interpreted with caution. Consider
                         increasing sample size for more reliable results.
    Caveat:              Warning: sample not sized for compliance verification.
                         With n=2 and target of 0.9999, even zero failures
                         would not provide sufficient statistical evidence of
                         compliance (α=0.001). A PASS at this sample size is a
                         smoke-test-level observation, not a compliance
                         determination. Note: a FAIL verdict remains a reliable
                         indication of non-conformance.
  
  THRESHOLD PROVENANCE
    Threshold origin:    SLA
    Contract ref:        Acme Payment SLA v3.2 §4.1

```

---

## 7. Covariate misalignment

When the test runs under conditions that differ from the baseline (e.g. different time of day, weekday vs weekend), PUnit emits a BASELINE FOUND banner listing the misaligned covariates before the test runs. This also appears as a caveat in the verdict.

### Summary

```
═ BASELINE FOUND FOR USE CASE: ShoppingBasketUseCase ═════════════════ PUnit ═

  Baseline file:    ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml
  
  Please note, the following covariates do not match the baseline:
    - weekday_vs_weekend: baseline=Mo-Fr, test=Sa-So
    - time_of_day: baseline=06:13-06:13 Europe/London, test=20:02-20:02 Europe/Zurich
  
  Statistical comparison may be less reliable.
  Consider running a new MEASURE experiment under current conditions.

```

```
═ TEST CONFIGURATION FOR: temporalMismatchShowsCaveatTransparent ═════ PUnit ═

  Mode:             SPEC-DRIVEN
  Spec:             ShoppingBasketUseCase
  Threshold:        0.5656 (derived from baseline)
  Samples:          50

```

```
═ STATISTICAL ANALYSIS FOR: temporalMismatchShowsCaveatTransparent(Shoppi... ═

  OBSERVED DATA
    Sample size (n):     36
    Successes (k):       14
    Observed rate (p̂):  0.3889
  
  BASELINE REFERENCE
    Source:              ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml (generated unknown)
    Empirical basis:     1000 samples, 596 successes (0.5960)
    Threshold derivation:Lower bound of 95% CI = 0.5703, min pass rate = 0.5656
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.3889 falls below the
                         required threshold of 0.5656. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Covariate misalignment detected: the test conditions
                         differ from the baseline. Misaligned covariates:
                         weekday_vs_weekend (baseline=Mo-Fr, test=Sa-So),
                         time_of_day (baseline=06:13-06:13 Europe/London,
                         test=20:02-20:02 Europe/Zurich). Statistical
                         comparison may be less reliable.
    Caveat:              With n=36 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.

```

### Verbose

```
═ STATISTICAL ANALYSIS FOR: temporalMismatchShowsCaveatTransparent(Shoppi... ═

  HYPOTHESIS TEST
    H₀ (null):        True success rate π ≥ 0.5656 (success rate meets threshold)
    H₁ (alternative): True success rate π < 0.5656 (success rate below threshold)
    Test type:        One-sided binomial proportion test
  
  OBSERVED DATA
    Sample size (n):     38
    Successes (k):       16
    Observed rate (p̂):  0.4211
  
  BASELINE REFERENCE
    Source:              ShoppingBasketUseCase-9be9-a7ef-06d4-0aa5-a769.yaml (generated unknown)
    Empirical basis:     1000 samples, 596 successes (0.5960)
    Threshold derivation:Lower bound of 95% CI = 0.5703, min pass rate = 0.5656
  
  STATISTICAL INFERENCE
    Standard error:      SE = √(p̂(1-p̂)/n) = √(0.42 × 0.58 / 38) = 0.0801
    95% Confidence interval: [0.279, 0.578]
    
    Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                         z = (0.42 - 0.57) / √(0.57 × 0.43 / 38)
                         z = -1.80
    
    p-value:             P(Z > -1.80) = 0.964
  
  VERDICT
    Result:              FAIL
    Interpretation:      The observed success rate of 0.4211 falls below the
                         required threshold of 0.5656. This suggests the system
                         is not meeting its expected performance level.
                         
    Caveat:              Covariate misalignment detected: the test conditions
                         differ from the baseline. Misaligned covariates:
                         weekday_vs_weekend (baseline=Mo-Fr, test=Sa-So),
                         time_of_day (baseline=06:13-06:13 Europe/London,
                         test=20:02-20:02 Europe/Zurich). Statistical
                         comparison may be less reliable.
    Caveat:              With n=38 samples, subtle performance changes may not
                         be detectable. For higher sensitivity, consider
                         increasing sample size.

```

