# Requirements for PUnit Author

## 1) Make developer intent explicit

- **Req 1.** Introduce an explicit intent/assurance declaration for probabilistic tests, with:
  - VERIFICATION (a.k.a. validation / compliance-capable / evidential)
  - SMOKE (sentinel / early-warning / non-verificatory)
- **Req 2.** Default intent MUST be VERIFICATION to prevent accidental "verification theatre".
- **Req 3.** Provide an ergonomically simple way to declare smoke intent:
  - `intent = SMOKE`. Choose an appropriate place to declare this as it will be used in one place only: In the ProbabilisticTest annotation.

## 2) Enforce intent-sample/statistical-feasibility consistency

- **Req 4.** PUnit MUST compute feasibility for the declared intent based on the developer-specified statistical parameters, including at minimum:
  - N (sample size)
  - target pass rate `p₀` (e.g., `minPassRate`); bear in mind the minPassRate is obtained from a baseline spec in spec'ed tests.
  - declared confidence / risk level (e.g., one-sided α)
  - (when applicable) minimal detectable effect (MDE) / power requirements
- **Req 5.** If `intent = VERIFICATION` and feasibility conditions are not met (e.g., N too small, required confidence too high, MDE too low for given N/power), the test MUST:
  - fail out of hand (hard fail) before evaluating the SUT statistically,
  - emit a succinct, statistically unambiguous explanation of why verification is impossible under the configured parameters,
  - include the required minimum (e.g., `N_min`, or adjusted α/MDE) and the configured values.
- **Req 6.** This failure MUST be non-ignorable in CI:
  - it MUST result in a test failure (non-zero / red), not merely a warning.
- **Req 7.** The failure MUST be categorized distinctly from SUT failure, e.g.:
  - `MISCONFIGURED`, `INVALID_CONFIGURATION`, or similar,
  - to avoid misattributing blame to the service under test.
- **Req 7b.** PUnit must continue to prohibit misconfiguration of the test itself, which includes specifying non-sensical combinations of attribute values, or non-sensical values like confidence = 1.0 etc. 

## 3) SLA-anchored scenarios must be handled as a special, high-risk semantics case

- **Req 8.** When threshold origin (provenance) is indicated (e.g., `thresholdOrigin = SLA` and/or `contractRef` present), PUnit MUST treat examine the threshold origin's 'isNormative()' value. If the value is `true` the risk of misinterpretation as high, and so VERIFICATION intent with undersized N MUST hard-fail as misconfiguration (Req 5-7).
- **Req 9.** PUnit MUST support "contract-anchored smoke testing" explicitly:
  - `intent = SMOKE` is the sanctioned way to run small-N checks against an SLA-derived target without making compliance claims.

## 4) Output semantics must prevent category errors

- **Req 10.** Test reporting (verdicts) MUST clearly separate:
  - intent (SMOKE vs VERIFICATION),
  - what was observed (e.g., `p̂`, failures, N),
  - what can be concluded (evidential claim vs non-evidential observation).
- **Req 11.** When `intent = SMOKE`, the report MUST explicitly avoid compliance/legal language such as "not meeting SLA obligation".
  - Use language like "inconsistent with SLA target", "incident/regression signal", "requires investigation".
- **Req 12.** When `intent = SMOKE` thresholdOrigin.isNormative() == true, the output MUST include one of the following phrases (based on computed verification feasibility):
  - If verificationCapable == false (given configured n, target p_0, α, method, and any power/MDE constraints), output: “sample not sized for verification (normative origin: SLA, SLO, Policy)” 
  - if verificationCapable == true, but the user has specified an intent of SMOKE, this is not strictly a misconfiguration, but it deserves a hint in the output e.g. “sample is sized for verification, consider removing intent = SMOKE”
- **Req 12b.** to compute the `verificationCapable` value define a dedicated function: feasibleVerification(p0, n, alpha, method, power, mde, …). This function is central to the credibility of the PUNit framework and must be well-documented, isolated from all other functionality to it can be easily tested across all plausible scenarios, which PUnit may encounter. The test itself must be very easy to navigate, with each test fully self-contained and self-documenting.
- **Req 13.** DELETED


## 5) Transparent, auditable statistical justification

- **Req 14.** Whenever PUnit hard-fails due to infeasible VERIFICATION, it MUST print:
  - the criterion used (e.g., one-sided lower confidence bound requirement / power calculation),
  - the α (and power if relevant),
  - target `p₀`,
  - computed `N_min` (or equivalent feasibility boundary),
  - the assumptions (at least "i.i.d./stationary Bernoulli" or whatever model is used).
- **Req 15.** "Transparent stats" mode SHOULD include:
  - observed failures, `p̂`,
  - confidence bounds / p-values where appropriate,
  - but MUST not allow those numbers to override the intent semantics.

## 6) Baseline spec integrity must be enforced as a hard gate

- **Req 16.** Baseline specs used as an oracle MUST be treated as immutable artifacts once generated and deployed.
- **Req 17.** PUnit MUST continue to use an integrity check (fingerprint) and MUST hard-fail out of hand if the fingerprint verification fails:
  - no PASS/FAIL about the SUT may be emitted,
  - verdict category MUST be distinct (e.g., `BASELINE_INTEGRITY_VIOLATION` / `INVALID_BASELINE_SPEC`).
- **Req 18.** The integrity failure message MUST be succinct and actionable, including:
  - baseline spec identifier/path,
  - expected vs actual fingerprint,
  - generator/spec version (if available),
  - remediation: "restore from trusted source" or "regenerate via experiment tooling".

## 7) Accept developer parameters under SMOKE intent

- **Req 20.** If the developer sets `intent = SMOKE`, PUnit MUST permit configurations that would be infeasible for VERIFICATION (small N, stringent confidence, low MDE), but MUST:
  - label the result as SMOKE,
  - include the "not sized for verification" caveat when relevant (especially for SLA-anchored targets),
  - avoid compliance language,
  - treat failures as high-signal alerts but not as formal non-compliance determinations.

## 8) Documentation requirements

- **Req 21.** PUnit documentation MUST explicitly state the asymmetry:
  - SMOKE: PASS is weak/non-evidential; FAIL is a strong signal (subject to model assumptions).
  - VERIFICATION: PASS/FAIL are evidential within declared α/power and assumptions.
- **Req 22.** Docs MUST explain why extreme SLAs (e.g., 0.9999) demand large N for verification, and why small-N "passes" cannot be marketed as compliance.
- **Req 23.** Use the recently created VERDICT-CATALOG generator to generate a comprehensive list of all verdict categories. Note that this requirement introduces NEW verdict catagories namely those which indicate misconfiguration, tampering etc. 

---

These requirements jointly enforce the core principle that developer intent governs interpretation, with VERIFICATION as the safe default, and with hard failures used to prevent silent category errors whenever the configured statistical goals are unattainable.
