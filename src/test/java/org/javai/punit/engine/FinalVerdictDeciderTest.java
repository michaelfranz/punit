package org.javai.punit.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FinalVerdictDecider}.
 */
class FinalVerdictDeciderTest {

    private final FinalVerdictDecider decider = new FinalVerdictDecider();

    @Test
    void isPassingReturnsTrueWhenPassRateMetExactly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 95; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 5; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isTrue();
    }

    @Test
    void isPassingReturnsTrueWhenPassRateExceeded() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 98; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isTrue();
    }

    @Test
    void isPassingReturnsFalseWhenPassRateNotMet() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 90; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 10; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isFalse();
    }

    @Test
    void isPassingReturnsTrueWhenMinPassRateIsZero() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 100; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.0)).isTrue();
    }

    @Test
    void isPassingReturnsFalseWhenMinPassRateIsOneAndAnyFailure() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 99; i++) {
            aggregator.recordSuccess();
        }
        aggregator.recordFailure(null);
        
        assertThat(decider.isPassing(aggregator, 1.0)).isFalse();
    }

    @Test
    void calculateRequiredSuccessesRoundsUp() {
        // 95% of 10 = 9.5, should round up to 10
        assertThat(decider.calculateRequiredSuccesses(10, 0.95)).isEqualTo(10);
        
        // 95% of 100 = 95
        assertThat(decider.calculateRequiredSuccesses(100, 0.95)).isEqualTo(95);
        
        // 90% of 100 = 90
        assertThat(decider.calculateRequiredSuccesses(100, 0.90)).isEqualTo(90);
        
        // 80% of 7 = 5.6, should round up to 6
        assertThat(decider.calculateRequiredSuccesses(7, 0.80)).isEqualTo(6);
    }

    @Test
    void buildFailureMessageIncludesStatistics() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 80; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 20; i++) {
            aggregator.recordFailure(new AssertionError("test failure " + i));
        }
        
        String message = decider.buildFailureMessage(aggregator, 0.95);
        
        assertThat(message)
                .contains("80.00%")
                .contains("95.00%")
                .contains("Samples executed: 100")
                .contains("Successes: 80")
                .contains("Failures: 20")
                .contains("Example failures");
    }

    @Test
    void buildSuccessMessageIncludesStatistics() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 98; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        String message = decider.buildSuccessMessage(aggregator, 0.95);
        
        assertThat(message)
                .contains("98.00%")
                .contains("95.00%")
                .contains("98/100");
    }
}

