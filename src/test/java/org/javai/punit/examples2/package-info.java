/**
 * Teaching examples demonstrating PUnit's probabilistic testing capabilities.
 *
 * <p>This package exists to <b>teach by example</b>. Each class is carefully crafted
 * to illustrate a specific PUnit feature or testing pattern. The examples progress
 * from simple to advanced, building understanding incrementally.
 *
 * <h2>Learning Path</h2>
 * <p>We recommend exploring the examples in this order:
 *
 * <h3>1. Understanding Use Cases</h3>
 * <p>Start with the {@code usecases} package to understand what we're testing:
 * <ul>
 *   <li>{@link org.javai.punit.examples2.usecases.ShoppingBasketUseCase} - LLM-based
 *       natural language to JSON translation</li>
 *   <li>{@link org.javai.punit.examples2.usecases.PaymentGatewayUseCase} - Non-deterministic
 *       external service with known SLA</li>
 * </ul>
 *
 * <h3>2. Running Experiments</h3>
 * <p>The {@code experiments} package shows how to gather empirical data:
 * <ul>
 *   <li><b>Explore</b> - Compare factor configurations</li>
 *   <li><b>Optimize</b> - Search for optimal parameters</li>
 *   <li><b>Measure</b> - Establish baseline success rates</li>
 * </ul>
 *
 * <h3>3. Writing Tests</h3>
 * <p>The {@code tests} package demonstrates probabilistic testing patterns:
 * <ul>
 *   <li>Threshold approaches (sample-first, confidence-first, threshold-first)</li>
 *   <li>Covariate-aware baselines</li>
 *   <li>Budget management</li>
 *   <li>Pacing for rate-limited APIs</li>
 *   <li>Exception handling strategies</li>
 * </ul>
 *
 * <h2>Running the Examples</h2>
 * <p>All examples are {@code @Disabled} by default to prevent accidental execution
 * in CI.
 *
 * <h3>Running Experiments</h3>
 * <p>Use the dedicated {@code exp} task for experiments:
 * <pre>{@code
 * ./gradlew exp -Prun=ShoppingBasketMeasure
 * ./gradlew exp -Prun=ShoppingBasketExplore.exploreTemperature
 * }</pre>
 *
 * <h3>Running Tests</h3>
 * <p>Use the standard test task for probabilistic tests:
 * <pre>{@code
 * ./gradlew test --tests "ShoppingBasketTest"
 * }</pre>
 *
 * <h2>Package Structure</h2>
 * <ul>
 *   <li>{@code usecases/} - Domain use cases encapsulating non-deterministic behavior</li>
 *   <li>{@code experiments/} - MEASURE, EXPLORE, and OPTIMIZE experiment examples</li>
 *   <li>{@code tests/} - Probabilistic test examples for various scenarios</li>
 *   <li>{@code infrastructure/} - Mock implementations supporting the examples</li>
 * </ul>
 *
 * @see org.javai.punit.api.ProbabilisticTest
 * @see org.javai.punit.api.MeasureExperiment
 * @see org.javai.punit.api.ExploreExperiment
 * @see org.javai.punit.api.OptimizeExperiment
 */
package org.javai.punit.examples2;
