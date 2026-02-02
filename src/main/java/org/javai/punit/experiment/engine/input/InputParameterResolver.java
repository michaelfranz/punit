package org.javai.punit.experiment.engine.input;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides input values for experiment and test methods.
 *
 * <p>Resolves parameters that:
 * <ul>
 *   <li>Match the expected input type</li>
 *   <li>Are not OutcomeCaptor (handled by {@code CaptorParameterResolver})</li>
 *   <li>Are not annotated with @Factor (handled by {@code FactorParameterResolver})</li>
 * </ul>
 *
 * <p>This resolver is used when a method is annotated with {@code @InputSource}
 * to inject the current input value for each invocation.
 */
public class InputParameterResolver implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private final Object inputValue;
    private final Class<?> inputType;

    public InputParameterResolver(Object inputValue, Class<?> inputType) {
        this.inputValue = inputValue;
        this.inputType = inputType;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();

        // Skip OutcomeCaptor - handled by CaptorParameterResolver
        if (paramType.getName().equals("org.javai.punit.api.OutcomeCaptor")) {
            return false;
        }

        // Skip @Factor-annotated parameters - handled by FactorParameterResolver
        if (parameterContext.getParameter().isAnnotationPresent(
                org.javai.punit.api.Factor.class)) {
            return false;
        }

        // Support if parameter type matches the input type
        return paramType.isAssignableFrom(inputType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // Store in the extension context so the interceptor can access it
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put("inputValue", inputValue);
        return inputValue;
    }
}
