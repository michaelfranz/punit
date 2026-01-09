package org.javai.punit.examples.shopping.experiment;

import java.util.Random;

import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ResultCaptor;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Experiments for the LLM-powered shopping assistant.
 *
 * <h2>Purpose</h2>
 * <p>These experiments execute use cases repeatedly to gather empirical data about
 * the shopping assistant's behavior. The results are used to:
 * <ul>
 *   <li>Establish baseline success rates for different operations</li>
 *   <li>Understand failure mode distribution</li>
 *   <li>Measure token consumption patterns</li>
 *   <li>Inform appropriate pass rate thresholds for probabilistic tests</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>This class demonstrates the recommended pattern for PUnit experiments:
 * <ul>
 *   <li>Use {@link UseCaseProvider} to configure use case construction</li>
 *   <li>Reference use case by class: {@code @Experiment(useCase = ShoppingUseCase.class)}</li>
 *   <li>Receive use case via method parameter injection</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ./gradlew experiment --tests "ShoppingExperiment"
 * ./gradlew experiment --tests "ShoppingExperiment.measureRealisticSearchBaseline"
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>Baselines are written to {@code build/punit/baselines/}.
 * These can be approved via {@code ./gradlew punitApprove} to create
 * execution specifications in {@code src/test/resources/punit/specs/}.
 *
 * @see ShoppingUseCase
 * @see UseCaseProvider
 */
public class ShoppingExperiment {

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE PROVIDER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The use case provider handles construction and injection of use cases.
     *
     * <p>Registered as a JUnit extension so it can inject parameters into
     * experiment methods.
     */
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    /**
     * Shared context for all experiments.
     */
    private UseCaseContext context;

    /**
     * Configures the use case provider before each experiment sample.
     *
     * <p>This is where you configure which implementation to use:
     * <ul>
     *   <li>Mock with specific configuration for experiments</li>
     *   <li>Real implementation for integration tests</li>
     *   <li>Spring-injected beans in {@code @SpringBootTest}</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Configure how ShoppingUseCase instances are created
        provider.register(ShoppingUseCase.class, () ->
            new ShoppingUseCase(
                new MockShoppingAssistant(
                    new Random(),
                    MockShoppingAssistant.MockConfiguration.experimentRealistic()
                )
            )
        );

        // Shared context for experiments
        context = DefaultUseCaseContext.builder()
            .backend("mock")
            .parameter("configuration", "experimentRealistic")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY EXPERIMENT (1000 samples)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Experiment: Establish realistic baseline for basic product search.
     *
     * <p>This is the primary experiment for generating a statistically reliable
     * baseline. It runs 1000 samples with the {@code experimentRealistic()}
     * configuration (~95% success rate, varied failure modes).
     *
     * <h2>Mock Configuration</h2>
     * <ul>
     *   <li>2.0% - Malformed JSON (syntax errors)</li>
     *   <li>1.5% - Hallucinated field names</li>
     *   <li>1.0% - Invalid field values (wrong types)</li>
     *   <li>0.5% - Missing required fields</li>
     * </ul>
     *
     * <h2>Expected Outcome</h2>
     * <ul>
     *   <li>Success rate: ~95% (±2%)</li>
     *   <li>Confidence interval width: ~2.6%</li>
     * </ul>
     *
     * <h2>Usage</h2>
     * <pre>{@code
     * ./gradlew experiment --tests "ShoppingExperiment.measureRealisticSearchBaseline"
     * }</pre>
     *
     * @param useCase the shopping use case (injected by {@link UseCaseProvider})
     * @param captor the result captor (injected by ExperimentExtension)
     * @see MockShoppingAssistant.MockConfiguration#experimentRealistic()
     */
    @Experiment(
        useCase = ShoppingUseCase.class,
        samples = 1000,
        tokenBudget = 500000,
        timeBudgetMs = 600000,
        experimentId = "shopping-search-realistic-v1"
    )
    void measureRealisticSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
        // Execute the use case and record the result
        captor.record(useCase.searchProducts("wireless headphones", context));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY EXPERIMENTS (deprecated, kept for reference)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Experiment: Measure basic product search reliability (legacy, 100 samples).
     *
     * @param useCase the shopping use case
     * @param captor the result captor
     * @deprecated Use {@link #measureRealisticSearchBaseline(ShoppingUseCase, ResultCaptor)} for
     *             statistically reliable baselines (1000 samples).
     */
    @Experiment(
        useCase = ShoppingUseCase.class,
        samples = 1000,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-baseline"
    )
    void measureBasicSearchReliability(ShoppingUseCase useCase, ResultCaptor captor) {
        captor.record(useCase.searchProducts("wireless headphones", context));
    }
}
