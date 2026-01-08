# REQ-01: IDE Support for Statistical Test Verdicts

## Status
**Open** - Awaiting ecosystem adoption

## Priority
**High** - Core UX issue affecting developer experience

## Problem Statement

JUnit 5's test result model aggregates child results: if any child test fails, the parent container is marked as failed. This creates a fundamental UX problem for PUnit:

1. **IDE shows class as ❌ FAILED** even when PUnit's statistical verdict is PASS
2. **Developers must read console output** to see the true verdict
3. **Visual feedback is misleading** - red icons suggest failure when the test actually passed

### Example

```
❌ MyProbabilisticTest                    ← IDE shows FAILED
    ❌ sample() > Sample 1/100            ← 3 samples failed
    ❌ sample() > Sample 23/100
    ❌ sample() > Sample 47/100
    ✅ ... (95 samples passed)
    
Console: "PUnit PASSED: 97% (97/100) >= 95%"  ← But PUnit says PASSED
```

## Root Cause Analysis

### JUnit 5 Architecture

1. **`TestExecutionResult`** - Binary status: `SUCCESSFUL`, `ABORTED`, or `FAILED`
2. **Result aggregation** - Containers aggregate child results; any child failure = container failure
3. **No extension point** for overriding container verdict based on child statistics

### IDE Behavior

1. **IntelliJ IDEA, Eclipse, VS Code** - All display JUnit's aggregated result
2. **Tree icons** - Red ❌ for any container with failed children
3. **No hook** for frameworks to provide alternative aggregation logic

### Investigated Solutions

| Approach | Outcome |
|----------|---------|
| `TestWatcher` | Cannot throw exceptions; observational only |
| `ExecutionCondition` | Can skip tests, but cannot change verdict |
| `TestExecutionExceptionHandler` | Cannot suppress failures for result purposes |
| `@RepeatedTest(failureThreshold)` | Only controls early termination, not parent verdict |
| Custom `TestEngine` | Would require IDE adaptation to new result model |
| Fork JUnit 5 | Would require IDE adaptation |

## Requirements

### REQ-01.1: JUnit Enhancement Request

**Request:** Add extension point for statistical test frameworks to provide custom container verdicts.

**Proposed API:**
```java
public interface ContainerVerdictProvider {
    /**
     * Called after all children complete to determine container verdict.
     * 
     * @param childResults list of child test results
     * @return the container's verdict (may differ from simple aggregation)
     */
    TestExecutionResult determineContainerVerdict(List<TestExecutionResult> childResults);
}
```

**Status:** Not yet submitted to JUnit team

### REQ-01.2: IDE Plugin for PUnit

**Request:** Create IDE plugins that recognize PUnit's console output and display the statistical verdict.

**Approach:**
1. Parse `═══════════════════════════════════════` console summary blocks
2. Extract "PUnit PASSED" or "PUnit FAILED" verdict
3. Override the tree icon for the test class

**Platforms:**
- IntelliJ IDEA (plugin)
- Eclipse (plugin)
- VS Code (extension)

**Status:** Not started

### REQ-01.3: JUnit Platform Test Result Extension

**Request:** Propose new test result status to JUnit Platform.

**Proposed Status:**
```java
public enum TestExecutionResult.Status {
    SUCCESSFUL,
    ABORTED,
    FAILED,
    STATISTICALLY_PASSED,  // NEW: Some children failed, but statistical criteria met
    STATISTICALLY_FAILED   // NEW: No individual failures, but statistical criteria not met
}
```

**Status:** Conceptual - requires community discussion

## Workarounds (Current)

1. **Console summary** - PUnit prints a prominent summary with the true verdict
2. **Structured report entries** - Parseable `punit.verdict=PASS` in TestReporter output
3. **CI/CD parsing** - Automation can extract verdict from structured output

## Acceptance Criteria

- [ ] IDE shows ✅ when PUnit says PASSED (even if some samples failed)
- [ ] IDE shows ❌ when PUnit says FAILED
- [ ] Click on test shows statistical summary (pass rate, threshold, samples)
- [ ] No confusion between individual sample failures and overall test verdict

## Dependencies

- JUnit 5 team adoption of extension point (REQ-01.1)
- IDE vendor adoption of new result model (REQ-01.2, REQ-01.3)
- PUnit community adoption driving demand

## Timeline

| Phase | Action | Timeframe |
|-------|--------|-----------|
| 1 | Document limitation (this document) | ✅ Complete |
| 2 | Build community/adoption | Ongoing |
| 3 | Propose JUnit enhancement | After adoption |
| 4 | IDE plugin development | After JUnit enhancement |

## References

- [JUnit 5 Extension Model](https://junit.org/junit5/docs/current/user-guide/#extensions)
- [JUnit 5.13 RepetitionExtension](https://github.com/junit-team/junit5) - Pattern for `ExecutionCondition`
- [IntelliJ IDEA Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

