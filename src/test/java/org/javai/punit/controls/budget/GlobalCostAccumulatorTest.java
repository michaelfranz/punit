package org.javai.punit.controls.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GlobalCostAccumulator")
class GlobalCostAccumulatorTest {

    private GlobalCostAccumulator accumulator;

    @BeforeEach
    void setUp() {
        accumulator = new GlobalCostAccumulator();
    }

    @Nested
    @DisplayName("Token recording")
    class TokenRecording {

        @Test
        @DisplayName("records tokens correctly")
        void recordsTokensCorrectly() {
            accumulator.recordTokens(100);
            accumulator.recordTokens(50);
            accumulator.recordTokens(25);

            assertThat(accumulator.getTotalTokens()).isEqualTo(175);
        }

        @Test
        @DisplayName("handles zero tokens")
        void handlesZeroTokens() {
            accumulator.recordTokens(0);

            assertThat(accumulator.getTotalTokens()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Sample recording")
    class SampleRecording {

        @Test
        @DisplayName("records samples correctly")
        void recordsSamplesCorrectly() {
            accumulator.recordSampleExecuted();
            accumulator.recordSampleExecuted();
            accumulator.recordSampleExecuted();

            assertThat(accumulator.getTotalSamplesExecuted()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Test method recording")
    class TestMethodRecording {

        @Test
        @DisplayName("records test method completion")
        void recordsTestMethodCompletion() {
            accumulator.recordTestMethodCompleted();
            accumulator.recordTestMethodCompleted();

            assertThat(accumulator.getTotalTestMethods()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Experiment method recording")
    class ExperimentMethodRecording {

        @Test
        @DisplayName("records experiment method completion with mode")
        void recordsExperimentMethodCompletionWithMode() {
            accumulator.recordExperimentMethodCompleted(GlobalCostAccumulator.ExperimentMode.MEASURE);
            accumulator.recordExperimentMethodCompleted(GlobalCostAccumulator.ExperimentMode.EXPLORE);
            accumulator.recordExperimentMethodCompleted(GlobalCostAccumulator.ExperimentMode.OPTIMIZE);

            assertThat(accumulator.getTotalExperimentMethods()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Elapsed time tracking")
    class ElapsedTimeTracking {

        @Test
        @DisplayName("tracks elapsed time")
        void tracksElapsedTime() throws InterruptedException {
            Thread.sleep(50);
            Duration elapsed = accumulator.getElapsedTime();

            assertThat(elapsed.toMillis()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("getElapsedMs returns milliseconds")
        void getElapsedMsReturnsMilliseconds() throws InterruptedException {
            Thread.sleep(10);

            assertThat(accumulator.getElapsedMs()).isGreaterThanOrEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafety {

        @Test
        @DisplayName("handles concurrent token recording")
        void handlesConcurrentTokenRecording() throws InterruptedException {
            int threadCount = 10;
            int tokensPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < tokensPerThread; j++) {
                        accumulator.recordTokens(1);
                    }
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(accumulator.getTotalTokens()).isEqualTo(threadCount * tokensPerThread);
        }

        @Test
        @DisplayName("handles concurrent sample recording")
        void handlesConcurrentSampleRecording() throws InterruptedException {
            int threadCount = 10;
            int samplesPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < samplesPerThread; j++) {
                        accumulator.recordSampleExecuted();
                    }
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(accumulator.getTotalSamplesExecuted()).isEqualTo(threadCount * samplesPerThread);
        }
    }

    @Nested
    @DisplayName("Budget checking (when no budget configured)")
    class BudgetChecking {

        @Test
        @DisplayName("time budget is not exhausted when not configured")
        void timeBudgetNotExhaustedWhenNotConfigured() {
            assertThat(accumulator.hasTimeBudget()).isFalse();
            assertThat(accumulator.isTimeBudgetExhausted()).isFalse();
        }

        @Test
        @DisplayName("token budget is not exhausted when not configured")
        void tokenBudgetNotExhaustedWhenNotConfigured() {
            assertThat(accumulator.hasTokenBudget()).isFalse();
            assertThat(accumulator.isTokenBudgetExhausted()).isFalse();
        }

        @Test
        @DisplayName("isAnyBudgetExhausted returns false when no budget configured")
        void isAnyBudgetExhaustedReturnsFalseWhenNoBudgetConfigured() {
            assertThat(accumulator.isAnyBudgetExhausted()).isFalse();
        }
    }
}
