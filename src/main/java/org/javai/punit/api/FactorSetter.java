package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a setter method on a use case for automatic factor injection.
 *
 * <p>When a use case is registered with {@code UseCaseProvider.registerAutoWired()},
 * the provider automatically invokes annotated setters with factor values.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @UseCase
 * public class ShoppingUseCase {
 *     private String model;
 *     private double temperature;
 *
 *     @FactorSetter("model")
 *     public void setModel(String model) {
 *         this.model = model;
 *     }
 *
 *     @FactorSetter("temp")
 *     public void setTemperature(double temperature) {
 *         this.temperature = temperature;
 *     }
 * }
 * }</pre>
 *
 * <p>With auto-wiring:
 * <pre>{@code
 * provider.registerAutoWired(ShoppingUseCase.class, ShoppingUseCase::new);
 * }</pre>
 *
 * @see UseCaseProvider#registerAutoWired
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorSetter {
    
    /**
     * The name of the factor to inject.
     *
     * <p>Must match a factor name defined in the experiment's
     * {@link org.javai.punit.api.FactorArguments}.
     *
     * @return the factor name
     */
    String value();
}
