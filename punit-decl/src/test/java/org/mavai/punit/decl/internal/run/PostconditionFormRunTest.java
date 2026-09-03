package org.mavai.punit.decl.internal.run;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.punit.runtime.PUnit.Declared;
import org.mavai.punit.runtime.PUnit;
import org.opentest4j.AssertionFailedError;

/**
 * Every postcondition form the contract format defines, judged at
 * runtime: one method per form, each driving a passing and a failing
 * contract over the same fixed document (the {@code form-oracle}
 * binding) so the form's own judgement, and nothing else, decides. The
 * published conformance corpus proves the grammar loads; this proves
 * the judgement.
 */
@DisplayName("Postcondition forms at runtime")
class PostconditionFormRunTest {

    @Nested
    @DisplayName("string forms")
    class StringForms {

        @Test
        @DisplayName("the exact string form")
        void equals() {
            assertThatCode(() -> PUnit.declared("form-equals-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-equals-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the membership form judges a selected string")
        void oneOf() {
            assertThatCode(() -> PUnit.declared("form-one-of-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-one-of-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the substring form judges the raw response")
        void contains() {
            assertThatCode(() -> PUnit.declared("form-contains-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-contains-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the regular-expression form judges the raw response")
        void matches() {
            assertThatCode(() -> PUnit.declared("form-matches-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-matches-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the parseability form: the view either parses or the trial fails")
        void parses() {
            assertThatCode(() -> PUnit.declared("form-parses-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-parses-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the registered-check form receives the subject")
        void satisfies() {
            assertThatCode(() -> PUnit.declared("form-satisfies-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-satisfies-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }
    }

    @Nested
    @DisplayName("value-comparison forms")
    class ValueComparisonForms {

        @Test
        @DisplayName("the numeric equality form judges decimals across spellings")
        void eq() {
            assertThatCode(() -> PUnit.declared("form-eq-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-eq-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the numeric inequality form judges decimals")
        void ne() {
            assertThatCode(() -> PUnit.declared("form-ne-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-ne-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the strictly-less-than form")
        void lt() {
            assertThatCode(() -> PUnit.declared("form-lt-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-lt-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the at-most form")
        void le() {
            assertThatCode(() -> PUnit.declared("form-le-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-le-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the strictly-greater-than form")
        void gt() {
            assertThatCode(() -> PUnit.declared("form-gt-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-gt-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the at-least form reads a numeric string")
        void ge() {
            assertThatCode(() -> PUnit.declared("form-ge-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-ge-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the string inequality form")
        void notEquals() {
            assertThatCode(() -> PUnit.declared("form-not-equals-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-not-equals-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the case-insensitive form folds case, trims and collapses whitespace")
        void equalsCi() {
            assertThatCode(() -> PUnit.declared("form-equals-ci-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-equals-ci-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the null form holds on JSON null and on an absent path")
        void isNull() {
            assertThatCode(() -> PUnit.declared("form-is-null-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-is-null-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the boolean form judges identity")
        void is() {
            assertThatCode(() -> PUnit.declared("form-is-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-is-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }
    }

    @Nested
    @DisplayName("set forms")
    class SetForms {

        @Test
        @DisplayName("the multiset form: order-free, duplicates significant")
        void equalsSet() {
            assertThatCode(() -> PUnit.declared("form-equals-set-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-equals-set-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the containment form reads decimals across spellings")
        void containsSet() {
            assertThatCode(() -> PUnit.declared("form-contains-set-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-contains-set-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the cardinality form counts the selection, zero included")
        void countEquals() {
            assertThatCode(() -> PUnit.declared("form-count-equals-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-count-equals-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }

        @Test
        @DisplayName("the graded set claim: required members, an optional floor, extras refused")
        void setOf() {
            assertThatCode(() -> PUnit.declared("form-set-of-holds").assertPasses())
                    .doesNotThrowAnyException();
            Declared failing = PUnit.declared("form-set-of-fails").samples(30);
            assertThatThrownBy(failing::assertPasses)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("the-form-holds");
        }
    }
}
