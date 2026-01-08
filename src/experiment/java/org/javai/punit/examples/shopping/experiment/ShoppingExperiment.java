package org.javai.punit.examples.shopping.experiment;

import java.util.Random;

import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentContext;

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
 * <h2>Usage</h2>
 * <p>Experiments use JUnit's {@code @TestTemplate} mechanism under the hood.
 * Run them using the {@code experimentTests} Gradle task:
 * <pre>{@code
 * ./gradlew experimentTests --tests "ShoppingExperiment"
 * }</pre>
 *
 * <p>Or run a specific experiment method:
 * <pre>{@code
 * ./gradlew experimentTests --tests "ShoppingExperiment.measureBasicSearchReliability"
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>Each experiment generates a baseline file in:
 * <pre>
 * src/test/resources/punit/baselines/
 * </pre>
 *
 * <p>These baselines can then be used to create execution specifications
 * for probabilistic conformance tests.
 *
 * <h2>Implementation Note</h2>
 * <p>The {@code experimentTests} task is a standard JUnit {@code Test} task
 * configured for the {@code src/experiment/java} source set. This provides
 * full IDE integration, debugging support, and familiar Gradle test filtering.
 *
 * @see org.javai.punit.examples.shopping.usecase.ShoppingUseCase
 */
public class ShoppingExperiment extends ShoppingUseCase {

    /**
     * Creates a ShoppingExperiment with a mock shopping assistant.
     *
     * <p>Uses the {@code experimentRealistic()} configuration which simulates
     * realistic LLM behavior with ~95% success rate and varied failure modes:
     * <ul>
     *   <li>2.0% - Malformed JSON (syntax errors)</li>
     *   <li>1.5% - Hallucinated field names</li>
     *   <li>1.0% - Invalid field values (wrong types)</li>
     *   <li>0.5% - Missing required fields</li>
     * </ul>
     */
    public ShoppingExperiment() {
        super(new MockShoppingAssistant(
                new Random(),
                MockShoppingAssistant.MockConfiguration.experimentRealistic()
        ));
    }

    // ========== Primary Experiment (1000 samples) ==========

    /**
     * Experiment: Establish realistic baseline for basic product search.
     *
     * <p>This is the primary experiment for generating a statistically reliable
     * baseline. It runs 1000 samples with the {@code experimentRealistic()}
     * configuration (~95% success rate, varied failure modes).
     *
     * <h2>Purpose</h2>
     * <p>Generate an empirical baseline that can be approved and used for
     * spec-driven probabilistic tests. The large sample size ensures:
     * <ul>
     *   <li>Statistically reliable success rate estimate</li>
     *   <li>Narrow confidence interval (~±1.3%)</li>
     *   <li>Representative failure mode distribution</li>
     * </ul>
     *
     * <h2>Expected Outcome</h2>
     * <ul>
     *   <li>Success rate: ~95% (±2%)</li>
     *   <li>Failure distribution: ~40% malformed JSON, ~30% hallucinated fields,
     *       ~20% invalid values, ~10% missing fields</li>
     * </ul>
     *
     * <h2>Usage</h2>
     * <pre>{@code
     * ./gradlew experimentTests --tests "ShoppingExperiment.measureRealisticSearchBaseline"
     * }</pre>
     *
     * @see MockShoppingAssistant.MockConfiguration#experimentRealistic()
     */
    @Experiment(
            useCase = "usecase.shopping.search",
            samples = 1000,
            tokenBudget = 500000,
            timeBudgetMs = 600000,
            experimentId = "shopping-search-realistic-v1"
    )
    @ExperimentContext(
            backend = "mock",
            parameters = {
                    "query = wireless headphones"
            }
    )
    void measureRealisticSearchBaseline() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Legacy Experiments (smaller samples) ==========

    /**
     * Experiment: Measure basic product search reliability (legacy, 100 samples).
     *
     * <p>Gathers empirical data about:
     * <ul>
     *   <li>JSON validity rate</li>
     *   <li>Required field presence rate</li>
     *   <li>Product attribute completeness</li>
     *   <li>Token consumption per query</li>
     * </ul>
     *
     * <p>Expected outcome: ~95% success rate for valid JSON with all required fields.
     *
     * @deprecated Use {@link #measureRealisticSearchBaseline()} for statistically
     *             reliable baselines (1000 samples).
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = wireless headphones"
        }
    )
    void measureBasicSearchReliability() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure search reliability with high-reliability configuration.
     *
     * <p>Uses a more reliable mock configuration to establish an upper bound
     * on expected success rates.
     *
     * @deprecated Legacy experiment. Use {@link #measureRealisticSearchBaseline()}.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-high-reliability"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = laptop bag"
        }
    )
    void measureSearchWithHighReliability() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Price Constraint Experiments ==========

    /**
     * Experiment: Measure price constraint compliance.
     *
     * <p>Gathers empirical data about how well the assistant respects
     * maximum price filters. Key metrics:
     * <ul>
     *   <li>Rate of responses with all products within price range</li>
     *   <li>Average number of price violations per response</li>
     *   <li>Correlation between price limit and violation rate</li>
     * </ul>
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search.price-constrained",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-price-constraint-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = gift ideas",
            "maxPrice = 50.00"
        }
    )
    void measurePriceConstraintCompliance() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure price constraint with tight budget.
     *
     * <p>Tests behavior with a very low price limit to understand
     * edge case handling.
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search.price-constrained",
        samples = 50,
        tokenBudget = 25000,
        timeBudgetMs = 60000,
        experimentId = "shopping-price-constraint-tight"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = budget accessories",
            "maxPrice = 20.00"
        }
    )
    void measureTightPriceConstraint() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Result Limit Experiments ==========

    /**
     * Experiment: Measure result count limit compliance.
     *
     * <p>Gathers empirical data about how well the assistant respects
     * the requested maximum number of results. Key metrics:
     * <ul>
     *   <li>Rate of responses respecting the limit</li>
     *   <li>Consistency between totalResults field and actual count</li>
     * </ul>
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search.limited-results",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-result-limit-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = coffee makers",
            "maxResults = 5"
        }
    )
    void measureResultLimitCompliance() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Relevance Experiments ==========

    /**
     * Experiment: Measure product relevance quality.
     *
     * <p>Gathers empirical data about the relevance of returned products.
     * Key metrics:
     * <ul>
     *   <li>Rate of responses where all products meet minimum relevance</li>
     *   <li>Average relevance score across all responses</li>
     *   <li>Distribution of low-relevance product counts</li>
     * </ul>
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search.relevance",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-relevance-baseline"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = bluetooth speaker waterproof",
            "minRelevanceScore = 0.7"
        }
    )
    void measureProductRelevance() {
        // Method body is optional—execution is driven by the use case
    }

    /**
     * Experiment: Measure relevance with stricter threshold.
     *
     * <p>Uses a higher minimum relevance score to understand
     * the upper bound of relevance quality.
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search.relevance",
        samples = 50,
        tokenBudget = 25000,
        timeBudgetMs = 60000,
        experimentId = "shopping-relevance-strict"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = premium headphones noise cancelling",
            "minRelevanceScore = 0.85"
        }
    )
    void measureStrictRelevanceThreshold() {
        // Method body is optional—execution is driven by the use case
    }

    // ========== Comparative Experiments ==========

    /**
     * Experiment: Compare reliability across different mock configurations.
     *
     * <p>Uses low reliability configuration to establish a lower bound
     * and understand degraded behavior patterns.
     *
     * @deprecated Legacy experiment with smaller sample size.
     */
    @Deprecated
    @Experiment(
        useCase = "usecase.shopping.search",
        samples = 100,
        tokenBudget = 50000,
        timeBudgetMs = 120000,
        experimentId = "shopping-search-low-reliability"
    )
    @ExperimentContext(
        backend = "mock",
        parameters = {
            "query = electronics sale"
        }
    )
    void measureSearchWithLowReliability() {
        // Method body is optional—execution is driven by the use case
    }
}
