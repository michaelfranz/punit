package org.javai.punit.experiment.optimize;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for handling @OptimizeExperiment.
 *
 * <p>OPTIMIZE mode iteratively refines a single treatment factor to find its
 * optimal value. It runs multiple samples per iteration, scores each iteration,
 * and mutates the treatment factor until termination conditions are met.
 *
 * <p>Note: Full implementation is in progress. This strategy currently throws
 * UnsupportedOperationException for invocation context generation.
 */
public class OptimizeStrategy implements ExperimentModeStrategy {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    @Override
    public boolean supports(Method testMethod) {
        return testMethod.isAnnotationPresent(OptimizeExperiment.class);
    }

    @Override
    public ExperimentConfig parseConfig(Method testMethod) {
        OptimizeExperiment annotation = testMethod.getAnnotation(OptimizeExperiment.class);
        if (annotation == null) {
            throw new ExtensionConfigurationException(
                    "Method must be annotated with @OptimizeExperiment");
        }

        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseProvider.resolveId(useCaseClass);

        return new OptimizeConfig(
                useCaseClass,
                useCaseId,
                annotation.treatmentFactor(),
                annotation.scorer(),
                annotation.mutator(),
                annotation.objective(),
                annotation.samplesPerIteration(),
                annotation.maxIterations(),
                annotation.noImprovementWindow(),
                annotation.timeBudgetMs(),
                annotation.tokenBudget(),
                annotation.experimentId()
        );
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ExperimentConfig config,
            ExtensionContext context,
            ExtensionContext.Store store) {

        OptimizeConfig optimizeConfig = (OptimizeConfig) config;

        store.put("mode", ExperimentMode.OPTIMIZE);
        store.put("terminated", new AtomicBoolean(false));

        // TODO: Implement @OptimizeExperiment invocation context generation
        // This will use a custom Spliterator for lazy iteration
        throw new UnsupportedOperationException(
                "@OptimizeExperiment is not yet fully implemented. " +
                        "Use the OptimizationOrchestrator directly for now.");
    }

    @Override
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        // TODO: Implement @OptimizeExperiment interception
        throw new UnsupportedOperationException(
                "@OptimizeExperiment interception is not yet implemented.");
    }

    @Override
    public int computeTotalSamples(ExperimentConfig config, Method testMethod) {
        OptimizeConfig optimizeConfig = (OptimizeConfig) config;
        return optimizeConfig.maxTotalSamples();
    }
}
