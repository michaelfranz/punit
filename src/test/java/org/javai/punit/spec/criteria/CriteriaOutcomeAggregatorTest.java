package org.javai.punit.spec.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.javai.punit.model.UseCaseCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CriteriaOutcomeAggregator")
class CriteriaOutcomeAggregatorTest {

    private CriteriaOutcomeAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CriteriaOutcomeAggregator();
    }

    @Nested
    @DisplayName("record()")
    class Record {

        @Test
        @DisplayName("should track samples recorded")
        void shouldTrackSamplesRecorded() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                .criterion("Test", () -> true)
                .build();

            aggregator.record(criteria);
            aggregator.record(criteria);
            aggregator.record(criteria);

            assertThat(aggregator.getSamplesRecorded()).isEqualTo(3);
        }

        @Test
        @DisplayName("should count all-passed samples")
        void shouldCountAllPassedSamples() {
            UseCaseCriteria allPass = UseCaseCriteria.ordered()
                .criterion("A", () -> true)
                .criterion("B", () -> true)
                .build();

            UseCaseCriteria someFail = UseCaseCriteria.ordered()
                .criterion("A", () -> true)
                .criterion("B", () -> false)
                .build();

            aggregator.record(allPass);
            aggregator.record(allPass);
            aggregator.record(someFail);

            assertThat(aggregator.getAllPassedCount()).isEqualTo(2);
            assertThat(aggregator.getSamplesRecorded()).isEqualTo(3);
        }

        @Test
        @DisplayName("should preserve criterion order")
        void shouldPreserveCriterionOrder() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                .criterion("First", () -> true)
                .criterion("Second", () -> true)
                .criterion("Third", () -> true)
                .build();

            aggregator.record(criteria);

            Map<String, CriteriaOutcomeAggregator.CriterionStats> stats = aggregator.getCriterionStats();
            String[] keys = stats.keySet().toArray(new String[0]);
            
            assertThat(keys).containsExactly("First", "Second", "Third");
        }
    }

    @Nested
    @DisplayName("CriterionStats")
    class CriterionStatsTests {

        @Test
        @DisplayName("should track passed count")
        void shouldTrackPassedCount() {
            UseCaseCriteria passing = UseCaseCriteria.ordered()
                .criterion("Test", () -> true)
                .build();
            UseCaseCriteria failing = UseCaseCriteria.ordered()
                .criterion("Test", () -> false)
                .build();

            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(failing);

            CriteriaOutcomeAggregator.CriterionStats stats = 
                aggregator.getCriterionStats().get("Test");
            
            assertThat(stats.getPassed()).isEqualTo(2);
            assertThat(stats.getFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should calculate pass rate")
        void shouldCalculatePassRate() {
            UseCaseCriteria passing = UseCaseCriteria.ordered()
                .criterion("Test", () -> true)
                .build();
            UseCaseCriteria failing = UseCaseCriteria.ordered()
                .criterion("Test", () -> false)
                .build();

            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(failing);

            CriteriaOutcomeAggregator.CriterionStats stats = 
                aggregator.getCriterionStats().get("Test");
            
            assertThat(stats.getPassRate()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("should track errored outcomes")
        void shouldTrackErroredOutcomes() {
            UseCaseCriteria erroring = UseCaseCriteria.ordered()
                .criterion("Test", () -> { throw new RuntimeException("oops"); })
                .build();

            aggregator.record(erroring);
            aggregator.record(erroring);

            CriteriaOutcomeAggregator.CriterionStats stats = 
                aggregator.getCriterionStats().get("Test");
            
            assertThat(stats.getErrored()).isEqualTo(2);
            assertThat(stats.getErrorMessages()).hasSize(2);
        }

        @Test
        @DisplayName("should limit error message collection")
        void shouldLimitErrorMessages() {
            UseCaseCriteria erroring = UseCaseCriteria.ordered()
                .criterion("Test", () -> { throw new RuntimeException("error"); })
                .build();

            for (int i = 0; i < 10; i++) {
                aggregator.record(erroring);
            }

            CriteriaOutcomeAggregator.CriterionStats stats = 
                aggregator.getCriterionStats().get("Test");
            
            assertThat(stats.getErrored()).isEqualTo(10);
            assertThat(stats.getErrorMessages()).hasSize(5); // Capped at 5
        }
    }

    @Nested
    @DisplayName("getOverallSuccessRate()")
    class OverallSuccessRate {

        @Test
        @DisplayName("should return 0 for no samples")
        void shouldReturnZeroForNoSamples() {
            assertThat(aggregator.getOverallSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should calculate correct rate")
        void shouldCalculateCorrectRate() {
            UseCaseCriteria allPass = UseCaseCriteria.ordered()
                .criterion("A", () -> true)
                .criterion("B", () -> true)
                .build();

            UseCaseCriteria someFail = UseCaseCriteria.ordered()
                .criterion("A", () -> true)
                .criterion("B", () -> false)
                .build();

            // 2 all-pass, 2 with failures
            aggregator.record(allPass);
            aggregator.record(allPass);
            aggregator.record(someFail);
            aggregator.record(someFail);

            assertThat(aggregator.getOverallSuccessRate()).isCloseTo(0.5, 
                org.assertj.core.data.Offset.offset(0.001));
        }
    }

    @Nested
    @DisplayName("getPassRateSummary()")
    class PassRateSummary {

        @Test
        @DisplayName("should return map of descriptions to pass rates")
        void shouldReturnPassRateMap() {
            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                .criterion("Always passes", () -> true)
                .criterion("Sometimes fails", () -> Math.random() > 0.5)
                .build();

            for (int i = 0; i < 100; i++) {
                aggregator.record(UseCaseCriteria.ordered()
                    .criterion("Always passes", () -> true)
                    .criterion("Never passes", () -> false)
                    .build());
            }

            Map<String, Double> summary = aggregator.getPassRateSummary();
            
            assertThat(summary.get("Always passes")).isEqualTo(1.0);
            assertThat(summary.get("Never passes")).isEqualTo(0.0);
        }
    }
}

