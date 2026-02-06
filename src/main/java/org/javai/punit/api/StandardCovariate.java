package org.javai.punit.api;

import java.time.LocalTime;
import org.javai.outcome.Covariate;
import org.javai.outcome.Region;
import org.javai.outcome.TimeOfDay;
import org.javai.outcome.Timezone;
import org.javai.outcome.WeekdayType;

/**
 * Standard covariates provided by PUnit for common contextual factors.
 *
 * <p>Covariates are contextual factors that may influence use case performance.
 * Declaring covariates enables PUnit to:
 * <ul>
 *   <li>Capture environmental conditions during MEASURE experiments</li>
 *   <li>Match baselines to compatible test execution contexts</li>
 *   <li>Warn when test conditions don't conform to baseline conditions</li>
 * </ul>
 *
 * <p>Each standard covariate has built-in resolution via {@link #resolve()} that
 * returns an Outcome {@link Covariate} with the current runtime value.
 *
 * @see UseCase#covariates()
 * @see CovariateResolver
 */
public enum StandardCovariate implements CovariateResolver {

    /**
     * Weekday vs weekend classification.
     *
     * <p><strong>Category:</strong> TEMPORAL
     * <p><strong>Resolution:</strong> Current date → {@link WeekdayType#weekday()} or {@link WeekdayType#weekend()}
     * <p><strong>Matching:</strong> Exact match between baseline and test
     */
    WEEKDAY_VERSUS_WEEKEND("weekday_vs_weekend", CovariateCategory.TEMPORAL) {
        @Override
        public Covariate resolve() {
            return WeekdayType.current();
        }
    },

    /**
     * Time of day window.
     *
     * <p><strong>Category:</strong> TEMPORAL
     * <p><strong>Resolution:</strong> Current hour as a {@link TimeOfDay} range
     * <p><strong>Matching:</strong> Current time falls within baseline's recorded interval
     *
     * <p>The resolved value represents the current hour as a single-hour window.
     */
    TIME_OF_DAY("time_of_day", CovariateCategory.TEMPORAL) {
        @Override
        public Covariate resolve() {
            int currentHour = LocalTime.now().getHour();
            // Represent current hour as a 1-hour window
            int nextHour = (currentHour + 1) % 24;
            return new TimeOfDay(currentHour, nextHour);
        }
    },

    /**
     * System timezone.
     *
     * <p><strong>Category:</strong> OPERATIONAL
     * <p><strong>Resolution:</strong> System default timezone via {@link Timezone#system()}
     * <p><strong>Matching:</strong> Exact string match
     */
    TIMEZONE("timezone", CovariateCategory.OPERATIONAL) {
        @Override
        public Covariate resolve() {
            return Timezone.system();
        }
    },

    /**
     * Deployment region.
     *
     * <p><strong>Category:</strong> OPERATIONAL
     * <p><strong>Resolution:</strong> System property {@code punit.region} or
     * environment variable {@code PUNIT_REGION}, defaults to "unknown"
     * <p><strong>Matching:</strong> Case-insensitive string match
     */
    REGION("region", CovariateCategory.OPERATIONAL) {
        @Override
        public Covariate resolve() {
            String region = System.getProperty("punit.region",
                    System.getenv().getOrDefault("PUNIT_REGION", "unknown"));
            return new Region(region);
        }
    };

    private final String key;
    private final CovariateCategory category;

    StandardCovariate(String key, CovariateCategory category) {
        this.key = key;
        this.category = category;
    }

    /**
     * Returns the stable string key used in baseline specs.
     *
     * <p>This key is used for:
     * <ul>
     *   <li>YAML serialization in spec files</li>
     *   <li>Footprint computation</li>
     *   <li>Covariate profile storage</li>
     * </ul>
     *
     * @return the stable string key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the category of this covariate.
     *
     * <p>The category determines matching behavior:
     * <ul>
     *   <li>TEMPORAL/INFRASTRUCTURE: Soft match with warnings</li>
     *   <li>CONFIGURATION: Hard gate (not applicable to standard covariates)</li>
     *   <li>INFORMATIONAL: Ignored in matching</li>
     * </ul>
     *
     * @return the covariate category
     * @see CovariateCategory
     */
    public CovariateCategory category() {
        return category;
    }
}

