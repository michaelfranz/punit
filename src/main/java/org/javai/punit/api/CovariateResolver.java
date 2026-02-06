package org.javai.punit.api;

import org.javai.outcome.Covariate;

/**
 * Resolves a covariate declaration to its current runtime value.
 *
 * <p>This interface bridges PUnit's declaration-based covariate model with
 * Outcome's value-based model. Implementations capture the current environmental
 * state and return an Outcome {@link Covariate} representing that state.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // StandardCovariate implements CovariateResolver
 * Covariate current = StandardCovariate.TIMEZONE.resolve();
 * // Returns Timezone with current system timezone
 *
 * Covariate weekday = StandardCovariate.WEEKDAY_VERSUS_WEEKEND.resolve();
 * // Returns WeekdayType.weekday() or WeekdayType.weekend()
 * }</pre>
 *
 * <h2>Resolution Timing</h2>
 * <p>Resolution happens at the time {@link #resolve()} is called. For temporal
 * covariates, this means the value reflects the current moment. Callers should
 * be aware that repeated calls may return different values.
 *
 * @see StandardCovariate
 * @see org.javai.outcome.Covariate
 */
@FunctionalInterface
public interface CovariateResolver {

    /**
     * Resolves the current value of this covariate.
     *
     * <p>The returned Outcome covariate contains both the value and its
     * category, enabling proper matching semantics.
     *
     * @return the resolved Outcome {@link Covariate} with its current value
     */
    Covariate resolve();
}
