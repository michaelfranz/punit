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
 * <h2>Example Usage</h2>
 * <pre>{@code
 * class ShoppingExperiment {
 *
 *     @RegisterExtension
 *     UseCaseProvider provider = new UseCaseProvider();
 *
 *     private UseCaseContext context;
 *
 *     @BeforeEach
 *     void setUp() {
 *         provider.register(ShoppingUseCase.class, () ->
 *             new ShoppingUseCase(new MockShoppingAssistant(
 *                 MockConfiguration.experimentRealistic()
 *             ))
 *         );
 *         context = DefaultUseCaseContext.builder()
 *             .backend("mock")
 *             .build();
 *     }
 *
 *     @Experiment(
 *         useCase = ShoppingUseCase.class,
 *         samples = 1000,
 *         timeBudgetMs = 600_000,
 *         tokenBudget = 500_000
 *     )
 *     void measureProductSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *         captor.record(useCase.searchProducts("wireless headphones", context));
 *     }
 * }
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><strong>No pass/fail</strong>: Experiments never fail (except for infrastructure errors)</li>
 *   <li><strong>Produces empirical baseline</strong>: After execution, generates a baseline file</li>
 *   <li><strong>Never gates CI</strong>: Results are informational only</li>
 *   <li><strong>Use case injection</strong>: Configure via {@link UseCaseProvider} in {@code @BeforeEach}</li>
 *   <li><strong>Result capture</strong>: Use {@link ResultCaptor} to record results for aggregation</li>
 * </ul>
 *
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
     * Number of sample invocations to execute (for single-config experiments).
     *
     * <p>For multi-config experiments, use {@link #samplesPerConfig()} instead.
     * Must be ≥ 1. Default: 100.
     *
     * @return the number of samples
     */
    int samples() default 100;
    
    /**
     * Number of samples per ExperimentConfig (for multi-config experiments).
     *
     * <p>When {@link ExperimentDesign} is present, this is used instead of {@link #samples()}.
     * Must be ≥ 1. Default: 100.
     *
     * @return the number of samples per config
     */
    int samplesPerConfig() default 100;
    
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

