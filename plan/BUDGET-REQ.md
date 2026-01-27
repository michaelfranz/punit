# Global budgeting enhancement for PUnit

This document describes the enhancement to PUnit to support global budgeting.

A code review revealed an issue with optimation experiments not respecting time budgets. This is documented in the file BUDGET-CODE-REVIEW.md.

This requirement is closely related to that code review feedback.

What we need is a way to track the total cost of an experiment or test run. All 'run' types must work in the same way. Conceivably it is possible that experiments and tests run in parallel, but we will keep the scope of the requirement to a single run of one or more experiments/tests.

There follow some options for how to implement this.

JUnit 5 gives you two good layers to hook into, depending on how “global” you need to be.

1) JUnit Platform level (truly whole-run): TestExecutionListener

At the Platform/Launcher layer you get one start + one finish callback for the full TestPlan:
•	testPlanExecutionStarted(TestPlan) (before any tests run)
•	testPlanExecutionFinished(TestPlan) (after all tests run)

That’s the cleanest place to publish “total cost” metrics.

How you’d use it for total cost
•	Create a listener that owns a thread-safe accumulator (e.g., LongAdder timeMs, LongAdder tokens).
•	In executionFinished(...), add per-test elapsed time (you can measure wall-clock around executionStarted/Finished, or use a more detailed approach if you already have timings).
•	In testPlanExecutionFinished(...), emit the final totals.

Registration is typically done via the Launcher’s listener registration mechanisms (including Java ServiceLoader auto-registration patterns used by the Platform).

2) Even broader session lifecycle: LauncherSessionListener

If you need lifecycle that spans the whole launcher session (covers discovery + potentially multiple executions within the same session), use LauncherSessionListener:
•	launcherSessionOpened(...) happens before discovery/execution begins
•	launcherSessionClosed(...) happens when nothing more will be discovered/executed

This is handy if you want “run totals” that include discovery overhead or if you’re integrating with a custom launcher.

3) Jupiter extension level (within the Jupiter engine): ExtensionContext.getRoot().getStore()

If you want the metrics to be updated from within Jupiter extensions (i.e., without requiring a custom launcher listener), you can keep a global accumulator in the root ExtensionContext store. The root store is explicitly positioned for values shared across multiple test classes.

The standard pattern is:
•	Put a CloseableResource into context.getRoot().getStore(namespace) exactly once.
•	JUnit will close it at the end of the engine lifecycle, giving you a reliable “finalize and publish totals” hook.

Making “tokens” easy for test authors to update

Platform listeners can observe execution, but they don’t automatically know your domain-specific “tokens”. The usual ergonomic approach is:
•	Provide a Jupiter extension that injects a recorder into tests via ParameterResolver, e.g. CostRecorder.
•	The recorder writes into the root store accumulator, so every test can do recorder.addTokens(n).

That gives you:
•	One global accumulator (root store / engine scope).
•	A nice API for test code to increment tokens.
•	One final emission point (root CloseableResource#close() or Platform testPlanExecutionFinished).

⸻

Rule of thumb
•	If you control the test execution environment (Gradle/Maven/custom launcher) and want a single authoritative “run summary”: use TestExecutionListener (and optionally LauncherSessionListener).  
•	If you want a library-style solution that test suites can adopt just by adding an extension: use Jupiter root store + CloseableResource, and optionally a ParameterResolver to let tests update “tokens”.

If you tell me whether you’re running under Gradle, Maven Surefire, IntelliJ, or a custom launcher, I’ll recommend the most frictionless wiring option for that environment.
