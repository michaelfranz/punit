package org.javai.punit.experiment.optimize;

import org.javai.punit.api.ExperimentMode;
import org.javai.punit.experiment.engine.ExperimentConfig;

/**
 * Configuration for @OptimizeExperiment.
 *
 * <p>OPTIMIZE mode iteratively refines a single treatment factor to find its
 * optimal value. It runs multiple samples per iteration, scores each iteration,
 * and mutates the treatment factor until termination conditions are met.
 *
 * <p>This config holds the annotation-parsed values (class references).
 * The {@link OptimizationConfig} class holds the runtime objects (instantiated
 * scorer, mutator, etc.) for the {@link OptimizationOrchestrator}.
 *
 * @param useCaseClass the use case class to test
 * @param useCaseId resolved use case identifier
 * @param treatmentFactor name of the factor to optimize
 * @param scorerClass scorer class for evaluating iterations
 * @param mutatorClass mutator class for generating new factor values
 * @param objective optimization objective (MAXIMIZE or MINIMIZE)
 * @param samplesPerIteration samples to run per iteration
 * @param maxIterations maximum iterations before termination
 * @param noImprovementWindow iterations without improvement before termination
 * @param timeBudgetMs time budget in milliseconds (0 = unlimited)
 * @param tokenBudget token budget (0 = unlimited)
 * @param experimentId experiment identifier for output naming
 */
public record OptimizeConfig(
        Class<?> useCaseClass,
        String useCaseId,
        String treatmentFactor,
        Class<? extends Scorer<OptimizationIterationAggregate>> scorerClass,
        Class<? extends FactorMutator<?>> mutatorClass,
        OptimizationObjective objective,
        int samplesPerIteration,
        int maxIterations,
        int noImprovementWindow,
        long timeBudgetMs,
        long tokenBudget,
        String experimentId
) implements ExperimentConfig {

    @Override
    public ExperimentMode mode() {
        return ExperimentMode.OPTIMIZE;
    }

    /**
     * Computes the maximum possible samples across all iterations.
     *
     * @return samplesPerIteration × maxIterations
     */
    public int maxTotalSamples() {
        return samplesPerIteration * maxIterations;
    }
}
