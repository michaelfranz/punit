package org.javai.punit.experiment.explore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ExploreOutputWriter.
 *
 * <p>Verifies that explore output:
 * <ul>
 *   <li>Omits requirements section (minPassRate)</li>
 *   <li>Omits inferential statistics (standardError, confidenceInterval95)</li>
 *   <li>Includes descriptive statistics (observed, successes, failures)</li>
 *   <li>Includes failure distribution for qualitative insight</li>
 * </ul>
 */
@DisplayName("ExploreOutputWriter")
class ExploreOutputWriterTest {

    private ExploreOutputWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ExploreOutputWriter();
    }

    @Nested
    @DisplayName("Statistics Output")
    class StatisticsOutput {

        @Test
        @DisplayName("should include observed success rate")
        void shouldIncludeObservedSuccessRate() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("observed: 0.7000");
        }

        @Test
        @DisplayName("should include success and failure counts")
        void shouldIncludeSuccessAndFailureCounts() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("successes: 14")
                .contains("failures: 6");
        }

        @Test
        @DisplayName("should NOT include standardError")
        void shouldNotIncludeStandardError() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("standardError");
        }

        @Test
        @DisplayName("should NOT include confidenceInterval95")
        void shouldNotIncludeConfidenceInterval() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("confidenceInterval95");
        }

        @Test
        @DisplayName("should include failure distribution when present")
        void shouldIncludeFailureDistributionWhenPresent() {
            Map<String, Integer> failures = new LinkedHashMap<>();
            failures.put("TIMEOUT", 3);
            failures.put("VALIDATION_ERROR", 3);
            EmpiricalBaseline baseline = createExploreBaselineWithFailures(0.70, 14, 6, failures);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("failureDistribution:")
                .contains("TIMEOUT: 3")
                .contains("VALIDATION_ERROR: 3");
        }
    }

    @Nested
    @DisplayName("Requirements Section")
    class RequirementsSection {

        @Test
        @DisplayName("should NOT include requirements section")
        void shouldNotIncludeRequirementsSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("requirements:");
        }

        @Test
        @DisplayName("should NOT include minPassRate")
        void shouldNotIncludeMinPassRate() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("minPassRate");
        }
    }

    @Nested
    @DisplayName("Common Sections")
    class CommonSections {

        @Test
        @DisplayName("should include schemaVersion")
        void shouldIncludeSchemaVersion() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("schemaVersion: punit-spec-1");
        }

        @Test
        @DisplayName("should include useCaseId")
        void shouldIncludeUseCaseId() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("useCaseId: TestUseCase");
        }

        @Test
        @DisplayName("should include execution section")
        void shouldIncludeExecutionSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("execution:")
                .contains("samplesPlanned: 20")
                .contains("samplesExecuted: 20");
        }

        @Test
        @DisplayName("should include cost section")
        void shouldIncludeCostSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("cost:")
                .contains("totalTimeMs:")
                .contains("totalTokens:");
        }

        @Test
        @DisplayName("should include contentFingerprint")
        void shouldIncludeContentFingerprint() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).containsPattern("contentFingerprint: [a-f0-9]{64}");
        }
    }

    @Nested
    @DisplayName("Output Format")
    class OutputFormat {

        @Test
        @DisplayName("explore output has valid YAML structure")
        void exploreOutputHasValidYamlStructure() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            // Verify basic structure without requiring MEASURE schema compliance
            // (EXPLORE has a deliberately different format optimized for readability)
            assertThat(yaml)
                .contains("schemaVersion:")
                .contains("useCaseId:")
                .contains("statistics:")
                .contains("observed:")
                .contains("contentFingerprint:");
        }

        @Test
        @DisplayName("explore output is distinct from measure output")
        void exploreOutputIsDistinctFromMeasureOutput() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            // EXPLORE deliberately omits these MEASURE-specific fields
            assertThat(yaml)
                .as("EXPLORE output should NOT have requirements (comparative, not prescriptive)")
                .doesNotContain("requirements:")
                .doesNotContain("minPassRate:");

            assertThat(yaml)
                .as("EXPLORE output should NOT have inferential stats (unreliable with small samples)")
                .doesNotContain("standardError:")
                .doesNotContain("confidenceInterval95:");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private EmpiricalBaseline createExploreBaseline(
            double successRate, int successes, int failures) {
        return createExploreBaselineWithFailures(successRate, successes, failures, Map.of());
    }

    private EmpiricalBaseline createExploreBaselineWithFailures(
            double successRate, int successes, int failures,
            Map<String, Integer> failureDistribution) {

        int total = successes + failures;
        double se = Math.sqrt(successRate * (1 - successRate) / total);
        double ciLower = Math.max(0, successRate - 1.96 * se);
        double ciUpper = Math.min(1, successRate + 1.96 * se);

        StatisticsSummary stats = new StatisticsSummary(
            successRate, se, ciLower, ciUpper, successes, failures, failureDistribution
        );

        return EmpiricalBaseline.builder()
            .useCaseId("TestUseCase")
            .generatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .execution(new ExecutionSummary(total, total, "COMPLETED", null))
            .statistics(stats)
            .cost(new CostSummary(100, 5, 2000, 100))
            .build();
    }
}
