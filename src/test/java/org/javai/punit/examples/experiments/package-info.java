/**
 * Example experiments demonstrating PUnit's MEASURE, EXPLORE, and OPTIMIZE modes.
 *
 * <p>Experiments are the <b>empirical foundation</b> of probabilistic testing. They
 * gather data about how a non-deterministic system actually behaves, producing
 * specifications that inform test thresholds.
 *
 * <h2>The Experimental Workflow</h2>
 * <pre>
 *   MEASURE experiment (many samples)
 *         |
 *         v
 *   ExecutionSpecification (YAML baseline)
 *         |
 *         v
 *   @ProbabilisticTest (derives threshold from baseline)
 * </pre>
 *
 * <h2>MEASURE Mode</h2>
 * <p>{@link org.javai.punit.examples.experiments.ShoppingBasketMeasure} runs many
 * samples (e.g., 1000) to establish a statistically robust baseline. The measured
 * success rate becomes the foundation for test thresholds.
 *
 * <h2>EXPLORE Mode</h2>
 * <p>{@link org.javai.punit.examples.experiments.ShoppingBasketExplore} compares
 * multiple configurations to understand how factors affect success rates. Useful
 * for identifying which model, temperature, or prompt variant performs best.
 *
 * <h2>OPTIMIZE Mode</h2>
 * <p>Two optimization examples demonstrate iterative parameter tuning:
 * <ul>
 *   <li>{@link org.javai.punit.examples.experiments.ShoppingBasketOptimizeTemperature} -
 *       Optimizes a numeric parameter (LLM temperature)</li>
 *   <li>{@link org.javai.punit.examples.experiments.ShoppingBasketOptimizePrompt} -
 *       Optimizes text (system prompt) through iterative refinement</li>
 * </ul>
 *
 * <h2>Supporting Classes</h2>
 * <ul>
 *   <li>{@link org.javai.punit.examples.experiments.ShoppingBasketSuccessRateScorer} - Scores
 *       optimization iterations by pass rate (defines 50% minimum threshold)</li>
 *   <li>{@link org.javai.punit.examples.experiments.TemperatureMutator} - Generates
 *       temperature variations for optimization</li>
 *   <li>{@link org.javai.punit.examples.experiments.ShoppingBasketPromptMutator} -
 *       Refines prompts based on failure analysis</li>
 * </ul>
 *
 * <h2>Running Experiments</h2>
 * <p>Use the dedicated {@code exp} (or {@code experiment}) Gradle task:
 * <pre>{@code
 * # Run a MEASURE experiment (entire class)
 * ./gradlew exp -Prun=ShoppingBasketMeasure
 *
 * # Run a specific EXPLORE experiment method
 * ./gradlew exp -Prun=ShoppingBasketExplore.exploreTemperature
 *
 * # Run an OPTIMIZE experiment
 * ./gradlew exp -Prun=ShoppingBasketOptimizeTemperature
 * }</pre>
 *
 * @see org.javai.punit.api.MeasureExperiment
 * @see org.javai.punit.api.ExploreExperiment
 * @see org.javai.punit.api.OptimizeExperiment
 */
package org.javai.punit.examples.experiments;
