Code Review - Issue Found

TimeBudgetPolicy will never trigger during optimization

File: src/main/java/org/javai/punit/experiment/optimize/TimeBudgetPolicy.java:47

The TimeBudgetPolicy relies on history.totalDuration() to check if the time budget is exhausted. However, this will never work during the optimization loop.

The Problem:

OptimizationHistory.totalDuration() returns Duration.ZERO when endTime is null:
https://github.com/javai-org/punit/blob/abcbea053e07cb1a118f75007f87cec96d36b874/src/main/java/org/javai/punit/experiment/optimize/OptimizationHistory.java#L141-L147

During the optimization loop, OptimizationOrchestrator calls buildPartial() to check termination policies, but endTime is never set on the builder during the loop:
https://github.com/javai-org/punit/blob/abcbea053e07cb1a118f75007f87cec96d36b874/src/main/java/org/javai/punit/experiment/optimize/OptimizationOrchestrator.java#L162-L166

As a result, history.totalDuration() will always return Duration.ZERO, and the time budget check will never trigger.

Note: The test in TerminationPolicyTest.java passes only because it explicitly sets endTime on the history:
https://github.com/javai-org/punit/blob/abcbea053e07cb1a118f75007f87cec96d36b874/src/test/java/org/javai/punit/experiment/optimize/TerminationPolicyTest.java#L32-L40

Suggested Fix:

To make TimeBudgetPolicy functional, consider one of these approaches:

Track elapsed time independently: Store startTime in the history and calculate elapsed time as Duration.between(startTime, Instant.now()) instead of relying on endTime.

Update buildPartial() to set a provisional endTime: When building a partial history for termination checks, set endTime to the current time.

Add a dedicated method: Create a separate method like elapsedDuration() that calculates time from startTime to now, distinct from totalDuration() which represents the completed duration.

—