package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

/**
 * Spec-driven probabilistic tests for the shopping assistant.
 *
 * <p>These tests demonstrate the complete PUnit workflow:
 * <ol>
 *   <li>Experiments run with 1000 samples to establish baseline</li>
 *   <li>Baselines are approved to create specifications</li>
 *   <li>Tests reference spec-derived thresholds with smaller sample counts</li>
 * </ol>
 *
 * <p>Unlike {@link ShoppingAssistantExamplesTest}, these tests derive their
 * pass/fail thresholds from approved specifications, not arbitrary values.
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Run: ./gradlew experiment --tests "ShoppingExperiment.measureRealisticSearchBaseline"
 * 2. Promote: ./gradlew punitPromote
 * 3. Review: punit/pending-approval/usecase-shopping-search.yaml
 * 4. Approve: ./gradlew punitApprove
 * 5. Test: ./gradlew test --tests "ShoppingAssistantSpecExamplesTest"
 * </pre>
 *
 * <h2>Threshold Derivation</h2>
 * <p>The thresholds in this test come from the approved specification at:
 * {@code src/test/resources/punit/specs/usecase-shopping-search-spec.yaml}
 *
 * <p>The spec was generated from a 1000-sample experiment with the following results:
 * <ul>
 *   <li>Observed success rate: 98.3% (983/1000)</li>
 *   <li>Standard error: 0.41%</li>
 *   <li>95% CI lower bound: 97.5%</li>
 * </ul>
 *
 * <p>The minimum threshold (97.5%) is derived from the 95% confidence interval
 * lower bound, ensuring we have high statistical confidence that the true
 * success rate is at least this value.
 *
 * @see org.javai.punit.examples.shopping.experiment.ShoppingExperiment
 */
@ProbabilisticTestBudget(
    tokenBudget = 15000,
    timeBudgetMs = 30000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
@DisplayName("Shopping Assistant Spec-Driven Tests")
class ShoppingAssistantSpecExamplesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // SPEC-DERIVED VALUES (from src/test/resources/punit/specs/usecase-shopping-search-spec.yaml)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Minimum success rate from spec's 95% CI lower bound.
     * 
     * <p>Source: thresholds.minSuccessRate from the approved specification.
     * Derived from 1000-sample experiment: 983 successes, 17 failures.
     */
    private static final double SPEC_MIN_SUCCESS_RATE = 0.975;
    
    /**
     * Recommended sample size from spec's tolerances.
     * 
     * <p>Source: tolerances.recommendedMinSamples from the approved specification.
     * Balances statistical validity with test execution cost.
     */
    private static final int SPEC_RECOMMENDED_SAMPLES = 30;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST SETUP
    // ═══════════════════════════════════════════════════════════════════════════

    private ShoppingUseCase useCase;
    private UseCaseContext context;

    @BeforeEach
    void setUp() {
        // Use the same mock configuration as the experiment to ensure consistency
        useCase = new ShoppingUseCase(
            new MockShoppingAssistant(
                new Random(),
                MockShoppingAssistant.MockConfiguration.experimentRealistic()
            )
        );
        context = DefaultUseCaseContext.builder()
            .backend("mock")
            .parameter("query", "wireless headphones")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPEC-DRIVEN TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests that responses are valid JSON, using spec-derived threshold.
     *
     * <p>This test uses the Threshold-First approach (Approach 3):
     * <ul>
     *   <li>Samples: 30 (from spec's recommendedMinSamples)</li>
     *   <li>minPassRate: 97.5% (from spec's 95% CI lower bound)</li>
     * </ul>
     *
     * <h3>Statistical Basis</h3>
     * <p>The 97.5% threshold comes from the 95% confidence interval lower bound
     * of the 1000-sample baseline experiment. This means:
     * <ul>
     *   <li>We are 95% confident the true success rate is at least 97.5%</li>
     *   <li>Tests may fail if the mock exhibits unexpected degradation</li>
     *   <li>Some sampling variance is expected with 30 samples</li>
     * </ul>
     *
     * <h3>Expected Behavior</h3>
     * <p>With the experimentRealistic() mock configuration:
     * <ul>
     *   <li>~98% of responses will be valid JSON</li>
     *   <li>~2% will have malformed JSON (syntax errors)</li>
     * </ul>
     *
     * <p>With 30 samples, we expect ~29-30 successes (96-100% observed rate),
     * which should pass the 97.5% threshold most of the time.
     */
    @ProbabilisticTest(
        samples = SPEC_RECOMMENDED_SAMPLES,
        minPassRate = SPEC_MIN_SUCCESS_RATE,
        maxExampleFailures = 5
    )
    @DisplayName("Should return valid JSON (spec: usecase.shopping.search)")
    void shouldReturnValidJson(TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("wireless headphones", context);

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    /**
     * Tests JSON validity with a slightly relaxed threshold for demonstration.
     *
     * <p>This test shows how to adjust the threshold based on operational needs.
     * A 95% threshold allows for slightly more variance while still catching
     * significant regressions.
     *
     * <p>Note: In production, you might use different thresholds for:
     * <ul>
     *   <li>PR checks: 95% (faster feedback, allow variance)</li>
     *   <li>Nightly builds: 97.5% (stricter validation)</li>
     *   <li>Release gates: 99% (highest confidence)</li>
     * </ul>
     */
    @ProbabilisticTest(
        samples = 30,
        minPassRate = 0.95,  // Relaxed for demonstration
        maxExampleFailures = 3
    )
    @DisplayName("Should return valid JSON (relaxed threshold)")
    void shouldReturnValidJsonRelaxed(TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("laptop accessories", context);

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }
}

