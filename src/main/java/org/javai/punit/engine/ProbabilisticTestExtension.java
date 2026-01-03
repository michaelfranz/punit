package org.javai.punit.engine;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * JUnit 5 extension that implements probabilistic test execution.
 *
 * <p>This extension:
 * <ul>
 *   <li>Generates N sample invocations based on {@link ProbabilisticTest#samples()}</li>
 *   <li>Catches assertion failures and records them without failing the individual sample</li>
 *   <li>Aggregates results and determines final pass/fail based on observed pass rate</li>
 *   <li>Terminates early when success becomes mathematically impossible</li>
 *   <li>Publishes structured statistics via {@link org.junit.jupiter.api.TestReporter}</li>
 * </ul>
 */
public class ProbabilisticTestExtension implements
        TestTemplateInvocationContextProvider,
        InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ProbabilisticTestExtension.class);

    private static final String AGGREGATOR_KEY = "aggregator";
    private static final String CONFIG_KEY = "config";
    private static final String EVALUATOR_KEY = "evaluator";
    private static final String TERMINATED_KEY = "terminated";

    // ========== TestTemplateInvocationContextProvider ==========

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> AnnotationSupport.isAnnotated(m, ProbabilisticTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        
        Method testMethod = context.getRequiredTestMethod();
        ProbabilisticTest annotation = testMethod.getAnnotation(ProbabilisticTest.class);
        
        int samples = annotation.samples();
        double minPassRate = annotation.minPassRate();
        
        // Validate configuration
        validateConfiguration(samples, minPassRate, testMethod);
        
        // Store configuration and create components
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        TestConfiguration config = new TestConfiguration(samples, minPassRate);
        SampleResultAggregator aggregator = new SampleResultAggregator(samples);
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(samples, minPassRate);
        AtomicBoolean terminated = new AtomicBoolean(false);
        
        store.put(CONFIG_KEY, config);
        store.put(AGGREGATOR_KEY, aggregator);
        store.put(EVALUATOR_KEY, evaluator);
        store.put(TERMINATED_KEY, terminated);
        
        // Generate stream of invocation contexts with early termination support
        return createSampleStream(samples, terminated);
    }

    /**
     * Creates a stream of sample invocation contexts that can be short-circuited
     * when early termination is triggered.
     */
    private Stream<TestTemplateInvocationContext> createSampleStream(int samples, AtomicBoolean terminated) {
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminated.get())
                .map(sampleIndex -> new ProbabilisticTestInvocationContext(sampleIndex, samples));
    }

    private void validateConfiguration(int samples, double minPassRate, Method testMethod) {
        if (samples <= 0) {
            throw new ExtensionConfigurationException(
                    "Invalid @ProbabilisticTest configuration on " + testMethod.getName() +
                    ": samples must be >= 1, but was " + samples);
        }
        if (minPassRate < 0.0 || minPassRate > 1.0) {
            throw new ExtensionConfigurationException(
                    "Invalid @ProbabilisticTest configuration on " + testMethod.getName() +
                    ": minPassRate must be in range [0.0, 1.0], but was " + minPassRate);
        }
    }

    // ========== InvocationInterceptor ==========

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                             ReflectiveInvocationContext<Method> invocationContext,
                                             ExtensionContext extensionContext) throws Throwable {
        
        SampleResultAggregator aggregator = getAggregator(extensionContext);
        TestConfiguration config = getConfiguration(extensionContext);
        EarlyTerminationEvaluator evaluator = getEvaluator(extensionContext);
        AtomicBoolean terminated = getTerminatedFlag(extensionContext);
        
        // If already terminated, skip execution (shouldn't happen due to stream filtering, but be safe)
        if (terminated.get()) {
            invocation.skip();
            return;
        }
        
        try {
            invocation.proceed();
            aggregator.recordSuccess();
        } catch (AssertionError e) {
            // Record failure but don't rethrow - sample failed, but overall test continues
            aggregator.recordFailure(e);
        } catch (Throwable t) {
            // For Phase 1/2, treat all exceptions as failures
            aggregator.recordFailure(t);
        }
        
        // Check for early termination
        Optional<TerminationReason> terminationReason = evaluator.shouldTerminate(
                aggregator.getSuccesses(), aggregator.getSamplesExecuted());
        
        if (terminationReason.isPresent()) {
            TerminationReason reason = terminationReason.get();
            String details = evaluator.buildImpossibilityExplanation(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted());
            
            aggregator.setTerminated(reason, details);
            terminated.set(true);
            
            // Finalize immediately since we're terminating
            finalizeProbabilisticTest(extensionContext, aggregator, config);
        } else if (aggregator.getSamplesExecuted() >= config.samples()) {
            // All samples completed normally
            aggregator.setCompleted();
            finalizeProbabilisticTest(extensionContext, aggregator, config);
        }
    }

    private void finalizeProbabilisticTest(ExtensionContext context,
                                           SampleResultAggregator aggregator,
                                           TestConfiguration config) {
        
        FinalVerdictDecider decider = new FinalVerdictDecider();
        boolean passed = decider.isPassing(aggregator, config.minPassRate());
        
        // Publish structured results via TestReporter
        publishResults(context, aggregator, config, passed);
        
        // Throw assertion error if test failed
        if (!passed) {
            String message = decider.buildFailureMessage(aggregator, config.minPassRate());
            AssertionError error = new AssertionError(message);
            
            // Add example failures as suppressed exceptions
            for (Throwable failure : aggregator.getExampleFailures()) {
                error.addSuppressed(failure);
            }
            
            throw error;
        }
    }

    private void publishResults(ExtensionContext context,
                                SampleResultAggregator aggregator,
                                TestConfiguration config,
                                boolean passed) {
        
        TerminationReason reason = aggregator.getTerminationReason();
        String terminationReasonStr = reason != null ? reason.name() : TerminationReason.COMPLETED.name();
        
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.samples", String.valueOf(config.samples()));
        entries.put("punit.samplesExecuted", String.valueOf(aggregator.getSamplesExecuted()));
        entries.put("punit.successes", String.valueOf(aggregator.getSuccesses()));
        entries.put("punit.failures", String.valueOf(aggregator.getFailures()));
        entries.put("punit.minPassRate", String.format("%.4f", config.minPassRate()));
        entries.put("punit.observedPassRate", String.format("%.4f", aggregator.getObservedPassRate()));
        entries.put("punit.verdict", passed ? "PASS" : "FAIL");
        entries.put("punit.terminationReason", terminationReasonStr);
        entries.put("punit.elapsedMs", String.valueOf(aggregator.getElapsedMs()));
        
        context.publishReportEntry(entries);
    }

    // ========== Store Access Helpers ==========

    private SampleResultAggregator getAggregator(ExtensionContext context) {
        return getFromStoreOrParent(context, AGGREGATOR_KEY, SampleResultAggregator.class);
    }

    private TestConfiguration getConfiguration(ExtensionContext context) {
        return getFromStoreOrParent(context, CONFIG_KEY, TestConfiguration.class);
    }

    private EarlyTerminationEvaluator getEvaluator(ExtensionContext context) {
        return getFromStoreOrParent(context, EVALUATOR_KEY, EarlyTerminationEvaluator.class);
    }

    private AtomicBoolean getTerminatedFlag(ExtensionContext context) {
        return getFromStoreOrParent(context, TERMINATED_KEY, AtomicBoolean.class);
    }

    private <T> T getFromStoreOrParent(ExtensionContext context, String key, Class<T> type) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        T value = store.get(key, type);
        if (value != null) {
            return value;
        }
        
        // Try parent context (template context stores data at parent level)
        return context.getParent()
                .map(parent -> parent.getStore(NAMESPACE).get(key, type))
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find " + key + " in extension context store"));
    }

    // ========== Inner Classes ==========

    /**
     * Holds the test configuration extracted from the annotation.
     */
    private record TestConfiguration(int samples, double minPassRate) {}

    /**
     * Invocation context for a single sample execution.
     */
    private static class ProbabilisticTestInvocationContext implements TestTemplateInvocationContext {

        private final int sampleIndex;
        private final int totalSamples;

        ProbabilisticTestInvocationContext(int sampleIndex, int totalSamples) {
            this.sampleIndex = sampleIndex;
            this.totalSamples = totalSamples;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("Sample %d/%d", sampleIndex, totalSamples);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return Collections.emptyList();
        }
    }
}
