package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.javai.punit.experiment.api.DiffableContentProvider;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultProjectionBuilder")
class ResultProjectionBuilderTest {

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("rejects maxDiffableLines less than 1")
        void rejectsMaxDiffableLinesLessThan1() {
            assertThatThrownBy(() -> new ResultProjectionBuilder(0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDiffableLines must be at least 1");
        }

        @Test
        @DisplayName("rejects maxLineLength less than 10")
        void rejectsMaxLineLengthLessThan10() {
            assertThatThrownBy(() -> new ResultProjectionBuilder(5, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLineLength must be at least 10");
        }
    }

    @Nested
    @DisplayName("build()")
    class Build {

        @Test
        @DisplayName("creates projection with correct sample index")
        void createsProjectionWithCorrectSampleIndex() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseResult result = UseCaseResult.builder().value("key", "value").build();

            ResultProjection projection = builder.build(3, result);

            assertThat(projection.sampleIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("captures execution time in milliseconds")
        void capturesExecutionTimeInMilliseconds() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseResult result = UseCaseResult.builder()
                .value("key", "value")
                .executionTime(Duration.ofMillis(250))
                .build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.executionTimeMs()).isEqualTo(250);
        }

        @Test
        @DisplayName("uses getDiffableContent from result")
        void usesGetDiffableContentFromResult() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseResult result = UseCaseResult.builder()
                .value("alpha", "a")
                .value("beta", "b")
                .build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.diffableLines()).contains("alpha: a", "beta: b");
        }
    }

    @Nested
    @DisplayName("line count normalization")
    class LineCountNormalization {

        @Test
        @DisplayName("pads with <absent> when fewer values than max")
        void padsWithAbsentWhenFewerValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseResult result = UseCaseResult.builder()
                .value("only", "one")
                .build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.diffableLines()).hasSize(5);
            assertThat(projection.diffableLines().get(0)).isEqualTo("only: one");
            assertThat(projection.diffableLines().get(1)).isEqualTo(ResultProjection.ABSENT);
            assertThat(projection.diffableLines().get(4)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("adds truncation notice when more values than max")
        void addsTruncationNoticeWhenMoreValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(3, 60);
            UseCaseResult result = UseCaseResult.builder()
                .value("a", "1")
                .value("b", "2")
                .value("c", "3")
                .value("d", "4")
                .value("e", "5")
                .build();

            ResultProjection projection = builder.build(0, result);

            // Should have 3 content lines + 1 truncation notice = 4 total
            assertThat(projection.diffableLines()).hasSize(4);
            assertThat(projection.diffableLines().get(0)).isEqualTo("a: 1");
            assertThat(projection.diffableLines().get(1)).isEqualTo("b: 2");
            assertThat(projection.diffableLines().get(2)).isEqualTo("c: 3");
            assertThat(projection.diffableLines().get(3)).isEqualTo("<truncated: +2 more>");
        }

        @Test
        @DisplayName("no padding or truncation when exactly max values")
        void noPaddingOrTruncationWhenExactlyMaxValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(3, 60);
            UseCaseResult result = UseCaseResult.builder()
                .value("a", "1")
                .value("b", "2")
                .value("c", "3")
                .build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.diffableLines()).hasSize(3);
            assertThat(projection.diffableLines()).noneMatch(line -> 
                line.equals(ResultProjection.ABSENT) || line.startsWith("<truncated"));
        }
    }

    @Nested
    @DisplayName("buildError()")
    class BuildError {

        @Test
        @DisplayName("includes error class name")
        void includesErrorClassName() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            
            ResultProjection projection = builder.buildError(
                0, 
                100, 
                new IllegalArgumentException("test message")
            );

            assertThat(projection.diffableLines().get(0))
                .isEqualTo("error: IllegalArgumentException");
        }

        @Test
        @DisplayName("includes error message")
        void includesErrorMessage() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            
            ResultProjection projection = builder.buildError(
                0, 
                100, 
                new IllegalArgumentException("test message")
            );

            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: test message");
        }

        @Test
        @DisplayName("pads remaining lines with absent")
        void padsRemainingLinesWithAbsent() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            
            ResultProjection projection = builder.buildError(
                0, 
                100, 
                new RuntimeException("error")
            );

            // 2 content lines (error, message) + 3 absent = 5 total
            assertThat(projection.diffableLines()).hasSize(5);
            assertThat(projection.diffableLines().get(2)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("handles multi-line error messages")
        void handlesMultilineErrorMessages() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            
            ResultProjection projection = builder.buildError(
                0, 
                100, 
                new RuntimeException("first line\nsecond line\nthird line")
            );

            // Should only show first line
            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: first line");
        }

        @Test
        @DisplayName("handles null error message")
        void handlesNullErrorMessage() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            
            ResultProjection projection = builder.buildError(
                0, 
                100, 
                new RuntimeException((String) null)
            );

            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: null");
        }
    }

    @Nested
    @DisplayName("custom provider")
    class CustomProvider {

        @Test
        @DisplayName("uses custom provider when supplied")
        void usesCustomProviderWhenSupplied() {
            DiffableContentProvider customProvider = (result, maxLineLength) -> 
                List.of("custom: projection");
            
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60, customProvider);
            UseCaseResult result = UseCaseResult.builder()
                .value("ignored", "value")
                .build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.diffableLines().get(0)).isEqualTo("custom: projection");
        }

        @Test
        @DisplayName("pads custom provider output if needed")
        void padsCustomProviderOutputIfNeeded() {
            DiffableContentProvider customProvider = (result, maxLineLength) -> 
                List.of("line1", "line2");
            
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60, customProvider);
            UseCaseResult result = UseCaseResult.builder().build();

            ResultProjection projection = builder.build(0, result);

            assertThat(projection.diffableLines()).hasSize(5);
            assertThat(projection.diffableLines().get(2)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("truncates custom provider output if exceeds max")
        void truncatesCustomProviderOutputIfExceedsMax() {
            DiffableContentProvider customProvider = (result, maxLineLength) -> 
                List.of("a", "b", "c", "d", "e", "f", "g");
            
            ResultProjectionBuilder builder = new ResultProjectionBuilder(3, 60, customProvider);
            UseCaseResult result = UseCaseResult.builder().build();

            ResultProjection projection = builder.build(0, result);

            // 3 content + 1 truncation = 4 total
            assertThat(projection.diffableLines()).hasSize(4);
            assertThat(projection.diffableLines().get(3)).isEqualTo("<truncated: +4 more>");
        }
    }
}

