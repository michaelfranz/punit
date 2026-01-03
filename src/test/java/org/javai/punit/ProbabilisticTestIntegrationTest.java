package org.javai.punit;

import org.javai.punit.testsubjects.ProbabilisticTestSubjects.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for the probabilistic testing framework.
 * 
 * These tests use JUnit Platform TestKit to execute and verify
 * probabilistic tests defined in the testsubjects package.
 */
class ProbabilisticTestIntegrationTest {

    @Test
    void alwaysPassingTestPasses() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(AlwaysPassingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(10)
                        .failed(0));
    }

    @Test
    void alwaysFailingTestFails() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(AlwaysFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 80% of 10 = 8 required, after 3 failures max possible = 0 + 7 = 7 < 8
                        // So early termination kicks in after 3 samples
                        .started(3)
                        .succeeded(2)  // First 2 samples complete without throwing
                        .failed(1));   // 3rd sample fails with aggregated result
    }

    @Test
    void partiallyFailingTestWithLowThresholdPasses() {
        PartiallyFailingTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(PartiallyFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(10)  // All samples complete, overall test passes
                        .failed(0));
    }

    @Test
    void barelyPassingTestPasses() {
        BarelyPassingTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(BarelyPassingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(10)
                        .failed(0));
    }

    @Test
    void barelyFailingTestFails() {
        BarelyFailingTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(BarelyFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(9)
                        .failed(1));
    }

    @Test
    void exceptionsTreatedAsFailures() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(ExceptionThrowingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 100% of 5 = 5 required, after 1 failure max possible = 0 + 4 = 4 < 5
                        // So early termination kicks in after 1 sample
                        .started(1)
                        .failed(1));   // First sample fails, terminates immediately
    }

    @Test
    void minPassRateZeroAlwaysPasses() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(MinPassRateZeroTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(10)
                        .failed(0));
    }

    @Test
    void singleSampleTestWorks() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(SingleSampleTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(1)
                        .succeeded(1)
                        .failed(0));
    }

    // ========== Phase 3: Configuration Override Tests ==========

    @Test
    void systemPropertyOverridesSamples() {
        ConfigurableSamplesTest.resetCounter();
        
        // Set system property to override samples from 10 to 5
        System.setProperty("punit.samples", "5");
        try {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(ConfigurableSamplesTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(5)  // Overridden from 10 to 5
                            .succeeded(5)
                            .failed(0));
            
            org.assertj.core.api.Assertions.assertThat(
                    ConfigurableSamplesTest.getSamplesExecuted())
                    .isEqualTo(5);
        } finally {
            System.clearProperty("punit.samples");
        }
    }

    @Test
    void samplesMultiplierScalesSamples() {
        ConfigurableSamplesTest.resetCounter();
        
        // Set multiplier to 0.5 (halves the samples from 10 to 5)
        System.setProperty("punit.samplesMultiplier", "0.5");
        try {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(ConfigurableSamplesTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(5)  // 10 * 0.5 = 5
                            .succeeded(5)
                            .failed(0));
            
            org.assertj.core.api.Assertions.assertThat(
                    ConfigurableSamplesTest.getSamplesExecuted())
                    .isEqualTo(5);
        } finally {
            System.clearProperty("punit.samplesMultiplier");
        }
    }

    @Test
    void minPassRateOverrideWorks() {
        BarelyFailingTest.resetCounter();
        
        // BarelyFailingTest passes 70% but requires 80%, so it fails
        // Override minPassRate to 0.6 so it should pass
        System.setProperty("punit.minPassRate", "0.6");
        try {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(BarelyFailingTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(10)
                            .succeeded(10)  // Now passes because 70% >= 60%
                            .failed(0));
        } finally {
            System.clearProperty("punit.minPassRate");
        }
    }

    // ========== Phase 2: Early Termination Tests ==========

    @Test
    void earlyTerminationByImpossibility() {
        EarlyTerminationByImpossibilityTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(EarlyTerminationByImpossibilityTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 95% of 100 = 95 required, after 6 failures max possible = 94
                        // So only 6 samples should run, not 100
                        .started(6)
                        .succeeded(5)  // First 5 samples complete without throwing
                        .failed(1));   // 6th sample fails with final verdict
        
        // Verify only 6 samples actually executed
        org.assertj.core.api.Assertions.assertThat(
                EarlyTerminationByImpossibilityTest.getSamplesActuallyExecuted())
                .isEqualTo(6);
    }

    @Test
    void terminatesOnFirstFailureWith100PercentRequired() {
        TerminateOnFirstFailureTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(TerminateOnFirstFailureTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 100% required, first failure makes it impossible
                        .started(1)
                        .failed(1));
        
        // Verify only 1 sample actually executed
        org.assertj.core.api.Assertions.assertThat(
                TerminateOnFirstFailureTest.getSamplesActuallyExecuted())
                .isEqualTo(1);
    }

    @Test
    void passesWithSomeFailuresNoEarlyTermination() {
        PassesWithSomeFailuresTest.resetCounter();
        
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(PassesWithSomeFailuresTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // All 10 samples should run (no early termination for passing tests)
                        .started(10)
                        .succeeded(10)
                        .failed(0));
    }

    // ========== Phase 5: Budget Scope Tests ==========

    @Test
    void classTokenBudgetExhaustion() {
        ClassTokenBudgetTest.reset();
        
        // Class has 100 token budget, each sample uses 30 tokens
        // After 4 samples: 120 tokens > 100 budget
        // With FAIL behavior, test should fail when budget exhausted
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(ClassTokenBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .failed(1));  // Test fails due to budget exhaustion
        
        // Verify only ~4 samples ran (budget exhausted after 4th sample)
        int tokensRecorded = ClassTokenBudgetTest.getTotalTokensRecorded();
        org.assertj.core.api.Assertions.assertThat(tokensRecorded)
                .isGreaterThanOrEqualTo(90)  // At least 3 samples
                .isLessThanOrEqualTo(150);   // At most 5 samples
    }

    @Test
    void classTimeBudgetExhaustion() {
        ClassTimeBudgetTest.reset();
        
        // Class has 50ms time budget, each sample takes 20ms
        // Should run ~2-3 samples before budget exhausted
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(ClassTimeBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .failed(1));  // Test fails due to budget exhaustion
        
        // Verify fewer than 100 samples ran
        int samplesExecuted = ClassTimeBudgetTest.getSamplesExecuted();
        org.assertj.core.api.Assertions.assertThat(samplesExecuted)
                .isLessThan(10);  // Much less than 100 samples
    }

    @Test
    void evaluatePartialBehaviorPasses() {
        // With EVALUATE_PARTIAL, test should pass if enough samples passed
        // before budget exhaustion (50 token budget, 30 per sample = 2 samples run)
        // 2/2 = 100% >= 50% required, so test passes
        EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(EvaluatePartialBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .succeeded(2)   // 2 samples succeeded before budget exhausted
                        .failed(0));    // No samples failed
    }
}
