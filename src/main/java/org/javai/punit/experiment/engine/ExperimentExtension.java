package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ResultCaptor;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * JUnit 5 extension that drives experiment execution.
 *
 * <p>This extension:
 * <ul>
 *   <li>Injects {@link ResultCaptor} into experiment methods</li>
 *   <li>Executes experiment methods repeatedly as samples</li>
 *   <li>Aggregates results from the captor</li>
 *   <li>Generates empirical baselines</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The new architecture uses a method-execution model:
 * <ol>
 *   <li>The experiment method receives a {@link ResultCaptor} parameter</li>
 *   <li>The method executes and calls the use case</li>
 *   <li>The method records the result via {@code captor.record(result)}</li>
 *   <li>The extension aggregates results after each method execution</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones", context));
 * }
 * }</pre>
 */
public class ExperimentExtension implements TestTemplateInvocationContextProvider, 
        InvocationInterceptor {
    
    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create(ExperimentExtension.class);
    
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
            .map(m -> m.isAnnotationPresent(Experiment.class))
            .orElse(false);
    }
    
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        Experiment annotation = testMethod.getAnnotation(Experiment.class);
        
        // Resolve use case ID from class reference or legacy string
        String useCaseId = resolveUseCaseId(annotation);
        int samples = annotation.samples();
        
        // Store annotation and metadata for later
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("annotation", annotation);
        store.put("useCaseId", useCaseId);
        store.put("useCaseClass", annotation.useCase());
        store.put("testMethod", testMethod);
        
        // Create aggregator
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samples);
        store.put("aggregator", aggregator);
        
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger currentSample = new AtomicInteger(0);
        store.put("terminated", terminated);
        store.put("currentSample", currentSample);
        store.put("startTimeMs", System.currentTimeMillis());
        store.put("contextBuilt", new AtomicBoolean(false));
        
        // Generate sample stream - each invocation gets its own captor
        return Stream.iterate(1, i -> i + 1)
            .limit(samples)
            .takeWhile(i -> !terminated.get())
            .map(i -> new ExperimentInvocationContext(i, samples, useCaseId, new ResultCaptor()));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INVOCATION INTERCEPTOR - Executes experiment and captures results
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        
        // Use parent context's store since provideTestTemplateInvocationContexts stored data there
        ExtensionContext parentContext = extensionContext.getParent().orElse(extensionContext);
        ExtensionContext.Store store = parentContext.getStore(NAMESPACE);
        
        // Build context on first invocation
        AtomicBoolean contextBuilt = store.get("contextBuilt", AtomicBoolean.class);
        if (contextBuilt != null && !contextBuilt.get()) {
            Method testMethod = store.get("testMethod", Method.class);
            UseCaseContext useCaseContext = buildContext(testMethod);
            store.put("useCaseContext", useCaseContext);
            contextBuilt.set(true);
        }
        
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        Experiment annotation = store.get("annotation", Experiment.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        AtomicInteger currentSample = store.get("currentSample", AtomicInteger.class);
        Long startTimeMs = store.get("startTimeMs", Long.class);
        
        int sample = currentSample.incrementAndGet();
        
        // Check time budget
        if (annotation.timeBudgetMs() > 0) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed >= annotation.timeBudgetMs()) {
                terminated.set(true);
                aggregator.setTerminated("TIME_BUDGET_EXHAUSTED", 
                    "Time budget of " + annotation.timeBudgetMs() + "ms exceeded");
                invocation.skip();
                checkAndGenerateBaseline(extensionContext, store);
                return;
            }
        }
        
        // Check token budget
        if (annotation.tokenBudget() > 0 && aggregator.getTotalTokens() >= annotation.tokenBudget()) {
            terminated.set(true);
            aggregator.setTerminated("TOKEN_BUDGET_EXHAUSTED",
                "Token budget of " + annotation.tokenBudget() + " exceeded");
            invocation.skip();
            checkAndGenerateBaseline(extensionContext, store);
            return;
        }
        
        // Get captor from the current invocation context's store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        ResultCaptor captor = invocationStore.get("captor", ResultCaptor.class);
        
        try {
            // Actually execute the experiment method
            // The method will:
            // 1. Receive the use case (injected by UseCaseProvider)
            // 2. Receive the captor (injected by CaptorParameterResolver)
            // 3. Execute the use case and call captor.record(result)
            invocation.proceed();
            
            // Read result from the captor and aggregate
            if (captor != null && captor.hasResult()) {
                UseCaseResult result = captor.getResult();
                boolean success = determineSuccess(result);
                
                if (success) {
                    aggregator.recordSuccess(result);
                } else {
                    String failureCategory = determineFailureCategory(result);
                    aggregator.recordFailure(result, failureCategory);
                }
            } else if (captor != null && captor.hasException()) {
                aggregator.recordException(captor.getException());
            } else {
                // No result recorded - treat as success with no data
                // (The method ran but didn't use the captor)
                aggregator.recordSuccess(UseCaseResult.builder().value("recorded", false).build());
            }
            
        } catch (Throwable e) {
            aggregator.recordException(e);
        }
        
        // Report progress
        reportProgress(extensionContext, aggregator, sample, annotation.samples());
        
        // Check if this is the last sample
        if (sample >= annotation.samples() || terminated.get()) {
            if (!terminated.get()) {
                aggregator.setCompleted();
            }
            generateBaseline(extensionContext, store);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONTEXT BUILDING
    // ═══════════════════════════════════════════════════════════════════════════
    
    @SuppressWarnings("unused")
    private UseCaseContext buildContext(Method method) {
        // Context is now managed by the experiment method body and UseCaseProvider
        // This method returns an empty context for baseline metadata purposes
        return DefaultUseCaseContext.builder().build();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT DETERMINATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private boolean determineSuccess(UseCaseResult result) {
        // Check for common success indicators (in priority order)
        if (result.hasValue("success")) {
            return result.getBoolean("success", false);
        }
        if (result.hasValue("isSuccess")) {
            return result.getBoolean("isSuccess", false);
        }
        if (result.hasValue("passed")) {
            return result.getBoolean("passed", false);
        }
        if (result.hasValue("isValid")) {
            return result.getBoolean("isValid", false);
        }
        if (result.hasValue("isValidJson")) {
            return result.getBoolean("isValidJson", false);
        }
        if (result.hasValue("error")) {
            return result.getValue("error", Object.class).isEmpty();
        }
        
        // Default to success if no failure indicators
        return true;
    }
    
    private String determineFailureCategory(UseCaseResult result) {
        // Check for common failure category indicators
        if (result.hasValue("failureCategory")) {
            return result.getString("failureCategory", "unknown");
        }
        if (result.hasValue("errorType")) {
            return result.getString("errorType", "unknown");
        }
        if (result.hasValue("errorCode")) {
            return result.getString("errorCode", "unknown");
        }
        return "unknown";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESS & BASELINE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void reportProgress(ExtensionContext context, ExperimentResultAggregator aggregator, 
                               int currentSample, int totalSamples) {
        // Report via JUnit TestReporter if available
        context.publishReportEntry("punit.mode", "EXPERIMENT");
        context.publishReportEntry("punit.sample", currentSample + "/" + totalSamples);
        context.publishReportEntry("punit.successRate", 
            String.format("%.2f%%", aggregator.getObservedSuccessRate() * 100));
    }
    
    private void checkAndGenerateBaseline(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        if (aggregator.getSamplesExecuted() > 0) {
            generateBaseline(context, store);
        }
    }
    
    private void generateBaseline(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        UseCaseContext useCaseContext = store.get("useCaseContext", UseCaseContext.class);
        Experiment annotation = store.get("annotation", Experiment.class);
        
        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
            aggregator,
            context.getTestClass().orElse(null),
            context.getTestMethod().orElse(null),
            useCaseContext
        );
        
        // Write baseline to file
        try {
            Path outputPath = resolveBaselineOutputPath(annotation, aggregator.getUseCaseId());
            BaselineWriter writer = new BaselineWriter();
            writer.write(baseline, outputPath, annotation.outputFormat());
            
            context.publishReportEntry("punit.baseline.outputPath", outputPath.toString());
        } catch (IOException e) {
            context.publishReportEntry("punit.baseline.error", e.getMessage());
        }
        
        // Publish final report
        publishFinalReport(context, aggregator);
    }
    
    private Path resolveBaselineOutputPath(Experiment annotation, String useCaseId) {
        String filename = useCaseId.replace('.', '-') + "." + annotation.outputFormat();
        
        // Check for system property override (set by Gradle experiment task)
        String outputDirOverride = System.getProperty("punit.baseline.outputDir");
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            return Paths.get(outputDirOverride, filename);
        }
        
        // Fallback: use annotation's baselineOutputDir relative to current directory
        String baseDir = annotation.baselineOutputDir();
        return Paths.get(baseDir, filename);
    }
    
    private void publishFinalReport(ExtensionContext context, ExperimentResultAggregator aggregator) {
        context.publishReportEntry("punit.experiment.complete", "true");
        context.publishReportEntry("punit.useCaseId", aggregator.getUseCaseId());
        context.publishReportEntry("punit.samplesExecuted", 
            String.valueOf(aggregator.getSamplesExecuted()));
        context.publishReportEntry("punit.successRate", 
            String.format("%.4f", aggregator.getObservedSuccessRate()));
        context.publishReportEntry("punit.standardError",
            String.format("%.4f", aggregator.getStandardError()));
        context.publishReportEntry("punit.terminationReason", 
            aggregator.getTerminationReason());
        context.publishReportEntry("punit.elapsedMs", 
            String.valueOf(aggregator.getElapsedMs()));
        context.publishReportEntry("punit.totalTokens",
            String.valueOf(aggregator.getTotalTokens()));
    }

    /**
     * Resolves the use case ID from the annotation.
     *
     * <p>Priority:
     * <ol>
     *   <li>If {@code useCase} class is specified (not Void.class), use {@link UseCaseProvider#resolveId}</li>
     *   <li>Otherwise, use the legacy {@code useCaseId} string</li>
     * </ol>
     */
    private String resolveUseCaseId(Experiment annotation) {
        Class<?> useCaseClass = annotation.useCase();
        
        // New pattern: use case class reference
        if (useCaseClass != null && useCaseClass != Void.class) {
            return UseCaseProvider.resolveId(useCaseClass);
        }
        
        // Legacy pattern: string ID
        String legacyId = annotation.useCaseId();
        if (legacyId != null && !legacyId.isEmpty()) {
            return legacyId;
        }
        
        throw new ExtensionConfigurationException(
            "Experiment must specify either useCase class or useCaseId. " +
            "Recommended: @Experiment(useCase = MyUseCase.class, ...)");
    }

    /**
     * Invocation context for a single experiment sample.
     */
    private record ExperimentInvocationContext(int sampleNumber, int totalSamples,
                                               String useCaseId, ResultCaptor captor) 
            implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new CaptorParameterResolver(captor));
        }
    }
    
    /**
     * Parameter resolver that provides the ResultCaptor for the current invocation.
     */
    private static class CaptorParameterResolver implements ParameterResolver {
        private final ResultCaptor captor;
        
        CaptorParameterResolver(ResultCaptor captor) {
            this.captor = captor;
        }
        
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == ResultCaptor.class;
        }
        
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            // Also store in the extension context so the interceptor can access it
            extensionContext.getStore(NAMESPACE).put("captor", captor);
            return captor;
        }
    }
}
