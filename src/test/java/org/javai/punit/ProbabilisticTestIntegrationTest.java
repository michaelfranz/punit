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
}
