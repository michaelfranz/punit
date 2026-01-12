package org.javai.punit.engine;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.api.UseCaseContract;
import org.javai.punit.model.UseCaseResult;
import org.javai.punit.model.UseCaseCriteria;

/**
 * Discovers and resolves success criteria for use case classes.
 *
 * <h2>Resolution Strategy</h2>
 * <p>The resolver uses a two-phase approach:
 * <ol>
 *   <li><strong>Interface check</strong>: If the use case implements {@link UseCaseContract},
 *       criteria can be obtained directly via the interface method</li>
 *   <li><strong>Reflection fallback</strong>: Otherwise, look for a method with signature:
 *       {@code public UseCaseCriteria criteria(UseCaseResult result)}</li>
 * </ol>
 *
 * <p>Using {@link UseCaseContract} is preferred because:
 * <ul>
 *   <li>IDEs recognize interface implementations (no "unused method" warnings)</li>
 *   <li>No reflection overhead at runtime</li>
 *   <li>Compile-time type safety</li>
 * </ul>
 *
 * @see UseCaseContract
 * @see UseCaseCriteria
 */
public class CriteriaResolver {

    /**
     * The canonical criteria method from {@link UseCaseContract}.
     * Derived from the interface to ensure refactoring safety.
     */
    private static final Method INTERFACE_CRITERIA_METHOD;
    
    /**
     * The criteria method name, derived from the interface method.
     */
    private static final String CRITERIA_METHOD_NAME;
    
    /**
     * The parameter types for the criteria method, derived from the interface.
     */
    private static final Class<?>[] CRITERIA_PARAMETER_TYPES;
    
    static {
        try {
            // Derive method signature from the interface - refactoring safe
            INTERFACE_CRITERIA_METHOD = UseCaseContract.class.getMethod(
                    "criteria", UseCaseResult.class);
            CRITERIA_METHOD_NAME = INTERFACE_CRITERIA_METHOD.getName();
            CRITERIA_PARAMETER_TYPES = INTERFACE_CRITERIA_METHOD.getParameterTypes();
        } catch (NoSuchMethodException e) {
            // This would indicate a breaking change to UseCaseContract
            throw new AssertionError(
                    "UseCaseContract.criteria(UseCaseResult) method not found. " +
                    "This indicates a breaking change to the interface.", e);
        }
    }

    /**
     * Checks if a use case class implements the {@link UseCaseContract} interface.
     *
     * <p>Classes implementing this interface can have their criteria resolved
     * without reflection, providing better IDE integration and performance.
     *
     * @param useCaseClass the use case class to inspect
     * @return true if the class implements UseCaseContract
     */
    public boolean implementsUseCaseContract(Class<?> useCaseClass) {
        Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");
        return UseCaseContract.class.isAssignableFrom(useCaseClass);
    }

    /**
     * Gets the use case ID from a use case instance.
     *
     * <p>If the instance implements {@link UseCaseContract}, the ID is obtained
     * directly from the interface method. Otherwise, falls back to the simple
     * class name.
     *
     * @param useCaseInstance the use case instance
     * @return the use case ID
     */
    public String getUseCaseId(Object useCaseInstance) {
        Objects.requireNonNull(useCaseInstance, "useCaseInstance must not be null");
        
        if (useCaseInstance instanceof UseCaseContract contract) {
            return contract.useCaseId();
        }
        return useCaseInstance.getClass().getSimpleName();
    }

    /**
     * Gets criteria for a use case result.
     *
     * <p>If the use case implements {@link UseCaseContract}, criteria are obtained
     * directly from the interface method. Otherwise, falls back to reflection
     * or returns default criteria.
     *
     * @param useCaseInstance the use case instance
     * @param result the use case result
     * @return the criteria for this result
     */
    public UseCaseCriteria getCriteria(Object useCaseInstance, UseCaseResult result) {
        Objects.requireNonNull(useCaseInstance, "useCaseInstance must not be null");
        Objects.requireNonNull(result, "result must not be null");
        
        // Preferred: use interface directly
        if (useCaseInstance instanceof UseCaseContract contract) {
            return contract.criteria(result);
        }
        
        // Fallback: use reflection
        Optional<Method> criteriaMethod = resolveCriteriaMethod(useCaseInstance.getClass());
        if (criteriaMethod.isPresent()) {
            try {
                return (UseCaseCriteria) criteriaMethod.get().invoke(useCaseInstance, result);
            } catch (Exception e) {
                return UseCaseCriteria.constructionFailed(e);
            }
        }
        
        // No criteria defined
        return UseCaseCriteria.defaultCriteria();
    }

    /**
     * Resolves a criteria method from a use case class via reflection.
     *
     * <p><strong>Note:</strong> Prefer checking {@link #implementsUseCaseContract(Class)}
     * first and using {@link #getCriteria(Object, UseCaseResult)} for direct access.
     *
     * @param useCaseClass the use case class to inspect
     * @return an Optional containing the criteria method, or empty if not found
     */
    public Optional<Method> resolveCriteriaMethod(Class<?> useCaseClass) {
        Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");

        try {
            // Use method name and parameter types derived from the interface
            Method method = useCaseClass.getMethod(CRITERIA_METHOD_NAME, CRITERIA_PARAMETER_TYPES);
            
            // Validate return type matches interface
            if (!INTERFACE_CRITERIA_METHOD.getReturnType().isAssignableFrom(method.getReturnType())) {
                return Optional.empty();
            }
            
            // Validate it's not static
            if (Modifier.isStatic(method.getModifiers())) {
                return Optional.empty();
            }
            
            return Optional.of(method);
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if a use case class has an explicit criteria method.
     *
     * @param useCaseClass the use case class to inspect
     * @return true if the class has a valid criteria method
     */
    public boolean hasCriteriaMethod(Class<?> useCaseClass) {
        return resolveCriteriaMethod(useCaseClass).isPresent();
    }

    /**
     * Describes why a criteria method was not found (for diagnostics).
     *
     * @param useCaseClass the use case class that was inspected
     * @return a description of why no criteria method was found
     */
    public String describeMissingCriteria(Class<?> useCaseClass) {
        Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");

        // Check if there's a method matching the interface method name
        for (Method method : useCaseClass.getMethods()) {
            if (CRITERIA_METHOD_NAME.equals(method.getName())) {
                // Found a method with matching name - explain why it doesn't match
                if (Modifier.isStatic(method.getModifiers())) {
                    return "Found " + CRITERIA_METHOD_NAME + "() method but it is static; must be an instance method";
                }
                
                Class<?>[] paramTypes = method.getParameterTypes();
                if (!java.util.Arrays.equals(paramTypes, CRITERIA_PARAMETER_TYPES)) {
                    return "Found " + CRITERIA_METHOD_NAME + "() method but signature is wrong; " +
                           "expected: " + CRITERIA_METHOD_NAME + "(" + formatParameterTypes(CRITERIA_PARAMETER_TYPES) + 
                           "), found: " + CRITERIA_METHOD_NAME + "(" + formatParameterTypes(paramTypes) + ")";
                }
                
                if (!INTERFACE_CRITERIA_METHOD.getReturnType().isAssignableFrom(method.getReturnType())) {
                    return "Found " + CRITERIA_METHOD_NAME + "() method but return type is wrong; " +
                           "expected: " + INTERFACE_CRITERIA_METHOD.getReturnType().getSimpleName() + 
                           ", found: " + method.getReturnType().getSimpleName();
                }
            }
        }
        
        return "No " + CRITERIA_METHOD_NAME + "() method found on " + useCaseClass.getSimpleName() + 
               "; consider implementing UseCaseContract or using default criteria";
    }

    private String formatParameterTypes(Class<?>[] types) {
        if (types.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].getSimpleName());
        }
        return sb.toString();
    }
}
