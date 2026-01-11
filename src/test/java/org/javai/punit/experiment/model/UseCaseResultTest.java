package org.javai.punit.experiment.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseResult")
class UseCaseResultTest {

    @Nested
    @DisplayName("getDiffableContent()")
    class GetDiffableContent {

        @Test
        @DisplayName("sorts keys alphabetically")
        void sortsKeysAlphabetically() {
            UseCaseResult result = UseCaseResult.builder()
                .value("zebra", "z")
                .value("apple", "a")
                .value("mango", "m")
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).containsExactly(
                "apple: a",
                "mango: m",
                "zebra: z"
            );
        }

        @Test
        @DisplayName("truncates long values with ellipsis")
        void truncatesLongValuesWithEllipsis() {
            UseCaseResult result = UseCaseResult.builder()
                .value("key", "This is a very long value that should be truncated")
                .build();

            List<String> content = result.getDiffableContent(30);

            assertThat(content).hasSize(1);
            assertThat(content.get(0)).hasSize(30);
            assertThat(content.get(0)).endsWith("…");
            assertThat(content.get(0)).startsWith("key: This is a very long");
        }

        @Test
        @DisplayName("escapes newlines in values")
        void escapesNewlinesInValues() {
            UseCaseResult result = UseCaseResult.builder()
                .value("multiline", "line1\nline2\nline3")
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).containsExactly("multiline: line1\\nline2\\nline3");
        }

        @Test
        @DisplayName("escapes tabs and carriage returns")
        void escapesTabsAndCarriageReturns() {
            UseCaseResult result = UseCaseResult.builder()
                .value("special", "col1\tcol2\r\n")
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).containsExactly("special: col1\\tcol2\\r\\n");
        }

        @Test
        @DisplayName("handles null values")
        void handlesNullValues() {
            UseCaseResult result = UseCaseResult.builder()
                .value("nullable", null)
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).containsExactly("nullable: null");
        }

        @Test
        @DisplayName("handles empty values map")
        void handlesEmptyValuesMap() {
            UseCaseResult result = UseCaseResult.builder().build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).isEmpty();
        }

        @Test
        @DisplayName("handles very long keys by truncating")
        void handlesVeryLongKeys() {
            UseCaseResult result = UseCaseResult.builder()
                .value("thisIsAnExtremelyLongKeyNameThatExceedsTheMaxLineLength", "value")
                .build();

            List<String> content = result.getDiffableContent(30);

            assertThat(content).hasSize(1);
            assertThat(content.get(0)).hasSize(30);
            assertThat(content.get(0)).endsWith("…");
        }

        @Test
        @DisplayName("uses object toString for complex values")
        void usesToStringForComplexValues() {
            record Product(String name, double price) {}
            
            UseCaseResult result = UseCaseResult.builder()
                .value("product", new Product("Widget", 29.99))
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content.get(0)).contains("Product");
            assertThat(content.get(0)).contains("Widget");
            assertThat(content.get(0)).contains("29.99");
        }

        @Test
        @DisplayName("preserves numeric precision")
        void preservesNumericPrecision() {
            UseCaseResult result = UseCaseResult.builder()
                .value("score", 0.123456789)
                .value("count", 42)
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).contains("count: 42");
            assertThat(content).contains("score: 0.123456789");
        }

        @Test
        @DisplayName("handles boolean values")
        void handlesBooleanValues() {
            UseCaseResult result = UseCaseResult.builder()
                .value("enabled", true)
                .value("visible", false)
                .build();

            List<String> content = result.getDiffableContent(80);

            assertThat(content).containsExactly("enabled: true", "visible: false");
        }
    }

    @Nested
    @DisplayName("record accessors")
    class RecordAccessors {

        @Test
        @DisplayName("values() returns immutable map")
        void valuesReturnsImmutableMap() {
            UseCaseResult result = UseCaseResult.builder()
                .value("key", "value")
                .build();

            assertThat(result.values()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("metadata() returns immutable map")
        void metadataReturnsImmutableMap() {
            UseCaseResult result = UseCaseResult.builder()
                .meta("requestId", "abc-123")
                .build();

            assertThat(result.metadata()).containsEntry("requestId", "abc-123");
        }

        @Test
        @DisplayName("timestamp() returns creation time")
        void timestampReturnsCreationTime() {
            Instant before = Instant.now();
            UseCaseResult result = UseCaseResult.builder().build();
            Instant after = Instant.now();

            assertThat(result.timestamp()).isBetween(before, after);
        }

        @Test
        @DisplayName("executionTime() returns configured duration")
        void executionTimeReturnsConfiguredDuration() {
            Duration duration = Duration.ofMillis(250);
            UseCaseResult result = UseCaseResult.builder()
                .executionTime(duration)
                .build();

            assertThat(result.executionTime()).isEqualTo(duration);
        }
    }

    @Nested
    @DisplayName("convenience accessors")
    class ConvenienceAccessors {

        @Test
        @DisplayName("getBoolean returns value or default")
        void getBooleanReturnsValueOrDefault() {
            UseCaseResult result = UseCaseResult.builder()
                .value("flag", true)
                .build();

            assertThat(result.getBoolean("flag", false)).isTrue();
            assertThat(result.getBoolean("missing", true)).isTrue();
        }

        @Test
        @DisplayName("getInt handles numeric coercion")
        void getIntHandlesNumericCoercion() {
            UseCaseResult result = UseCaseResult.builder()
                .value("count", 42L) // Long, not Integer
                .build();

            assertThat(result.getInt("count", 0)).isEqualTo(42);
        }

        @Test
        @DisplayName("getString returns value or default")
        void getStringReturnsValueOrDefault() {
            UseCaseResult result = UseCaseResult.builder()
                .value("name", "test")
                .build();

            assertThat(result.getString("name", "default")).isEqualTo("test");
            assertThat(result.getString("missing", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("hasValue checks key existence")
        void hasValueChecksKeyExistence() {
            UseCaseResult result = UseCaseResult.builder()
                .value("present", "value")
                .build();

            assertThat(result.hasValue("present")).isTrue();
            assertThat(result.hasValue("absent")).isFalse();
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("valuesFrom copies all values from source")
        void valuesFromCopiesAllValues() {
            UseCaseResult source = UseCaseResult.builder()
                .value("a", 1)
                .value("b", 2)
                .build();

            UseCaseResult copy = UseCaseResult.builder()
                .valuesFrom(source)
                .value("c", 3)
                .build();

            assertThat(copy.values()).containsEntry("a", 1);
            assertThat(copy.values()).containsEntry("b", 2);
            assertThat(copy.values()).containsEntry("c", 3);
        }

        @Test
        @DisplayName("metadataFrom copies all metadata from source")
        void metadataFromCopiesAllMetadata() {
            UseCaseResult source = UseCaseResult.builder()
                .meta("requestId", "123")
                .meta("backend", "openai")
                .build();

            UseCaseResult copy = UseCaseResult.builder()
                .metadataFrom(source)
                .build();

            assertThat(copy.metadata()).containsEntry("requestId", "123");
            assertThat(copy.metadata()).containsEntry("backend", "openai");
        }
    }

    @Nested
    @DisplayName("deprecated bridge methods")
    class DeprecatedBridgeMethods {

        @Test
        @DisplayName("getAllValues returns same as values()")
        @SuppressWarnings("deprecation")
        void getAllValuesReturnsSameAsValues() {
            UseCaseResult result = UseCaseResult.builder()
                .value("key", "value")
                .build();

            assertThat(result.getAllValues()).isEqualTo(result.values());
        }

        @Test
        @DisplayName("getTimestamp returns same as timestamp()")
        @SuppressWarnings("deprecation")
        void getTimestampReturnsSameAsTimestamp() {
            UseCaseResult result = UseCaseResult.builder().build();

            assertThat(result.getTimestamp()).isEqualTo(result.timestamp());
        }
    }
}

