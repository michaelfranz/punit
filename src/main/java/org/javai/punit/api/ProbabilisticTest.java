package org.javai.punit.api;

import org.javai.punit.engine.ProbabilisticTestExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a probabilistic test that will be executed multiple times
 * with pass/fail determined by whether the observed pass rate meets a minimum threshold.
 *
 * <p>Probabilistic tests are useful for testing non-deterministic systems where
 * individual invocations may occasionally fail, but the overall behavior should
 * meet a statistical threshold.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100, minPassRate = 0.95)
 * void serviceReturnsValidResponse() {
 *     Response response = service.call();
 *     assertThat(response.isValid()).isTrue();
 * }
 * }</pre>
 *
 * <p>The test above will execute 100 times and pass if at least 95% of
 * invocations succeed.
 *
 * @see ProbabilisticTestExtension
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {

    /**
     * Number of sample invocations to execute.
     * Must be ≥ 1.
     *
     * @return the number of samples to execute
     */
    int samples() default 100;

    /**
     * Minimum pass rate required for overall test success.
     * Value must be in range [0.0, 1.0].
     *
     * <p>The test passes if and only if:
     * {@code (successes / samplesExecuted) >= minPassRate}
     *
     * @return the minimum required pass rate (0.0 to 1.0)
     */
    double minPassRate() default 0.95;
}

