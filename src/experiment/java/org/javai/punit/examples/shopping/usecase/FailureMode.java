package org.javai.punit.examples.shopping.usecase;

/**
 * Categories of LLM response failures.
 *
 * <p>Real LLMs fail in varied and interesting ways. This enum categorizes
 * the main failure modes to enable realistic simulation in mock implementations.
 *
 * <h2>Failure Mode Distribution</h2>
 * <p>A realistic LLM might exhibit the following failure distribution:
 * <ul>
 *   <li>{@link #MALFORMED_JSON} - ~2% (syntax errors, unclosed braces)</li>
 *   <li>{@link #HALLUCINATED_FIELDS} - ~1.5% (wrong/misspelled field names)</li>
 *   <li>{@link #INVALID_VALUES} - ~1% (wrong types, nonsensical values)</li>
 *   <li>{@link #MISSING_FIELDS} - ~0.5% (required fields absent)</li>
 *   <li>{@link #NONE} - ~95% (valid response)</li>
 * </ul>
 *
 * @see MockShoppingAssistant
 */
public enum FailureMode {
    
    /**
     * Syntactically invalid JSON.
     *
     * <p>Examples:
     * <ul>
     *   <li>Unclosed braces: {@code { "products": [}</li>
     *   <li>Missing quotes: {@code { products: [] }}</li>
     *   <li>Missing value: {@code { "products": [], "query": }}</li>
     *   <li>Complete garbage: {@code not json at all}</li>
     * </ul>
     */
    MALFORMED_JSON,
    
    /**
     * Valid JSON but with unexpected or misspelled field names.
     *
     * <p>The LLM "hallucinates" field names that don't match the expected schema.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "prodcuts"} instead of {@code "products"}</li>
     *   <li>{@code "items"} instead of {@code "products"}</li>
     *   <li>{@code "search_query"} instead of {@code "query"}</li>
     *   <li>{@code "count"} instead of {@code "totalResults"}</li>
     * </ul>
     */
    HALLUCINATED_FIELDS,
    
    /**
     * Valid JSON structure but field values are wrong type or nonsensical.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "price": "expensive"} instead of {@code "price": 29.99}</li>
     *   <li>{@code "name": 12345} instead of {@code "name": "Product Name"}</li>
     *   <li>{@code "totalResults": "five"} instead of {@code "totalResults": 5}</li>
     *   <li>{@code "relevanceScore": "high"} instead of {@code "relevanceScore": 0.95}</li>
     * </ul>
     */
    INVALID_VALUES,
    
    /**
     * Required fields are missing entirely from the response.
     *
     * <p>The JSON is syntactically valid but lacks expected fields.
     *
     * <p>Examples:
     * <ul>
     *   <li>Missing {@code "products"} array</li>
     *   <li>Missing {@code "query"} echo</li>
     *   <li>Missing {@code "totalResults"} count</li>
     * </ul>
     */
    MISSING_FIELDS,
    
    /**
     * No failure - response is valid and well-formed.
     */
    NONE;
    
    /**
     * Returns true if this mode represents an actual failure.
     *
     * @return true if this is a failure mode, false if {@link #NONE}
     */
    public boolean isFailure() {
        return this != NONE;
    }
    
    /**
     * Returns a human-readable description of this failure mode.
     *
     * @return description suitable for logging or error messages
     */
    public String description() {
        return switch (this) {
            case MALFORMED_JSON -> "Malformed JSON (syntax error)";
            case HALLUCINATED_FIELDS -> "Hallucinated field names";
            case INVALID_VALUES -> "Invalid field values (wrong types)";
            case MISSING_FIELDS -> "Missing required fields";
            case NONE -> "Valid response";
        };
    }
}

