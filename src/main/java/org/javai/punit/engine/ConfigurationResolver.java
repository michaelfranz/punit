package org.javai.punit.engine;

import org.javai.punit.api.ProbabilisticTest;

import java.lang.reflect.Method;

/**
 * Resolves effective configuration for a probabilistic test by merging values from
 * multiple sources with defined precedence.
 *
 * <h2>Precedence (highest to lowest)</h2>
 * <ol>
 *   <li>System property (e.g., {@code -Dpunit.samples=10})</li>
 *   <li>Environment variable (e.g., {@code PUNIT_SAMPLES=10})</li>
 *   <li>Annotation value (e.g., {@code @ProbabilisticTest(samples = 100)})</li>
 *   <li>Framework default</li>
 * </ol>
 *
 * <p>This allows different configurations per environment (PR builds, nightly, local dev)
 * without code changes.
 */
public class ConfigurationResolver {

    // System property names
    public static final String PROP_SAMPLES = "punit.samples";
    public static final String PROP_MIN_PASS_RATE = "punit.minPassRate";
    public static final String PROP_SAMPLES_MULTIPLIER = "punit.samplesMultiplier";

    // Environment variable names
    public static final String ENV_SAMPLES = "PUNIT_SAMPLES";
    public static final String ENV_MIN_PASS_RATE = "PUNIT_MIN_PASS_RATE";
    public static final String ENV_SAMPLES_MULTIPLIER = "PUNIT_SAMPLES_MULTIPLIER";

    // Framework defaults
    public static final int DEFAULT_SAMPLES = 100;
    public static final double DEFAULT_MIN_PASS_RATE = 0.95;
    public static final double DEFAULT_SAMPLES_MULTIPLIER = 1.0;

    /**
     * Resolves the effective configuration for a test method.
     *
     * @param testMethod the test method with @ProbabilisticTest annotation
     * @return the resolved configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public ResolvedConfiguration resolve(Method testMethod) {
        ProbabilisticTest annotation = testMethod.getAnnotation(ProbabilisticTest.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Method " + testMethod.getName() + " is not annotated with @ProbabilisticTest");
        }

        return resolve(annotation, testMethod.getName());
    }

    /**
     * Resolves the effective configuration from an annotation.
     *
     * @param annotation the @ProbabilisticTest annotation
     * @param contextName name for error messages (e.g., method name)
     * @return the resolved configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public ResolvedConfiguration resolve(ProbabilisticTest annotation, String contextName) {
        // Resolve samples
        int baseSamples = resolveInt(
                PROP_SAMPLES, ENV_SAMPLES,
                annotation.samples(),
                DEFAULT_SAMPLES);

        // Validate base samples before multiplier (explicit 0 or negative should fail)
        if (baseSamples <= 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": samples must be >= 1, but resolved to " + baseSamples);
        }

        // Apply multiplier
        double multiplier = resolveDouble(
                PROP_SAMPLES_MULTIPLIER, ENV_SAMPLES_MULTIPLIER,
                DEFAULT_SAMPLES_MULTIPLIER,
                DEFAULT_SAMPLES_MULTIPLIER);

        int effectiveSamples = (int) Math.round(baseSamples * multiplier);
        // Ensure at least 1 sample after multiplier (small multiplier shouldn't reduce below 1)
        effectiveSamples = Math.max(1, effectiveSamples);

        // Resolve minPassRate
        double minPassRate = resolveDouble(
                PROP_MIN_PASS_RATE, ENV_MIN_PASS_RATE,
                annotation.minPassRate(),
                DEFAULT_MIN_PASS_RATE);

        // Validate minPassRate
        validateMinPassRate(minPassRate, contextName);

        return new ResolvedConfiguration(effectiveSamples, minPassRate, multiplier);
    }

    /**
     * Resolves an integer value with precedence: system prop > env var > annotation > default.
     */
    private int resolveInt(String sysProp, String envVar, int annotationValue, int defaultValue) {
        // 1. System property (highest priority)
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Integer.parseInt(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        // 2. Environment variable
        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        // 3. Annotation value (if not default)
        // Note: We can't easily detect if annotation used default, so we always use annotation value
        return annotationValue;
    }

    /**
     * Resolves a double value with precedence: system prop > env var > annotation > default.
     */
    private double resolveDouble(String sysProp, String envVar, double annotationValue, double defaultValue) {
        // 1. System property (highest priority)
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Double.parseDouble(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        // 2. Environment variable
        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Double.parseDouble(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        // 3. Annotation value
        return annotationValue;
    }

    /**
     * Gets an environment variable. Extracted for testability.
     */
    protected String getEnvironmentVariable(String name) {
        return System.getenv(name);
    }

    /**
     * Validates the minPassRate configuration.
     */
    private void validateMinPassRate(double minPassRate, String contextName) {
        if (minPassRate < 0.0 || minPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": minPassRate must be in range [0.0, 1.0], but resolved to " + minPassRate);
        }
    }

    /**
     * Holds the resolved configuration values.
     */
    public record ResolvedConfiguration(
            int samples,
            double minPassRate,
            double appliedMultiplier
    ) {
        /**
         * Returns true if a multiplier was applied (not 1.0).
         */
        public boolean hasMultiplier() {
            return appliedMultiplier != 1.0;
        }
    }
}

