package org.javai.punit.spec.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CriteriaInvoker")
class CriteriaInvokerTest {

    private CriteriaInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new CriteriaInvoker();
    }

    @Nested
    @DisplayName("invoke()")
    class Invoke {

        @Test
        @DisplayName("should invoke criteria method on use case")
        void shouldInvokeCriteriaMethod() {
            UseCaseResult result = UseCaseResult.builder()
                .value("response", "valid response")
                .build();
            ValidUseCase useCase = new ValidUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            assertThat(criteria).isNotNull();
            List<CriterionOutcome> outcomes = criteria.evaluate();
            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0).description()).isEqualTo("Has response");
            assertThat(outcomes.get(0).passed()).isTrue();
        }

        @Test
        @DisplayName("should use default criteria when no criteria method exists")
        void shouldUseDefaultCriteriaWhenNoMethod() {
            UseCaseResult result = UseCaseResult.builder()
                .value("success", true)
                .value("isValid", true)
                .build();
            NoCriteriaUseCase useCase = new NoCriteriaUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            assertThat(criteria).isNotNull();
            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should handle criteria method that throws exception")
        void shouldHandleCriteriaMethodException() {
            UseCaseResult result = UseCaseResult.builder().build();
            ThrowingCriteriaUseCase useCase = new ThrowingCriteriaUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            assertThat(criteria).isNotNull();
            List<CriterionOutcome> outcomes = criteria.evaluate();
            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
        }

        @Test
        @DisplayName("should propagate Error subclasses")
        void shouldPropagateErrors() {
            UseCaseResult result = UseCaseResult.builder().build();
            ErrorThrowingUseCase useCase = new ErrorThrowingUseCase();

            assertThatThrownBy(() -> invoker.invoke(useCase, result))
                .isInstanceOf(OutOfMemoryError.class);
        }

        @Test
        @DisplayName("should handle criteria method returning null")
        void shouldHandleNullReturn() {
            UseCaseResult result = UseCaseResult.builder().build();
            NullReturningUseCase useCase = new NullReturningUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            assertThat(criteria).isNotNull();
            List<CriterionOutcome> outcomes = criteria.evaluate();
            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Errored.class);
        }

        @Test
        @DisplayName("should reject null use case instance")
        void shouldRejectNullUseCase() {
            UseCaseResult result = UseCaseResult.builder().build();

            assertThatThrownBy(() -> invoker.invoke(null, result))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("useCaseInstance");
        }

        @Test
        @DisplayName("should reject null result")
        void shouldRejectNullResult() {
            ValidUseCase useCase = new ValidUseCase();

            assertThatThrownBy(() -> invoker.invoke(useCase, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("result");
        }
    }

    @Nested
    @DisplayName("Default criteria (trivial postcondition)")
    class DefaultCriteria {

        @Test
        @DisplayName("should return empty criteria when no criteria method exists")
        void shouldReturnEmptyCriteria() {
            UseCaseResult result = UseCaseResult.builder()
                .value("anything", "doesn't matter")
                .build();
            NoCriteriaUseCase useCase = new NoCriteriaUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            // DbC: trivial postcondition = empty criteria
            assertThat(criteria.evaluate()).isEmpty();
        }

        @Test
        @DisplayName("should trivially pass regardless of result content")
        void shouldTriviallyPassRegardlessOfResultContent() {
            // Even with "failure" indicators, the trivial postcondition passes
            // because the use case hasn't declared what success means
            UseCaseResult result = UseCaseResult.builder()
                .value("success", false)
                .value("error", "Something went wrong")
                .build();
            NoCriteriaUseCase useCase = new NoCriteriaUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            // DbC: if no criteria declared, trivial postcondition satisfied
            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should trivially pass for empty result")
        void shouldTriviallyPassForEmptyResult() {
            UseCaseResult result = UseCaseResult.builder().build();
            NoCriteriaUseCase useCase = new NoCriteriaUseCase();

            UseCaseCriteria criteria = invoker.invoke(useCase, result);

            assertThat(criteria.allPassed()).isTrue();
        }
    }

    // ========== Test Fixtures ==========

    static class ValidUseCase {
        public UseCaseCriteria criteria(UseCaseResult result) {
            String response = result.getString("response", "");
            return UseCaseCriteria.ordered()
                .criterion("Has response", () -> !response.isEmpty())
                .build();
        }
    }

    static class NoCriteriaUseCase {
        public void doSomething() {}
    }

    static class ThrowingCriteriaUseCase {
        public UseCaseCriteria criteria(UseCaseResult result) {
            throw new RuntimeException("Criteria construction failed");
        }
    }

    @SuppressWarnings("unused")
    static class ErrorThrowingUseCase {
        public UseCaseCriteria criteria(UseCaseResult result) {
            throw new OutOfMemoryError("Simulated error");
        }
    }

    static class NullReturningUseCase {
        public UseCaseCriteria criteria(UseCaseResult result) {
            return null;
        }
    }
}

