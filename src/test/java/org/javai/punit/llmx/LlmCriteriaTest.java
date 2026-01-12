package org.javai.punit.llmx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LlmCriteria")
class LlmCriteriaTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should check for content presence")
        void shouldCheckForContentPresence() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "Hello, world!")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .hasContent()
                .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should fail when content is empty")
        void shouldFailWhenContentIsEmpty() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .hasContent()
                .build();

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should validate JSON parseability")
        void shouldValidateJsonParseability() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"name\": \"test\", \"value\": 42}")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .jsonParseable()
                .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should fail for invalid JSON")
        void shouldFailForInvalidJson() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "not valid json")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .jsonParseable()
                .build();

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should check for required fields")
        void shouldCheckForRequiredFields() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"products\": [], \"totalCount\": 0}")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .hasRequiredFields("products", "totalCount")
                .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should fail when required field is missing")
        void shouldFailWhenRequiredFieldIsMissing() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"products\": []}")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .hasRequiredFields("products", "totalCount")
                .build();

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should use custom hallucination detector")
        void shouldUseCustomHallucinationDetector() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"valid\": true}")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .jsonParseable()
                .noHallucination(parsed -> false) // No hallucination detected
                .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should fail when hallucination detected")
        void shouldFailWhenHallucinationDetected() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"suspicious\": true}")
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .jsonParseable()
                .noHallucination(parsed -> true) // Hallucination detected
                .build();

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should check token limits")
        void shouldCheckTokenLimits() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "response")
                .value("tokensUsed", 100L)
                .build();

            UseCaseCriteria withinLimit = LlmCriteria.forResult(result)
                .withinTokenLimit(150)
                .build();

            UseCaseCriteria overLimit = LlmCriteria.forResult(result)
                .withinTokenLimit(50)
                .build();

            assertThat(withinLimit.allPassed()).isTrue();
            assertThat(overLimit.allPassed()).isFalse();
        }

        @Test
        @DisplayName("should check for errors")
        void shouldCheckForErrors() {
            UseCaseResult cleanResult = UseCaseResult.builder()
                .value("content", "success")
                .build();

            UseCaseResult errorResult = UseCaseResult.builder()
                .value("content", "error occurred")
                .value("errorType", "RateLimitError")
                .build();

            assertThat(LlmCriteria.forResult(cleanResult).noErrors().build().allPassed()).isTrue();
            assertThat(LlmCriteria.forResult(errorResult).noErrors().build().allPassed()).isFalse();
        }

        @Test
        @DisplayName("should support custom criteria")
        void shouldSupportCustomCriteria() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"score\": 0.95}")
                .value("qualityScore", 0.95)
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .criterion("High quality score", () -> 
                    result.getDouble("qualityScore", 0.0) >= 0.9)
                .build();

            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should chain multiple criteria")
        void shouldChainMultipleCriteria() {
            UseCaseResult result = UseCaseResult.builder()
                .value("content", "{\"products\": [{\"id\": 1}]}")
                .value("tokensUsed", 100L)
                .value("latencyMs", 200L)
                .build();

            UseCaseCriteria criteria = LlmCriteria.forResult(result)
                .hasContent()
                .jsonParseable()
                .hasField("products")
                .withinTokenLimit(500)
                .withinLatencyLimit(1000)
                .noErrors()
                .build();

            List<CriterionOutcome> outcomes = criteria.evaluate();
            assertThat(outcomes).hasSize(6);
            assertThat(criteria.allPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Static helpers")
    class StaticHelpers {

        @Test
        @DisplayName("hasContent should check for non-empty content")
        void hasContentShouldCheck() {
            assertThat(LlmCriteria.hasContent("hello").get()).isTrue();
            assertThat(LlmCriteria.hasContent("").get()).isFalse();
            assertThat(LlmCriteria.hasContent(null).get()).isFalse();
        }

        @Test
        @DisplayName("isValidJson should validate JSON")
        void isValidJsonShouldValidate() {
            assertThat(LlmCriteria.isValidJson("{\"key\": \"value\"}").get()).isTrue();
            assertThat(LlmCriteria.isValidJson("[1, 2, 3]").get()).isTrue();
            assertThat(LlmCriteria.isValidJson("not json").get()).isFalse();
        }

        @Test
        @DisplayName("hasJsonFields should check field presence")
        void hasJsonFieldsShouldCheck() {
            String json = "{\"name\": \"test\", \"value\": 42}";
            assertThat(LlmCriteria.hasJsonFields(json, "name").get()).isTrue();
            assertThat(LlmCriteria.hasJsonFields(json, "name", "value").get()).isTrue();
            assertThat(LlmCriteria.hasJsonFields(json, "missing").get()).isFalse();
        }
    }
}

