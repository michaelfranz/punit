package org.javai.punit.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SampleResultAggregator}.
 */
class SampleResultAggregatorTest {

    @Test
    void newAggregatorHasZeroSuccessesAndFailures() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getSuccesses()).isZero();
        assertThat(aggregator.getFailures()).isZero();
        assertThat(aggregator.getSamplesExecuted()).isZero();
        assertThat(aggregator.getTotalSamples()).isEqualTo(100);
    }

    @Test
    void recordSuccessIncrementsSuccessCount() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        
        assertThat(aggregator.getSuccesses()).isEqualTo(3);
        assertThat(aggregator.getFailures()).isZero();
        assertThat(aggregator.getSamplesExecuted()).isEqualTo(3);
    }

    @Test
    void recordFailureIncrementsFailureCount() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordFailure(new AssertionError("test"));
        aggregator.recordFailure(new AssertionError("test2"));
        
        assertThat(aggregator.getSuccesses()).isZero();
        assertThat(aggregator.getFailures()).isEqualTo(2);
        assertThat(aggregator.getSamplesExecuted()).isEqualTo(2);
    }

    @Test
    void observedPassRateCalculatedCorrectly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        // 8 successes, 2 failures = 80% pass rate
        for (int i = 0; i < 8; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(aggregator.getObservedPassRate()).isEqualTo(0.8);
    }

    @Test
    void observedPassRateIsZeroWhenNoSamplesExecuted() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getObservedPassRate()).isZero();
    }

    @Test
    void exampleFailuresAreCapturedUpToMaximum() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100, 3);
        
        aggregator.recordFailure(new AssertionError("failure 1"));
        aggregator.recordFailure(new AssertionError("failure 2"));
        aggregator.recordFailure(new AssertionError("failure 3"));
        aggregator.recordFailure(new AssertionError("failure 4")); // Should not be captured
        aggregator.recordFailure(new AssertionError("failure 5")); // Should not be captured
        
        assertThat(aggregator.getExampleFailures()).hasSize(3);
        assertThat(aggregator.getFailures()).isEqualTo(5);
    }

    @Test
    void nullFailureCauseIsHandledGracefully() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordFailure(null);
        
        assertThat(aggregator.getFailures()).isEqualTo(1);
        assertThat(aggregator.getExampleFailures()).isEmpty();
    }

    @Test
    void remainingSamplesCalculatedCorrectly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getRemainingSamples()).isEqualTo(100);
        
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        aggregator.recordFailure(null);
        
        assertThat(aggregator.getRemainingSamples()).isEqualTo(97);
    }

    @Test
    void elapsedTimeIsPositive() throws InterruptedException {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        Thread.sleep(10);
        
        assertThat(aggregator.getElapsedMs()).isGreaterThan(0);
    }
}

