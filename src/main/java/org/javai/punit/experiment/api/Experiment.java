package org.javai.punit.experiment.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a method as an experiment that executes a use case repeatedly to gather empirical data.
 *
 * <p>Experiments are exploratory: they produce empirical baselines but never pass/fail verdicts.
 * Experiment results are informational only and never gate CI.
 *
 * <h2>Experiment Modes</h2>
 * <p>Two modes are supported:
 * <ul>
 *   <li><b>BASELINE</b> (default): Single configuration, large sample size (1000+)
 *       for precise statistical estimation</li>
 *   <li><b>EXPLORE</b>: Multiple configurations from a {@link FactorSource},
 *       small sample size per config (default: 1) for rapid comparison</li>
 * </ul>
 *
 * <h2>Example: BASELINE Mode</h2>
 * <pre>{@code
 * @Experiment(
 *     useCase = ShoppingUseCase.class,
 *     samples = 1000,
 *     timeBudgetMs = 600_000
 * )
 * void measureProductSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones", context));
 * }
 * }</pre>
 *
 * <h2>Example: EXPLORE Mode</h2>
 * <pre>{@code
 * @Experiment(
 *     mode = ExperimentMode.EXPLORE,
 *     useCase = ShoppingUseCase.class,
 *     samplesPerConfig = 1  // Default: 1 for fast exploration
 * )
 * @FactorSource("modelConfigs")
 * void exploreModels(
 *     @Factor("model") String model,
 *     @Factor("temperature") double temperature,
 *     UseCaseProvider provider,
 *     ResultCaptor captor
 * ) {
 *     // Configure use case with factor values
 *     provider.register(ShoppingUseCase.class, () ->
 *         new ShoppingUseCase(createAssistant(model, temperature))
 *     );
 *     ShoppingUseCase useCase = provider.getInstance(ShoppingUseCase.class);
 *     captor.record(useCase.searchProducts("headphones"));
 * }
 *
 * static Stream<Arguments> modelConfigs() {
 *     return Stream.of(
 *         Arguments.of("gpt-4", 0.0),
 *         Arguments.of("gpt-4", 0.7),
 *         Arguments.of("gpt-3.5-turbo", 0.0)
 *     );
 * }
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><strong>No pass/fail</strong>: Experiments never fail (except for infrastructure errors)</li>
 *   <li><strong>Produces empirical baseline</strong>: After execution, generates baseline file(s)</li>
 *   <li><strong>Never gates CI</strong>: Results are informational only</li>
 *   <li><strong>Use case injection</strong>: Configure via {@link UseCaseProvider}</li>
 *   <li><strong>Result capture</strong>: Use {@link ResultCaptor} to record results for aggregation</li>
 * </ul>
 *
 * @see ExperimentMode
 * @see FactorSource
 * @see Factor
 * @see UseCase
 * @see UseCaseProvider
 * @see ResultCaptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ExperimentExtension.class)
public @interface Experiment {
    
    /**
     * The experiment mode: BASELINE or EXPLORE.
     *
     * <p>Default is {@link ExperimentMode#BASELINE}, which runs a single configuration
     * with many samples for precise statistical estimation.
     *
     * <p>Use {@link ExperimentMode#EXPLORE} with {@link FactorSource} to compare
     * multiple configurations.
     *
     * @return the experiment mode
     */
    ExperimentMode mode() default ExperimentMode.BASELINE;
    
    /**
     * The use case class to execute.
     *
     * <p>The use case ID is resolved from the class:
     * <ol>
     *   <li>If {@code @UseCase} annotation is present with a value, use that</li>
     *   <li>Otherwise, use the simple class name</li>
     * </ol>
     *
     * <p>The use case instance is obtained from a {@link UseCaseProvider} registered
     * as a JUnit extension. Configure the provider in {@code @BeforeEach}.
     *
     * @return the use case class
     */
    Class<?> useCase() default Void.class;
    
    /**
     * The use case ID to execute (legacy, prefer {@link #useCase()} class reference).
     *
     * <p>If both {@code useCaseId} and {@code useCase} are specified, the class reference
     * takes precedence.
     *
     * @return the use case ID
     * @deprecated Use {@link #useCase()} with class reference for type safety
     */
    @Deprecated
    String useCaseId() default "";
    
    /**
     * Number of sample invocations to execute in BASELINE mode.
     *
     * <p>This is used when {@code mode = ExperimentMode.BASELINE} (the default).
     * For EXPLORE mode, use {@link #samplesPerConfig()} instead.
     *
     * <p>Must be ≥ 1. Default: 1000 for statistically reliable baselines.
     *
     * @return the number of samples
     */
    int samples() default 1000;
    
    /**
     * Number of samples per configuration in EXPLORE mode.
     *
     * <p>This is used when {@code mode = ExperimentMode.EXPLORE}.
     *
     * <p>Default: 1 (single sample per config for fast initial filtering).
     * Increase to 10+ when comparing finalists for statistical rigor.
     *
     * <p>Typical workflow:
     * <ol>
     *   <li>Phase 1: {@code samplesPerConfig = 1} — quick pass to find working configs</li>
     *   <li>Phase 2: {@code samplesPerConfig = 10} — compare promising configs</li>
     *   <li>Final: Switch to BASELINE mode with 1000+ samples for chosen config</li>
     * </ol>
     *
     * @return the number of samples per config
     */
    int samplesPerConfig() default 1;
    
    /**
     * Maximum wall-clock time budget in milliseconds.
     *
     * <p>For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs() default 0;
    
    /**
     * Maximum token budget for all samples.
     *
     * <p>For multi-config experiments, this applies across ALL configs combined.
     * 0 = unlimited. Default: 0.
     *
     * @return the token budget
     */
    long tokenBudget() default 0;
    
    /**
     * Directory for storing generated baselines.
     *
     * <p>For multi-config experiments, a subdirectory is created per experiment.
     * Relative to test resources root.
     * Default: "punit/baselines"
     *
     * @return the baseline output directory
     */
    String baselineOutputDir() default "punit/baselines";
    
    /**
     * Unique identifier for this experiment.
     *
     * <p>Required for multi-config experiments; optional for single-config.
     * Used as directory name for output files.
     *
     * @return the experiment ID
     */
    String experimentId() default "";
    
    /**
     * Reference to a YAML ExperimentDesign file (for complex multi-config experiments).
     *
     * <p>When provided, overrides annotation-based configuration.
     * Path relative to test resources root.
     *
     * @return the design file path
     */
    String designFile() default "";
    
    /**
     * Whether to overwrite existing baseline files.
     *
     * @return true to overwrite existing baselines
     */
    boolean overwriteBaseline() default false;
    
    /**
     * Output format for baseline files.
     *
     * <p>Supported values: "yaml" (default), "json".
     *
     * @return the output format
     */
    String outputFormat() default "yaml";
}

