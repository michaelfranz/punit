package org.javai.punit.testsubjects;

import org.javai.punit.api.ProbabilisticTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test subject classes for integration tests.
 * These classes are used by ProbabilisticTestIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 * 
 * Note: These are intentionally not annotated with @Nested or any JUnit
 * container annotations to avoid direct discovery.
 */
public class ProbabilisticTestSubjects {

    // Prevent instantiation
    private ProbabilisticTestSubjects() {}

    public static class AlwaysPassingTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void alwaysPasses() {
            assertThat(true).isTrue();
        }
    }

    public static class AlwaysFailingTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void alwaysFails() {
            assertThat(false).isTrue();
        }
    }

    public static class PartiallyFailingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void failsHalf() {
            int count = counter.incrementAndGet();
            // Fail on odd numbers (1, 3, 5, 7, 9) = 50% failure rate
            assertThat(count % 2 == 0).isTrue();
        }
    }

    public static class BarelyPassingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void passes80Percent() {
            int count = counter.incrementAndGet();
            // Fail on 9, 10 = 80% pass rate (exactly meets threshold)
            assertThat(count <= 8).isTrue();
        }
    }

    public static class BarelyFailingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void passes70Percent() {
            int count = counter.incrementAndGet();
            // Fail on 8, 9, 10 = 70% pass rate (below 80% threshold)
            assertThat(count <= 7).isTrue();
        }
    }

    public static class ExceptionThrowingTest {
        @ProbabilisticTest(samples = 5, minPassRate = 1.0)
        void throwsException() {
            throw new RuntimeException("Unexpected exception");
        }
    }

    public static class MinPassRateZeroTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.0)
        void canFailCompletely() {
            assertThat(false).isTrue();
        }
    }

    public static class SingleSampleTest {
        @ProbabilisticTest(samples = 1, minPassRate = 1.0)
        void singleSamplePasses() {
            assertThat(true).isTrue();
        }
    }

    // ========== Phase 3: Configuration Override Test Subjects ==========

    /**
     * Test with small samples that can be scaled by multiplier.
     * Used to verify system property overrides work.
     */
    public static class ConfigurableSamplesTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        public static int getSamplesExecuted() {
            return counter.get();
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void configurableTest() {
            counter.incrementAndGet();
            assertThat(true).isTrue();
        }
    }

    // ========== Phase 2: Early Termination Test Subjects ==========

    /**
     * Test that should trigger early termination due to impossibility.
     * With 100 samples and 95% required, after 6 consecutive failures
     * it becomes impossible to reach 95 successes (max possible = 94).
     */
    public static class EarlyTerminationByImpossibilityTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        private static int samplesActuallyExecuted = 0;
        
        public static void resetCounter() {
            counter.set(0);
            samplesActuallyExecuted = 0;
        }
        
        public static int getSamplesActuallyExecuted() {
            return samplesActuallyExecuted;
        }
        
        @ProbabilisticTest(samples = 100, minPassRate = 0.95)
        void triggersEarlyTermination() {
            samplesActuallyExecuted = counter.incrementAndGet();
            // Always fails - should terminate after 6 failures
            assertThat(false).isTrue();
        }
    }

    /**
     * Test with 100% pass rate requirement - should terminate on first failure.
     */
    public static class TerminateOnFirstFailureTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        private static int samplesActuallyExecuted = 0;
        
        public static void resetCounter() {
            counter.set(0);
            samplesActuallyExecuted = 0;
        }
        
        public static int getSamplesActuallyExecuted() {
            return samplesActuallyExecuted;
        }
        
        @ProbabilisticTest(samples = 100, minPassRate = 1.0)
        void terminatesOnFirstFailure() {
            samplesActuallyExecuted = counter.incrementAndGet();
            // First sample fails
            assertThat(false).isTrue();
        }
    }

    /**
     * Test that passes before all samples are executed.
     * Even though some samples fail, the test should still run all samples
     * if it's still possible to pass (no early termination for passing tests).
     */
    public static class PassesWithSomeFailuresTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.7)
        void passesWithThreeFailures() {
            int count = counter.incrementAndGet();
            // Fail on 1, 2, 3 = 70% pass rate (exactly meets 70% threshold)
            assertThat(count > 3).isTrue();
        }
    }
}

