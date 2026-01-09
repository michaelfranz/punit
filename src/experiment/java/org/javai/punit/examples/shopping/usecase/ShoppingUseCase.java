package org.javai.punit.examples.shopping.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.UseCase;
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
 * <h2>Configuration</h2>
 * <p>Configuration (model, temperature, etc.) is provided at construction time,
 * not at each method call. This follows the principle that the use case instance
 * encapsulates its complete configuration.
 *
 * <h2>Dependency Injection</h2>
 * <p>Instances are created by {@link org.javai.punit.api.UseCaseProvider}.
 * In EXPLORE mode, the provider creates differently-configured instances
 * for each factor combination.
 *
 * @see org.javai.punit.api.UseCaseProvider
 */
@UseCase  // ID defaults to "ShoppingUseCase" (simple class name)
public class ShoppingUseCase {

    private ShoppingAssistant assistant;
    private String model = "default";
    private double temperature = 0.0;

    /**
     * Creates a use case with the given assistant and default configuration.
     */
    public ShoppingUseCase(ShoppingAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Creates a use case with explicit configuration.
     *
     * <p>In EXPLORE mode, the UseCaseProvider creates instances with different
     * model/temperature combinations to compare configurations.
     *
     * @param assistant the shopping assistant implementation
     * @param model the model name (for metadata in results)
     * @param temperature the temperature setting (for metadata in results)
     */
    public ShoppingUseCase(ShoppingAssistant assistant, String model, double temperature) {
        this.assistant = assistant;
        this.model = model;
        this.temperature = temperature;
    }

    /**
     * Returns the model name for this use case configuration.
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the temperature setting for this use case configuration.
     */
    public double getTemperature() {
        return temperature;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR PARAMETER SETTERS - For auto-wired factor injection
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets the model and reconfigures the assistant accordingly.
     *
     * <p>Called automatically by {@link org.javai.punit.api.UseCaseProvider}
     * when using {@code registerAutoWired} in EXPLORE mode.
     *
     * @param model the model name (e.g., "gpt-4", "gpt-3.5-turbo")
     */
    @FactorSetter("model")
    public void setModel(String model) {
        this.model = model;
        reconfigureAssistant();
    }
    
    /**
     * Sets the temperature and reconfigures the assistant accordingly.
     *
     * <p>Called automatically by {@link org.javai.punit.api.UseCaseProvider}
     * when using {@code registerAutoWired} in EXPLORE mode.
     *
     * @param temperature the temperature setting (0.0 to 1.0)
     */
    @FactorSetter("temp")
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        reconfigureAssistant();
    }
    
    /**
     * Reconfigures the mock assistant based on current model/temperature.
     *
     * <p>Different configurations simulate different LLM reliability profiles.
     */
    private void reconfigureAssistant() {
        if (assistant instanceof MockShoppingAssistant mock) {
            MockShoppingAssistant.MockConfiguration config;
            if ("gpt-4".equals(model)) {
                config = temperature == 0.0 
                    ? MockShoppingAssistant.MockConfiguration.highReliability()
                    : MockShoppingAssistant.MockConfiguration.experimentRealistic();
            } else {
                config = temperature == 0.0
                    ? MockShoppingAssistant.MockConfiguration.experimentRealistic()
                    : MockShoppingAssistant.MockConfiguration.defaultConfig();
            }
            mock.setConfiguration(config);
        }
    }

    /**
     * Search for products matching a query.
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
     * @return observations from the use case execution
     */
    public UseCaseResult searchProducts(String query) {
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

        // Determine failure category
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
                .value("success", success)
                .value("isValidJson", isValidJson)
                .value("hasAllRequiredFields", hasAllRequiredFields)
                .value("hasProductsField", hasProductsField)
                .value("hasQueryField", hasQueryField)
                .value("hasTotalResultsField", hasTotalResultsField)
                .value("allProductsHaveRequiredAttributes", allProductsHaveRequiredAttributes)
                .value("productCount", productCount)
                .value("tokensUsed", response.tokensUsed())
                .value("rawJson", response.rawJson())
                .value("failureCategory", failureCategory)
                .value("failureMode", failureMode.name())
                .meta("query", query)
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();
    }

    /**
     * Search for products within a price constraint.
     *
     * @param query the search query
     * @param maxPrice the maximum price constraint
     * @return observations from the use case execution
     */
    public UseCaseResult searchProductsWithPriceConstraint(String query, double maxPrice) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxPrice);
        
        Duration executionTime = Duration.between(start, Instant.now());

        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        List<Product> products = response.products();
        boolean allProductsWithinPriceRange = products.stream()
                .allMatch(p -> p.price() != null && p.price() <= maxPrice);
        
        long productsExceedingPrice = products.stream()
                .filter(p -> p.price() != null && p.price() > maxPrice)
                .count();

        boolean allProductsHaveRequiredAttributes = products.stream()
                .allMatch(p -> p.name() != null && p.price() != null && p.category() != null);

        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        boolean success = isValidJson && hasAllRequiredFields 
                && allProductsHaveRequiredAttributes && allProductsWithinPriceRange;

        return UseCaseResult.builder()
                .value("success", success)
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
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();
    }

    /**
     * Search for products with a result count limit.
     *
     * @param query the search query
     * @param maxResults the maximum number of products to return
     * @return observations from the use case execution
     */
    public UseCaseResult searchProductsWithLimit(String query, int maxResults) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query, maxResults);
        
        Duration executionTime = Duration.between(start, Instant.now());

        boolean isValidJson = response.isValidJson();
        boolean hasAllRequiredFields = response.hasField("products") 
                && response.hasField("query") 
                && response.hasField("totalResults");

        List<Product> products = response.products();
        boolean respectsResultLimit = products.size() <= maxResults;
        
        Integer reportedTotal = response.totalResults();
        boolean totalResultsMatchesActual = reportedTotal != null && reportedTotal == products.size();

        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        boolean success = isValidJson && hasAllRequiredFields && respectsResultLimit;

        return UseCaseResult.builder()
                .value("success", success)
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
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();
    }

    /**
     * Search for products and evaluate relevance.
     *
     * @param query the search query
     * @param minRelevanceScore the minimum acceptable relevance score
     * @return observations from the use case execution
     */
    public UseCaseResult searchProductsWithRelevanceCheck(String query, double minRelevanceScore) {
        Instant start = Instant.now();
        
        LlmResponse response = assistant.searchProducts(query);
        
        Duration executionTime = Duration.between(start, Instant.now());

        boolean isValidJson = response.isValidJson();

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

        FailureMode failureMode = response.failureMode();
        String failureCategory = failureMode.isFailure() ? failureMode.name() : null;

        boolean success = isValidJson && allProductsRelevant;

        return UseCaseResult.builder()
                .value("success", success)
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
                .meta("model", model)
                .meta("temperature", temperature)
                .executionTime(executionTime)
                .build();
    }
}
