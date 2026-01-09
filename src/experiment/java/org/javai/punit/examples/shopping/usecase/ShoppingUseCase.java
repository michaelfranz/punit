package org.javai.punit.examples.shopping.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.javai.punit.api.UseCase;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;

/**
 * Use cases for the LLM-powered shopping assistant.
 *
 * <p>This class encapsulates the business logic for product search operations
 * powered by an LLM. It captures observations about response quality, format
 * compliance, and relevance for probabilistic testing.
 *
 * <h2>Use Case ID</h2>
 * <p>The {@code @UseCase} annotation provides the ID used for:
 * <ul>
 *   <li>Baseline file naming: {@code baselines/ShoppingUseCase.yaml}</li>
 *   <li>Spec file location: {@code specs/ShoppingUseCase/v1.yaml}</li>
 *   <li>Linking experiments and tests to specifications</li>
 * </ul>
 *
 * <h2>Dependency Injection</h2>
 * <p>Instances are created by {@link org.javai.punit.api.UseCaseProvider}.
 * Configure the provider in {@code @BeforeEach} to control which
 * {@link ShoppingAssistant} implementation is used (mock or real).
 *
 * @see org.javai.punit.api.UseCaseProvider
 */
@UseCase  // ID defaults to "ShoppingUseCase" (simple class name)
public class ShoppingUseCase {

    private final ShoppingAssistant assistant;

    public ShoppingUseCase(ShoppingAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Use case: Search for products matching a query.
     *
     * <p>Captures observations about:
     * <ul>
     *   <li>Whether the response is valid JSON</li>
     *   <li>Whether required fields are present (products, query, totalResults)</li>
     *   <li>Whether all products have required attributes (name, price, category)</li>
     *   <li>Token consumption</li>
     * </ul>
     *
     * @param query the search query
     * @param context the execution context
     * @return observations from the use case execution
     */
    /**
     * Search for products matching a query.
     */
    public UseCaseResult searchProducts(String query, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Observe response format
        boolean isValidJson = response.isValidJson();
        boolean hasProductsField = response.hasField("products");
        boolean hasQueryField = response.hasField("query");
        boolean hasTotalResultsField = response.hasField("totalResults");
        boolean hasAllRequiredFields = hasProductsField && hasQueryField && hasTotalResultsField;

        // Observe product quality
        List<Product> products = response.products();
        boolean hasProducts = !products.isEmpty();
        boolean allProductsHaveRequiredAttributes = hasProducts && products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);
        
        int productCount = products.size();

        // Capture failure mode for experiment statistics
        FailureMode failureMode = response.failureMode();

        // Overall success requires all quality checks to pass
        boolean success = isValidJson && hasAllRequiredFields && allProductsHaveRequiredAttributes;

        // Determine failure category:
        // 1. If failureMode indicates an LLM error, use that
        // 2. Otherwise, if products are missing or have bad attributes, that's a quality issue
        String failureCategory = null;
        if (!success) {
            if (failureMode.isFailure()) {
                failureCategory = failureMode.name();
            } else if (!hasProducts) {
                failureCategory = "EMPTY_RESPONSE";
            } else if (!allProductsHaveRequiredAttributes) {
                failureCategory = "PRODUCT_ATTRIBUTE_ERROR";
            } else if (!hasAllRequiredFields) {
                failureCategory = "MISSING_REQUIRED_FIELDS";
            } else {
                failureCategory = "VALIDATION_ERROR";
            }
        }

        return UseCaseResult.builder()
                .value("success", success)  // Primary success indicator for experiments
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("hasProductsField", hasProductsField)
                .value("hasQueryField", hasQueryField)
                .value("hasTotalResultsField", hasTotalResultsField)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("productCount", productCount)
                .value("tokensUsed", response.tokensUsed())
                .value("rawJson", response.rawJson())
                .value("failureCategory", failureCategory)  // For experiment statistics
                .value("failureMode", failureMode.name())   // Raw failure mode
                .meta("query", query)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products within a price range.
     *
     * <p>Captures observations about price constraint compliance in addition
     * to standard format and quality checks.
     *
     * @param query the search query
     * @param maxPrice the maximum price constraint
     * @param context the execution context
     * @return observations from the use case execution
     */
    /**
     * Search for products within a price constraint.
     */
    public UseCaseResult searchProductsWithPriceConstraint(String query, double maxPrice, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxPrice);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        // Price constraint compliance
        List<Product> products = response.products();
        boolean allProductsWithinPriceRange = products.stream()
                .allMatch(p -> p.price() != null && p.price() <= maxPrice);
        
        long productsExceedingPrice = products.stream()
                .filter(p -> p.price() != null && p.price() > maxPrice)
                .count();

        boolean allProductsHaveRequiredAttributes = products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);

        // Capture failure mode for experiment statistics
        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        // Overall success requires all quality checks to pass
        boolean success = isValidJson && hasAllRequiredFields 
                && allProductsHaveRequiredAttributes && allProductsWithinPriceRange;

        return UseCaseResult.builder()
                .value("success", success)  // Primary success indicator for experiments
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("allProductsWithinPriceRange", allProductsWithinPriceRange)
                .value("productsExceedingPrice", (int) productsExceedingPrice)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("productCount", products.size())
                .value("tokensUsed", response.tokensUsed())
                .value("failureCategory", failureCategory)
                .value("failureMode", failureMode.name())
                .meta("query", query)
                .meta("maxPrice", maxPrice)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products with a result count limit.
     *
     * <p>Captures observations about whether the assistant respects the
     * requested maximum number of results.
     *
     * @param query the search query
     * @param maxResults the maximum number of products to return
     * @param context the execution context
     * @return observations from the use case execution
     */
    /**
     * Search for products with a result count limit.
     */
    public UseCaseResult searchProductsWithLimit(String query, int maxResults, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxResults);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        // Result count compliance
        List<Product> products = response.products();
        boolean respectsResultLimit = products.size() <= maxResults;
        
        // Check if totalResults field matches actual count
        Integer reportedTotal = response.totalResults();
        boolean totalResultsMatchesActual = reportedTotal != null && reportedTotal == products.size();

        // Capture failure mode for experiment statistics
        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        // Overall success requires all quality checks to pass
        boolean success = isValidJson && hasAllRequiredFields && respectsResultLimit;

        return UseCaseResult.builder()
                .value("success", success)  // Primary success indicator for experiments
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("respectsResultLimit", respectsResultLimit)
                .value("totalResultsMatchesActual", totalResultsMatchesActual)
                .value("productCount", products.size())
                .value("requestedMaxResults", maxResults)
                .value("tokensUsed", response.tokensUsed())
                .value("failureCategory", failureCategory)
                .value("failureMode", failureMode.name())
                .meta("query", query)
                .meta("maxResults", maxResults)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }

    /**
     * Use case: Search for products and evaluate relevance.
     *
     * <p>Captures observations about product relevance scores.
     *
     * @param query the search query
     * @param minRelevanceScore the minimum acceptable relevance score
     * @param context the execution context
     * @return observations from the use case execution
     */
    /**
     * Search for products and evaluate relevance.
     */
    public UseCaseResult searchProductsWithRelevanceCheck(String query, double minRelevanceScore, UseCaseContext context) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        // Standard format observations
        boolean isValidJson = response.isValidJson();

        // Relevance observations
        List<Product> products = response.products();
        boolean allProductsRelevant = products.stream()
                .allMatch(p -> p.relevanceScore() != null && p.relevanceScore() >= minRelevanceScore);
        
        double averageRelevance = products.stream()
                .filter(p -> p.relevanceScore() != null)
                .mapToDouble(Product::relevanceScore)
                .average()
                .orElse(0.0);
        
        long lowRelevanceCount = products.stream()
                .filter(p -> p.relevanceScore() != null && p.relevanceScore() < minRelevanceScore)
                .count();

        // Capture failure mode for experiment statistics
        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        // Overall success requires valid JSON and all products meeting relevance threshold
        boolean success = isValidJson && allProductsRelevant;

        return UseCaseResult.builder()
                .value("success", success)  // Primary success indicator for experiments
                .value("isValidJson", isValidJson)
                .value("allProductsRelevant", allProductsRelevant)
                .value("averageRelevance", averageRelevance)
                .value("lowRelevanceCount", (int) lowRelevanceCount)
                .value("productCount", products.size())
                .value("tokensUsed", response.tokensUsed())
                .value("failureCategory", failureCategory)
                .value("failureMode", failureMode.name())
                .meta("query", query)
                .meta("minRelevanceScore", minRelevanceScore)
                .meta("backend", context.getBackend())
                .executionTime(executionTime)
                .build();
    }
}
