package org.javai.punit.api;

import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;

/**
 * The contract that use case classes implement to define their identity and success criteria.
 *
 * <p>Implementing this interface provides two key benefits:
 * <ul>
 *   <li><b>IDE-friendly</b>: Methods like {@link #criteria(UseCaseResult)} are recognized as
 *       interface implementations, not flagged as "unused" by static analysis</li>
 *   <li><b>Self-documenting</b>: The interface documents what methods a use case can provide,
 *       with sensible defaults</li>
 * </ul>
 *
 * <h2>Default Behavior</h2>
 * <ul>
 *   <li>{@link #useCaseId()}: Returns the simple class name (e.g., "ShoppingUseCase")</li>
 *   <li>{@link #criteria(UseCaseResult)}: Returns trivial (empty) criteria that always passes</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class ShoppingUseCase implements UseCaseContract {
 *     
 *     // Accept default ID ("ShoppingUseCase")
 *     // Or override:
 *     @Override
 *     public String useCaseId() {
 *         return "shopping.search";
 *     }
 *     
 *     // Accept trivial criteria
 *     // Or override:
 *     @Override
 *     public UseCaseCriteria criteria(UseCaseResult result) {
 *         return UseCaseCriteria.ordered()
 *             .criterion("Valid JSON", () -> result.getBoolean("isValidJson", false))
 *             .criterion("Has products", () -> result.getInt("productCount", 0) > 0)
 *             .build();
 *     }
 *     
 *     public UseCaseOutcome searchProducts(String query) {
 *         // ... implementation
 *     }
 * }
 * }</pre>
 *
 * <h2>Design by Contract</h2>
 * <p>The {@link #criteria(UseCaseResult)} method defines <em>postconditions</em> for the use case.
 * The default implementation returns the trivial postcondition (empty criteria), which is the
 * DbC-correct default when explicit postconditions haven't been declared.
 *
 * <h2>Relationship to @UseCase Annotation</h2>
 * <p>The {@link UseCase @UseCase} annotation can still be used as an optional marker and for
 * additional configuration (e.g., diffable content settings). However, implementing this interface
 * is the recommended approach for defining use case identity and criteria.
 *
 * @see UseCaseCriteria
 * @see UseCase
 */
public interface UseCaseContract {

    /**
     * Returns the unique identifier for this use case.
     *
     * <p>The ID is used for:
     * <ul>
     *   <li>Spec file naming: {@code specs/{useCaseId}.yaml}</li>
     *   <li>Baseline file location: {@code baselines/{useCaseId}.yaml}</li>
     *   <li>Linking experiments to tests</li>
     *   <li>Logging and diagnostics</li>
     * </ul>
     *
     * <p>Default implementation returns the simple class name.
     *
     * <h3>Customization</h3>
     * <p>Override this method to provide a custom ID:
     * <pre>{@code
     * @Override
     * public String useCaseId() {
     *     return "shopping.product.search";
     * }
     * }</pre>
     *
     * <p>Best practices for custom IDs:
     * <ul>
     *   <li>Use lowercase with dots as separators</li>
     *   <li>Be descriptive but concise</li>
     *   <li>Avoid special characters invalid in file paths</li>
     * </ul>
     *
     * @return the use case identifier
     */
    default String useCaseId() {
        return getClass().getSimpleName();
    }

    /**
     * Returns success criteria for evaluating a use case result.
     *
     * <p>Success criteria define <em>postconditions</em> that must be satisfied for a use case
     * invocation to be considered successful. They are used by:
     * <ul>
     *   <li><strong>Experiments</strong>: To track pass rates for each criterion</li>
     *   <li><strong>Probabilistic tests</strong>: To determine success/failure</li>
     * </ul>
     *
     * <h3>Default Implementation</h3>
     * <p>Returns the trivial postcondition (empty criteria that always passes). This is the
     * Design by Contract-correct default: if no postconditions are declared, the method
     * returning normally is sufficient.
     *
     * <h3>Customization</h3>
     * <p>Override this method to define meaningful success criteria:
     * <pre>{@code
     * @Override
     * public UseCaseCriteria criteria(UseCaseResult result) {
     *     return UseCaseCriteria.ordered()
     *         .criterion("Valid JSON", () -> result.getBoolean("isValidJson", false))
     *         .criterion("Has products", () -> result.getInt("productCount", 0) > 0)
     *         .build();
     * }
     * }</pre>
     *
     * <h3>Relationship to UseCaseOutcome</h3>
     * <p>This method provides shared criteria for all results from this use case. When methods
     * return {@link org.javai.punit.model.UseCaseOutcome UseCaseOutcome}, the bundled criteria
     * takes precedence for that specific invocation.
     *
     * @param result the use case result to evaluate
     * @return success criteria for the result
     */
    default UseCaseCriteria criteria(UseCaseResult result) {
        return UseCaseCriteria.defaultCriteria();
    }
}

