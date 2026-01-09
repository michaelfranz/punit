package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Spec-driven probabilistic tests for the shopping assistant.
 *
 * <p>These tests demonstrate the complete PUnit workflow:
 * <ol>
 *   <li>Experiments run with 1000 samples to establish baseline</li>
 *   <li>Baselines are approved to create specifications</li>
 *   <li>Tests reference use case class; spec is looked up automatically</li>
 * </ol>
 *
 * <h2>Architecture</h2>
 * <p>This class demonstrates the recommended pattern for PUnit tests:
 * <ul>
 *   <li>Use {@link UseCaseProvider} to configure use case construction</li>
 *   <li>Reference use case by class: {@code @ProbabilisticTest(useCase = ShoppingUseCase.class)}</li>
 *   <li>Receive use case via method parameter injection</li>
 *   <li>Spec is automatically looked up based on use case ID</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Run: ./gradlew experiment --tests "ShoppingExperiment.measureRealisticSearchBaseline"
 * 2. Promote: ./gradlew punitPromote
 * 3. Review: punit/pending-approval/ShoppingUseCase.yaml
 * 4. Approve: ./gradlew punitApprove
 * 5. Test: ./gradlew test --tests "ShoppingAssistantSpecExamplesTest"
 * </pre>
 *
 * <h2>Spec Location</h2>
 * <p>The spec is automatically looked up at:
 * {@code src/test/resources/punit/specs/ShoppingUseCase/v1.yaml}
 *
 * @see org.javai.punit.examples.shopping.experiment.ShoppingExperiment
 * @see ShoppingUseCase
 * @see UseCaseProvider
 */
@ProbabilisticTestBudget(
    tokenBudget = 15000,
    timeBudgetMs = 30000,
    onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
@DisplayName("Shopping Assistant Spec-Driven Tests")
class ShoppingAssistantSpecExamplesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE PROVIDER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The use case provider handles construction and injection of use cases.
     */
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    /**
     * Shared context for all tests.
     */
    private UseCaseContext context;

    /**
     * Configures the use case provider before each test sample.
     *
     * <p>Uses the same mock configuration as the experiment to ensure
     * consistency between baseline and test behavior.
     */
    @BeforeEach
    void setUp() {
        // Configure how ShoppingUseCase instances are created
        // Use the same configuration as the experiment for consistency
        provider.register(ShoppingUseCase.class, () ->
            new ShoppingUseCase(
                new MockShoppingAssistant(
                    new Random(),
                    MockShoppingAssistant.MockConfiguration.experimentRealistic()
                )
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
     * <p>This test demonstrates the recommended pattern:
     * <ul>
     *   <li>{@code useCase = ShoppingUseCase.class} - type-safe reference</li>
     *   <li>Spec is automatically looked up at: {@code specs/ShoppingUseCase/v1.yaml}</li>
     *   <li>Threshold ({@code minPassRate}) comes from the spec</li>
     *   <li>Use case is injected by the provider</li>
     * </ul>
     *
     * <h3>Statistical Basis</h3>
     * <p>The threshold in the spec comes from the 95% confidence interval
     * lower bound of the 1000-sample baseline experiment.
     *
     * @param useCase the shopping use case (injected by {@link UseCaseProvider})
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        maxExampleFailures = 5
        // minPassRate is derived from spec (specs/ShoppingUseCase/v1.yaml)
    )
    @DisplayName("Should return valid JSON (spec-driven)")
    void shouldReturnValidJson(ShoppingUseCase useCase, TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("wireless headphones", context);

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    /**
     * Tests JSON validity with explicit inline threshold (no spec lookup).
     *
     * <p>This demonstrates the fallback mode when you want to specify
     * the threshold explicitly rather than deriving it from a spec.
     *
     * @param useCase the shopping use case
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        minPassRate = 0.95,  // Explicit threshold (no spec lookup)
        maxExampleFailures = 3
    )
    @DisplayName("Should return valid JSON (explicit threshold)")
    void shouldReturnValidJsonExplicit(ShoppingUseCase useCase, TokenChargeRecorder tokenRecorder) {
        UseCaseResult result = useCase.searchProducts("laptop accessories", context);

        tokenRecorder.recordTokens(result.getInt("tokensUsed", 0));

        assertThat(result.getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }
}
