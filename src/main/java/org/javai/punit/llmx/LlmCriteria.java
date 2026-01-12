package org.javai.punit.llmx;

import java.util.Objects;
import java.util.function.Supplier;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;
import org.javai.punit.util.Lazy;

/**
 * Common success criteria helpers for LLM-based use cases.
 *
 * <p>This class provides factory methods and builders for creating
 * success criteria that are commonly needed when evaluating LLM responses.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * public UseCaseOutcome search(String query) {
 *     LlmResponse response = llm.complete(buildPrompt(query));
 *     
 *     UseCaseResult result = UseCaseResult.builder()
 *         .value("response", response.content())
 *         .value("tokensUsed", response.tokens())
 *         .build();
 *     
 *     UseCaseCriteria criteria = LlmCriteria.forResult(result)
 *         .jsonParseable()
 *         .hasRequiredFields("products", "totalCount")
 *         .noHallucination(this::detectHallucination)
 *         .build();
 *     
 *     return new UseCaseOutcome(result, criteria);
 * }
 * }</pre>
 *
 * <h2>Lazy Evaluation</h2>
 * <p>All criteria are evaluated lazily. Expensive operations like JSON parsing
 * are deferred until the criterion is actually evaluated.
 *
 * @see UseCaseCriteria
 */
public final class LlmCriteria {

    private LlmCriteria() {}

    /**
     * Creates a builder for LLM criteria based on a use case result.
     *
     * @param result the use case result to evaluate
     * @return a new builder
     */
    public static Builder forResult(UseCaseResult result) {
        return new Builder(result);
    }

    /**
     * Creates a criterion that checks if content is non-empty.
     *
     * @param content the content to check
     * @return a supplier that returns true if content is non-empty
     */
    public static Supplier<Boolean> hasContent(String content) {
        return () -> content != null && !content.isBlank();
    }

    /**
     * Creates a criterion that checks if content parses as valid JSON.
     *
     * @param content the content to parse
     * @return a supplier that returns true if content is valid JSON
     */
    public static Supplier<Boolean> isValidJson(String content) {
        return () -> {
            if (content == null || content.isBlank()) {
                return false;
            }
            return JsonValidator.isValid(content);
        };
    }

    /**
     * Creates a criterion that checks if JSON contains all required fields.
     *
     * @param content the JSON content
     * @param fields the required field names
     * @return a supplier that returns true if all fields are present
     */
    public static Supplier<Boolean> hasJsonFields(String content, String... fields) {
        return () -> {
            if (content == null || content.isBlank()) {
                return false;
            }
            return JsonValidator.hasFields(content, fields);
        };
    }

    /**
     * Builder for constructing LLM success criteria.
     */
    public static class Builder {
        private final UseCaseResult result;
        private final UseCaseCriteria.Builder criteriaBuilder;
        private Lazy<String> content;
        private Lazy<Object> parsedJson;

        Builder(UseCaseResult result) {
            this.result = Objects.requireNonNull(result, "result must not be null");
            this.criteriaBuilder = UseCaseCriteria.ordered();
            this.content = Lazy.of(() -> extractContent(result));
            this.parsedJson = Lazy.of(() -> JsonValidator.parse(content.get()));
        }

        private String extractContent(UseCaseResult result) {
            // Try common content keys
            String c = result.getString(LlmResultValues.CONTENT, null);
            if (c == null) {
                c = result.getString(LlmResultValues.RESPONSE, null);
            }
            return c != null ? c : "";
        }

        /**
         * Adds a criterion that checks if the response has content.
         *
         * @return this builder
         */
        public Builder hasContent() {
            criteriaBuilder.criterion("Has content", () -> !content.get().isBlank());
            return this;
        }

        /**
         * Adds a criterion that checks if the response parses as valid JSON.
         *
         * @return this builder
         */
        public Builder jsonParseable() {
            criteriaBuilder.criterion("JSON parseable", () -> parsedJson.get() != null);
            return this;
        }

        /**
         * Adds a criterion that checks if the JSON has all required fields.
         *
         * @param fields the required field names
         * @return this builder
         */
        public Builder hasRequiredFields(String... fields) {
            criteriaBuilder.criterion("Has required fields", 
                () -> JsonValidator.hasFields(content.get(), fields));
            return this;
        }

        /**
         * Adds a criterion that checks if a specific field exists and is non-null.
         *
         * @param fieldName the field name to check
         * @return this builder
         */
        public Builder hasField(String fieldName) {
            criteriaBuilder.criterion("Has field: " + fieldName,
                () -> JsonValidator.hasFields(content.get(), fieldName));
            return this;
        }

        /**
         * Adds a criterion that uses a custom hallucination detector.
         *
         * <p>The detector receives the parsed JSON and returns true if
         * hallucination is detected (criterion fails if true).
         *
         * @param detector the hallucination detector
         * @return this builder
         */
        public Builder noHallucination(HallucinationDetector detector) {
            criteriaBuilder.criterion("No hallucination",
                () -> !detector.detectHallucination(parsedJson.get()));
            return this;
        }

        /**
         * Adds a custom criterion.
         *
         * @param description the criterion description
         * @param check the check to perform
         * @return this builder
         */
        public Builder criterion(String description, Supplier<Boolean> check) {
            criteriaBuilder.criterion(description, check);
            return this;
        }

        /**
         * Adds a criterion that checks if token count is within limits.
         *
         * @param maxTokens the maximum allowed tokens
         * @return this builder
         */
        public Builder withinTokenLimit(int maxTokens) {
            criteriaBuilder.criterion("Within token limit",
                () -> result.getLong(LlmResultValues.TOKENS_USED, 0) <= maxTokens);
            return this;
        }

        /**
         * Adds a criterion that checks if latency is within limits.
         *
         * @param maxLatencyMs the maximum allowed latency in milliseconds
         * @return this builder
         */
        public Builder withinLatencyLimit(long maxLatencyMs) {
            criteriaBuilder.criterion("Within latency limit",
                () -> result.getLong(LlmResultValues.LATENCY_MS, 0) <= maxLatencyMs);
            return this;
        }

        /**
         * Adds a criterion that checks if the response contains no errors.
         *
         * @return this builder
         */
        public Builder noErrors() {
            criteriaBuilder.criterion("No errors", 
                () -> !result.hasValue(LlmResultValues.ERROR_TYPE) &&
                      !result.hasValue(LlmResultValues.ERROR_MESSAGE));
            return this;
        }

        /**
         * Adds a criterion that checks if rate limiting was not hit.
         *
         * @return this builder
         */
        public Builder notRateLimited() {
            criteriaBuilder.criterion("Not rate limited",
                () -> !result.getBoolean(LlmResultValues.RATE_LIMITED, false));
            return this;
        }

        /**
         * Builds the success criteria.
         *
         * @return the built criteria
         */
        public UseCaseCriteria build() {
            return criteriaBuilder.build();
        }
    }

    /**
     * Functional interface for hallucination detection.
     */
    @FunctionalInterface
    public interface HallucinationDetector {
        /**
         * Detects if the parsed JSON contains hallucinated content.
         *
         * @param parsedJson the parsed JSON object
         * @return true if hallucination is detected
         */
        boolean detectHallucination(Object parsedJson);
    }
}
