package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.outcome.Covariate;
import org.javai.outcome.Region;
import org.javai.outcome.TimeOfDay;
import org.javai.outcome.Timezone;
import org.javai.outcome.WeekdayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StandardCovariate}.
 */
@DisplayName("StandardCovariate")
class StandardCovariateTest {

    @Nested
    @DisplayName("key()")
    class KeyTests {

        @Test
        @DisplayName("WEEKDAY_VERSUS_WEEKEND should return 'weekday_vs_weekend'")
        void weekdayVsWeekendKey() {
            assertThat(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key())
                .isEqualTo("weekday_vs_weekend");
        }

        @Test
        @DisplayName("TIME_OF_DAY should return 'time_of_day'")
        void timeOfDayKey() {
            assertThat(StandardCovariate.TIME_OF_DAY.key())
                .isEqualTo("time_of_day");
        }

        @Test
        @DisplayName("TIMEZONE should return 'timezone'")
        void timezoneKey() {
            assertThat(StandardCovariate.TIMEZONE.key())
                .isEqualTo("timezone");
        }

        @Test
        @DisplayName("REGION should return 'region'")
        void regionKey() {
            assertThat(StandardCovariate.REGION.key())
                .isEqualTo("region");
        }
    }

    @Nested
    @DisplayName("Stability")
    class StabilityTests {

        @Test
        @DisplayName("enum values should be stable for serialization")
        void enumValuesShouldBeStable() {
            // These ordinal values should never change (serialization compatibility)
            assertThat(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.ordinal()).isEqualTo(0);
            assertThat(StandardCovariate.TIME_OF_DAY.ordinal()).isEqualTo(1);
            assertThat(StandardCovariate.TIMEZONE.ordinal()).isEqualTo(2);
            assertThat(StandardCovariate.REGION.ordinal()).isEqualTo(3);
        }

        @Test
        @DisplayName("should have exactly 4 standard covariates")
        void shouldHaveExactlyFourCovariates() {
            assertThat(StandardCovariate.values()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("WEEKDAY_VERSUS_WEEKEND should resolve to WeekdayType")
        void weekdayVsWeekendResolvesToWeekdayType() {
            Covariate resolved = StandardCovariate.WEEKDAY_VERSUS_WEEKEND.resolve();

            assertThat(resolved).isInstanceOf(WeekdayType.class);
            assertThat(resolved.name()).isEqualTo("weekday_type");
            // Should be either weekday or weekend based on current date
            WeekdayType weekdayType = (WeekdayType) resolved;
            assertThat(weekdayType).isIn(WeekdayType.weekday(), WeekdayType.weekend());
        }

        @Test
        @DisplayName("TIME_OF_DAY should resolve to TimeOfDay with current hour")
        void timeOfDayResolvesToTimeOfDay() {
            Covariate resolved = StandardCovariate.TIME_OF_DAY.resolve();

            assertThat(resolved).isInstanceOf(TimeOfDay.class);
            assertThat(resolved.name()).isEqualTo("time_of_day");
            TimeOfDay timeOfDay = (TimeOfDay) resolved;
            // Hour should be in valid range
            assertThat(timeOfDay.fromHour()).isBetween(0, 23);
            assertThat(timeOfDay.toHour()).isBetween(0, 23);
        }

        @Test
        @DisplayName("TIMEZONE should resolve to Timezone with system default")
        void timezoneResolvesToTimezone() {
            Covariate resolved = StandardCovariate.TIMEZONE.resolve();

            assertThat(resolved).isInstanceOf(Timezone.class);
            assertThat(resolved.name()).isEqualTo("timezone");
            Timezone timezone = (Timezone) resolved;
            assertThat(timezone.zoneId()).isEqualTo(java.time.ZoneId.systemDefault());
        }

        @Test
        @DisplayName("REGION should resolve to Region")
        void regionResolvesToRegion() {
            Covariate resolved = StandardCovariate.REGION.resolve();

            assertThat(resolved).isInstanceOf(Region.class);
            assertThat(resolved.name()).isEqualTo("region");
            // Default should be "unknown" if no system property or env var is set
            Region region = (Region) resolved;
            assertThat(region.value()).isNotBlank();
        }

        @Test
        @DisplayName("all StandardCovariates should implement CovariateResolver")
        void allStandardCovariatesImplementResolver() {
            for (StandardCovariate covariate : StandardCovariate.values()) {
                assertThat(covariate).isInstanceOf(CovariateResolver.class);
                Covariate resolved = covariate.resolve();
                assertThat(resolved).isNotNull();
            }
        }

        @Test
        @DisplayName("resolved covariates should have matching category semantics")
        void resolvedCovariatesShouldHaveMatchingCategories() {
            // TEMPORAL covariates
            assertThat(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.resolve().category())
                    .isEqualTo(org.javai.outcome.CovariateCategory.TEMPORAL);
            assertThat(StandardCovariate.TIME_OF_DAY.resolve().category())
                    .isEqualTo(org.javai.outcome.CovariateCategory.TEMPORAL);

            // OPERATIONAL covariates
            assertThat(StandardCovariate.TIMEZONE.resolve().category())
                    .isEqualTo(org.javai.outcome.CovariateCategory.OPERATIONAL);
            assertThat(StandardCovariate.REGION.resolve().category())
                    .isEqualTo(org.javai.outcome.CovariateCategory.OPERATIONAL);
        }
    }
}

