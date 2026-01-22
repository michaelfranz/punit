package org.javai.punit.contract;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Outcomes")
class OutcomesTest {

    @Nested
    @DisplayName("ok()")
    class OkTests {

        @Test
        @DisplayName("creates successful outcome with value")
        void createsSuccessfulOutcome() {
            Outcome<String> outcome = Outcomes.ok("hello");

            assertThat(outcome.isOk()).isTrue();
            assertThat(outcome.getOrThrow()).isEqualTo("hello");
        }

        @Test
        @DisplayName("throws when value is null")
        void throwsWhenValueIsNull() {
            assertThatThrownBy(() -> Outcomes.ok(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value must not be null");
        }
    }

    @Nested
    @DisplayName("fail()")
    class FailTests {

        @Test
        @DisplayName("creates failed outcome with reason")
        void createsFailedOutcome() {
            Outcome<String> outcome = Outcomes.fail("Something went wrong");

            assertThat(outcome.isFail()).isTrue();
            assertThat(Outcomes.failureMessage(outcome)).isEqualTo("Something went wrong");
        }

        @Test
        @DisplayName("throws when reason is null")
        void throwsWhenReasonIsNull() {
            assertThatThrownBy(() -> Outcomes.fail(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("reason must not be null");
        }

        @Test
        @DisplayName("throws when reason is blank")
        void throwsWhenReasonIsBlank() {
            assertThatThrownBy(() -> Outcomes.fail("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be blank");
        }
    }

    @Nested
    @DisplayName("lift()")
    class LiftTests {

        @Test
        @DisplayName("lifts pure function into outcome-returning function")
        void liftsPureFunction() {
            Function<String, Outcome<String>> lifted = Outcomes.lift(String::toUpperCase);

            Outcome<String> result = lifted.apply("hello");

            assertThat(result.isOk()).isTrue();
            assertThat(result.getOrThrow()).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("throws when function is null")
        void throwsWhenFunctionIsNull() {
            assertThatThrownBy(() -> Outcomes.lift(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fn must not be null");
        }
    }

    @Nested
    @DisplayName("failureMessage()")
    class FailureMessageTests {

        @Test
        @DisplayName("extracts message from failed outcome")
        void extractsMessageFromFailedOutcome() {
            Outcome<String> outcome = Outcomes.fail("Error occurred");

            String message = Outcomes.failureMessage(outcome);

            assertThat(message).isEqualTo("Error occurred");
        }

        @Test
        @DisplayName("throws when outcome is successful")
        void throwsWhenOutcomeIsSuccessful() {
            Outcome<String> outcome = Outcomes.ok("success");

            assertThatThrownBy(() -> Outcomes.failureMessage(outcome))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot get failure message from successful outcome");
        }
    }
}
