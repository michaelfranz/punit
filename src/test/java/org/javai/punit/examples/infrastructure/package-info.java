/**
 * Mock infrastructure supporting the example use cases.
 *
 * <p>This package provides <b>simulated external services</b> that exhibit realistic
 * non-deterministic behavior. These mocks allow the examples to run without actual
 * API credentials while still demonstrating meaningful probabilistic testing patterns.
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@code llm/} - Mock LLM client with configurable success rates and failure modes</li>
 *   <li>{@code payment/} - Mock payment gateway simulating real-world reliability patterns</li>
 * </ul>
 *
 * <h2>Design Philosophy</h2>
 * <p>The mocks are designed to be <b>pedagogically useful</b>:
 * <ul>
 *   <li>They produce varied, realistic outputs (not just "success" or "failure")</li>
 *   <li>Failure modes mirror real-world issues (malformed JSON, timeouts, etc.)</li>
 *   <li>Success rates are configurable to demonstrate different testing scenarios</li>
 * </ul>
 *
 * <p>In production code, you would replace these mocks with real service clients.
 * The use case abstraction ensures the tests remain unchanged.
 */
package org.javai.punit.examples.infrastructure;
