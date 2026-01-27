package org.javai.punit.controls.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CostMonitor}.
 */
@DisplayName("CostMonitor")
class CostMonitorTest {

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("unlimited() creates monitor with no budget limits")
        void unlimitedCreatesMonitorWithNoBudgetLimits() {
            CostMonitor monitor = CostMonitor.unlimited();

            assertThat(monitor.hasTimeBudget()).isFalse();
            assertThat(monitor.hasTokenBudget()).isFalse();
        }

        @Test
        @DisplayName("withTimeBudget() creates monitor with time budget only")
        void withTimeBudgetCreatesMonitorWithTimeBudgetOnly() {
            CostMonitor monitor = CostMonitor.withTimeBudget(60000);

            assertThat(monitor.hasTimeBudget()).isTrue();
            assertThat(monitor.hasTokenBudget()).isFalse();
            assertThat(monitor.getTimeBudgetMs()).isEqualTo(60000);
        }

        @Test
        @DisplayName("withTokenBudget() creates monitor with token budget only")
        void withTokenBudgetCreatesMonitorWithTokenBudgetOnly() {
            CostMonitor monitor = CostMonitor.withTokenBudget(100000);

            assertThat(monitor.hasTimeBudget()).isFalse();
            assertThat(monitor.hasTokenBudget()).isTrue();
            assertThat(monitor.getTokenBudget()).isEqualTo(100000);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds monitor with all options")
        void buildsMonitorWithAllOptions() {
            CostMonitor monitor = CostMonitor.builder()
                    .timeBudgetMs(5000)
                    .tokenBudget(1000)
                    .onBudgetExhausted(BudgetExhaustedBehavior.EVALUATE_PARTIAL)
                    .build();

            assertThat(monitor.getTimeBudgetMs()).isEqualTo(5000);
            assertThat(monitor.getTokenBudget()).isEqualTo(1000);
            assertThat(monitor.getOnBudgetExhausted()).isEqualTo(BudgetExhaustedBehavior.EVALUATE_PARTIAL);
        }
    }

    @Nested
    @DisplayName("Token recording")
    class TokenRecording {

        @Test
        @DisplayName("records tokens correctly")
        void recordsTokensCorrectly() {
            CostMonitor monitor = CostMonitor.unlimited();

            monitor.recordTokens(100);
            monitor.recordTokens(50);

            assertThat(monitor.getTokensConsumed()).isEqualTo(150);
        }

        @Test
        @DisplayName("ignores zero or negative token values")
        void ignoresZeroOrNegativeTokenValues() {
            CostMonitor monitor = CostMonitor.unlimited();

            monitor.recordTokens(0);
            monitor.recordTokens(-10);
            monitor.recordTokens(50);

            assertThat(monitor.getTokensConsumed()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Sample recording")
    class SampleRecording {

        @Test
        @DisplayName("records sample execution")
        void recordsSampleExecution() {
            CostMonitor monitor = CostMonitor.unlimited();

            monitor.recordSampleExecuted();
            monitor.recordSampleExecuted();
            monitor.recordSampleExecuted();

            assertThat(monitor.getSamplesExecuted()).isEqualTo(3);
        }

        @Test
        @DisplayName("records sample with tokens")
        void recordsSampleWithTokens() {
            CostMonitor monitor = CostMonitor.unlimited();

            monitor.recordSampleWithTokens(100);
            monitor.recordSampleWithTokens(50);

            assertThat(monitor.getSamplesExecuted()).isEqualTo(2);
            assertThat(monitor.getTokensConsumed()).isEqualTo(150);
        }
    }

    @Nested
    @DisplayName("Elapsed time tracking")
    class ElapsedTimeTracking {

        @Test
        @DisplayName("tracks elapsed duration")
        void tracksElapsedDuration() throws InterruptedException {
            CostMonitor monitor = CostMonitor.unlimited();

            Thread.sleep(50);
            Duration elapsed = monitor.getElapsedDuration();

            assertThat(elapsed.toMillis()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("tracks elapsed milliseconds")
        void tracksElapsedMilliseconds() throws InterruptedException {
            CostMonitor monitor = CostMonitor.unlimited();

            Thread.sleep(10);

            assertThat(monitor.getElapsedMs()).isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("start time is captured at construction")
        void startTimeIsCapturedAtConstruction() {
            Instant before = Instant.now();
            CostMonitor monitor = CostMonitor.unlimited();
            Instant after = Instant.now();

            assertThat(monitor.getStartTime()).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("Time budget exhaustion")
    class TimeBudgetExhaustion {

        @Test
        @DisplayName("detects time budget exhaustion")
        void detectsTimeBudgetExhaustion() throws InterruptedException {
            CostMonitor monitor = CostMonitor.withTimeBudget(50);

            assertThat(monitor.isTimeBudgetExhausted()).isFalse();

            Thread.sleep(60);

            assertThat(monitor.isTimeBudgetExhausted()).isTrue();
        }

        @Test
        @DisplayName("checkTimeBudget returns termination reason when exhausted")
        void checkTimeBudgetReturnsTerminationReasonWhenExhausted() throws InterruptedException {
            CostMonitor monitor = CostMonitor.withTimeBudget(1);

            Thread.sleep(10);

            assertThat(monitor.checkTimeBudget())
                    .isPresent()
                    .contains(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("returns false when no time budget configured")
        void returnsFalseWhenNoTimeBudgetConfigured() throws InterruptedException {
            CostMonitor monitor = CostMonitor.unlimited();

            Thread.sleep(10);

            assertThat(monitor.isTimeBudgetExhausted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Token budget exhaustion")
    class TokenBudgetExhaustion {

        @Test
        @DisplayName("detects token budget exhaustion")
        void detectsTokenBudgetExhaustion() {
            CostMonitor monitor = CostMonitor.withTokenBudget(100);

            assertThat(monitor.isTokenBudgetExhausted()).isFalse();

            monitor.recordTokens(100);

            assertThat(monitor.isTokenBudgetExhausted()).isTrue();
        }

        @Test
        @DisplayName("checkTokenBudget returns termination reason when exhausted")
        void checkTokenBudgetReturnsTerminationReasonWhenExhausted() {
            CostMonitor monitor = CostMonitor.withTokenBudget(100);

            monitor.recordTokens(150);

            assertThat(monitor.checkTokenBudget())
                    .isPresent()
                    .contains(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("returns false when no token budget configured")
        void returnsFalseWhenNoTokenBudgetConfigured() {
            CostMonitor monitor = CostMonitor.unlimited();

            monitor.recordTokens(1000000);

            assertThat(monitor.isTokenBudgetExhausted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Combined budget checking")
    class CombinedBudgetChecking {

        @Test
        @DisplayName("isAnyBudgetExhausted returns true when time budget exhausted")
        void isAnyBudgetExhaustedReturnsTrueWhenTimeBudgetExhausted() throws InterruptedException {
            CostMonitor monitor = CostMonitor.builder()
                    .timeBudgetMs(50)
                    .tokenBudget(100000)
                    .build();

            Thread.sleep(60);

            assertThat(monitor.isAnyBudgetExhausted()).isTrue();
        }

        @Test
        @DisplayName("isAnyBudgetExhausted returns true when token budget exhausted")
        void isAnyBudgetExhaustedReturnsTrueWhenTokenBudgetExhausted() {
            CostMonitor monitor = CostMonitor.builder()
                    .timeBudgetMs(60000)
                    .tokenBudget(100)
                    .build();

            monitor.recordTokens(100);

            assertThat(monitor.isAnyBudgetExhausted()).isTrue();
        }

        @Test
        @DisplayName("isAnyBudgetExhausted returns false when neither exhausted")
        void isAnyBudgetExhaustedReturnsFalseWhenNeitherExhausted() {
            CostMonitor monitor = CostMonitor.builder()
                    .timeBudgetMs(60000)
                    .tokenBudget(100000)
                    .build();

            monitor.recordTokens(50);

            assertThat(monitor.isAnyBudgetExhausted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Remaining budget calculation")
    class RemainingBudgetCalculation {

        @Test
        @DisplayName("calculates remaining time budget")
        void calculatesRemainingTimeBudget() throws InterruptedException {
            CostMonitor monitor = CostMonitor.withTimeBudget(1000);

            Thread.sleep(100);

            long remaining = monitor.getRemainingTimeMs();
            assertThat(remaining).isLessThanOrEqualTo(900);
            assertThat(remaining).isGreaterThan(0);
        }

        @Test
        @DisplayName("returns MAX_VALUE when no time budget")
        void returnsMaxValueWhenNoTimeBudget() {
            CostMonitor monitor = CostMonitor.unlimited();

            assertThat(monitor.getRemainingTimeMs()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("calculates remaining token budget")
        void calculatesRemainingTokenBudget() {
            CostMonitor monitor = CostMonitor.withTokenBudget(1000);

            monitor.recordTokens(300);

            assertThat(monitor.getRemainingTokenBudget()).isEqualTo(700);
        }

        @Test
        @DisplayName("returns MAX_VALUE when no token budget")
        void returnsMaxValueWhenNoTokenBudget() {
            CostMonitor monitor = CostMonitor.unlimited();

            assertThat(monitor.getRemainingTokenBudget()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("remaining budget is zero when exceeded")
        void remainingBudgetIsZeroWhenExceeded() {
            CostMonitor monitor = CostMonitor.withTokenBudget(100);

            monitor.recordTokens(150);

            assertThat(monitor.getRemainingTokenBudget()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Exhaustion reason")
    class ExhaustionReason {

        @Test
        @DisplayName("returns time exhaustion reason")
        void returnsTimeExhaustionReason() throws InterruptedException {
            CostMonitor monitor = CostMonitor.withTimeBudget(50);

            Thread.sleep(60);

            assertThat(monitor.getExhaustionReason())
                    .isPresent()
                    .hasValueSatisfying(reason -> assertThat(reason).contains("Time budget exhausted"));
        }

        @Test
        @DisplayName("returns token exhaustion reason")
        void returnsTokenExhaustionReason() {
            CostMonitor monitor = CostMonitor.withTokenBudget(100);

            monitor.recordTokens(100);

            assertThat(monitor.getExhaustionReason())
                    .isPresent()
                    .hasValueSatisfying(reason -> assertThat(reason).contains("Token budget exhausted"));
        }

        @Test
        @DisplayName("returns empty when no budget exhausted")
        void returnsEmptyWhenNoBudgetExhausted() {
            CostMonitor monitor = CostMonitor.unlimited();

            assertThat(monitor.getExhaustionReason()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Global accumulator integration")
    class GlobalAccumulatorIntegration {

        @Test
        @DisplayName("propagates tokens to global accumulator when set")
        void propagatesTokensToGlobalAccumulatorWhenSet() {
            CostMonitor monitor = CostMonitor.unlimited();
            GlobalCostAccumulator accumulator = new GlobalCostAccumulator();
            monitor.setGlobalAccumulator(accumulator);

            monitor.recordTokens(100);

            assertThat(accumulator.getTotalTokens()).isEqualTo(100);
        }

        @Test
        @DisplayName("propagates samples to global accumulator when set")
        void propagatesSamplesToGlobalAccumulatorWhenSet() {
            CostMonitor monitor = CostMonitor.unlimited();
            GlobalCostAccumulator accumulator = new GlobalCostAccumulator();
            monitor.setGlobalAccumulator(accumulator);

            monitor.recordSampleExecuted();
            monitor.recordSampleExecuted();

            assertThat(accumulator.getTotalSamplesExecuted()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns global accumulator when set")
        void returnsGlobalAccumulatorWhenSet() {
            CostMonitor monitor = CostMonitor.unlimited();
            GlobalCostAccumulator accumulator = new GlobalCostAccumulator();
            monitor.setGlobalAccumulator(accumulator);

            assertThat(monitor.getGlobalAccumulator()).isSameAs(accumulator);
        }

        @Test
        @DisplayName("returns null when no global accumulator set")
        void returnsNullWhenNoGlobalAccumulatorSet() {
            CostMonitor monitor = CostMonitor.unlimited();

            assertThat(monitor.getGlobalAccumulator()).isNull();
            assertThat(monitor.isGlobalBudgetExhausted()).isFalse();
        }
    }
}
