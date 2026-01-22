package org.javai.punit.contract;

import org.javai.punit.model.CriterionOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostconditionResultAdapter")
class PostconditionResultAdapterTest {

    @Nested
    @DisplayName("toCriterionOutcome()")
    class ToCriterionOutcomeTests {

        @Test
        @DisplayName("converts passed to CriterionOutcome.Passed")
        void convertsPassedToPassed() {
            PostconditionResult passed = PostconditionResult.passed("Response not empty");

            CriterionOutcome outcome = PostconditionResultAdapter.toCriterionOutcome(passed);

            assertThat(outcome).isInstanceOf(CriterionOutcome.Passed.class);
            assertThat(outcome.description()).isEqualTo("Response not empty");
            assertThat(outcome.passed()).isTrue();
        }

        @Test
        @DisplayName("converts failed with reason to CriterionOutcome.Failed")
        void convertsFailedWithReasonToFailed() {
            PostconditionResult failed = PostconditionResult.failed("Valid JSON", "Parse error at line 5");

            CriterionOutcome outcome = PostconditionResultAdapter.toCriterionOutcome(failed);

            assertThat(outcome).isInstanceOf(CriterionOutcome.Failed.class);
            assertThat(outcome.description()).isEqualTo("Valid JSON");
            assertThat(outcome.passed()).isFalse();
            CriterionOutcome.Failed failedOutcome = (CriterionOutcome.Failed) outcome;
            assertThat(failedOutcome.reason()).isEqualTo("Parse error at line 5");
        }

        @Test
        @DisplayName("converts failed with default reason to CriterionOutcome.Failed")
        void convertsFailedWithDefaultReasonToFailed() {
            PostconditionResult failed = PostconditionResult.failed("Response not empty");

            CriterionOutcome outcome = PostconditionResultAdapter.toCriterionOutcome(failed);

            assertThat(outcome).isInstanceOf(CriterionOutcome.Failed.class);
            CriterionOutcome.Failed failedOutcome = (CriterionOutcome.Failed) outcome;
            assertThat(failedOutcome.reason()).isEqualTo("Postcondition not satisfied");
        }

        @Test
        @DisplayName("converts skipped (failure with Skipped: prefix) to CriterionOutcome.NotEvaluated")
        void convertsSkippedToNotEvaluated() {
            // Skipped is now represented as a failure with "Skipped:" prefix
            PostconditionResult skipped = PostconditionResult.failed(
                    "Has products array", "Skipped: JSON parsing failed");

            CriterionOutcome outcome = PostconditionResultAdapter.toCriterionOutcome(skipped);

            assertThat(outcome).isInstanceOf(CriterionOutcome.NotEvaluated.class);
            assertThat(outcome.description()).isEqualTo("Has products array");
            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("throws on null result")
        void throwsOnNullResult() {
            assertThatThrownBy(() -> PostconditionResultAdapter.toCriterionOutcome(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("result must not be null");
        }
    }

    @Nested
    @DisplayName("toCriterionOutcomes()")
    class ToCriterionOutcomesTests {

        @Test
        @DisplayName("converts list of postcondition results")
        void convertsListOfResults() {
            List<PostconditionResult> results = List.of(
                    PostconditionResult.passed("Not empty"),
                    PostconditionResult.failed("Valid JSON", "Parse error"),
                    PostconditionResult.failed("Has field", "Skipped: JSON not available")
            );

            List<CriterionOutcome> outcomes = PostconditionResultAdapter.toCriterionOutcomes(results);

            assertThat(outcomes).hasSize(3);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Passed.class);
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.Failed.class);
            assertThat(outcomes.get(2)).isInstanceOf(CriterionOutcome.NotEvaluated.class);
        }

        @Test
        @DisplayName("returns empty list for empty input")
        void returnsEmptyListForEmptyInput() {
            List<CriterionOutcome> outcomes = PostconditionResultAdapter.toCriterionOutcomes(List.of());

            assertThat(outcomes).isEmpty();
        }

        @Test
        @DisplayName("throws on null list")
        void throwsOnNullList() {
            assertThatThrownBy(() -> PostconditionResultAdapter.toCriterionOutcomes(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("results must not be null");
        }
    }
}
