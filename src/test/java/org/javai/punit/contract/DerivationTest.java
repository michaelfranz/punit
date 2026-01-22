package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Derivation")
class DerivationTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates derivation with description")
        void createsDerivation() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            assertThat(derivation.description()).isEqualTo("Valid number");
            assertThat(derivation.postconditions()).hasSize(1);
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new Derivation<>(
                    null,
                    s -> Outcomes.ok(s),
                    List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Derivation<>(
                    "   ",
                    s -> Outcomes.ok(s),
                    List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }

        @Test
        @DisplayName("throws when function is null")
        void throwsWhenFunctionIsNull() {
            assertThatThrownBy(() -> new Derivation<String, String>(
                    "Test",
                    null,
                    List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("function must not be null");
        }

        @Test
        @DisplayName("throws when postconditions is null")
        void throwsWhenPostconditionsIsNull() {
            assertThatThrownBy(() -> new Derivation<>(
                    "Test",
                    s -> Outcomes.ok(s),
                    null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("postconditions must not be null");
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("returns passed derivation and evaluated postconditions when derivation succeeds")
        void returnsPassedWhenDerivationSucceeds() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(
                            new Postcondition<>("Positive", n -> n > 0),
                            new Postcondition<>("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("42");

            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(2)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
        }

        @Test
        @DisplayName("returns failed derivation and skipped postconditions when derivation fails")
        void returnsFailedWhenDerivationFails() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    },
                    List.of(
                            new Postcondition<>("Positive", n -> n > 0),
                            new Postcondition<>("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(((PostconditionResult.Failed) results.get(0)).reason()).isEqualTo("Not a number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Skipped.class);
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(2)).isInstanceOf(PostconditionResult.Skipped.class);
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
        }

        @Test
        @DisplayName("returns failed derivation when function throws exception")
        void returnsFailedWhenFunctionThrows() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Skipped.class);
        }

        @Test
        @DisplayName("evaluates with no nested postconditions")
        void evaluatesWithNoNestedPostconditions() {
            Derivation<String, String> derivation = new Derivation<>(
                    "Uppercase",
                    s -> Outcomes.ok(s.toUpperCase()),
                    List.of());

            List<PostconditionResult> results = derivation.evaluate("hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(0).description()).isEqualTo("Uppercase");
        }
    }
}
