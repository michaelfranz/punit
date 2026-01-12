package org.javai.punit.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;

/**
 * Invokes success criteria methods on use case instances with robust exception handling.
 *
 * <p>The invoker ensures that a {@link UseCaseCriteria} is always returned,
 * even when the criteria method itself throws an exception. This guarantees that
 * experiments can always capture meaningful outcome information.
 *
 * <h2>Exception Handling Strategy</h2>
 * <ul>
 *   <li>{@link Error} subclasses are always propagated (JVM-level issues)</li>
 *   <li>All other throwables are wrapped in a "construction failed" criteria</li>
 * </ul>
 *
 * @see CriteriaResolver
 * @see UseCaseCriteria
 */
public class CriteriaInvoker {

    private final CriteriaResolver resolver;

    /**
     * Creates a new invoker with the default resolver.
     */
    public CriteriaInvoker() {
        this(new CriteriaResolver());
    }

    /**
     * Creates a new invoker with a custom resolver.
     *
     * @param resolver the resolver to use for finding criteria methods
     */
    public CriteriaInvoker(CriteriaResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * Invokes the criteria method on a use case instance.
     *
     * <p>If the use case class has a criteria() method, it is invoked with the
     * given result. If no criteria method exists, a default criteria is created
     * based on conventional value keys in the result.
     *
     * <p>This method never throws exceptions (except for {@link Error} subclasses).
     * Any exception during criteria construction or invocation is captured and
     * returned as an "errored" criteria.
     *
     * @param useCaseInstance the use case instance
     * @param result the use case result to evaluate
     * @return a UseCaseCriteria (never null)
     */
    public UseCaseCriteria invoke(Object useCaseInstance, UseCaseResult result) {
        Objects.requireNonNull(useCaseInstance, "useCaseInstance must not be null");
        Objects.requireNonNull(result, "result must not be null");

        Class<?> useCaseClass = useCaseInstance.getClass();
        
        return resolver.resolveCriteriaMethod(useCaseClass)
            .map(method -> invokeMethod(method, useCaseInstance, result))
            .orElseGet(() -> createDefaultCriteria(result));
    }

    /**
     * Invokes the criteria method, handling all exceptions.
     */
    private UseCaseCriteria invokeMethod(Method method, Object instance, UseCaseResult result) {
        try {
            method.setAccessible(true);
            Object returnValue = method.invoke(instance, result);
            
            if (returnValue instanceof UseCaseCriteria criteria) {
                return criteria;
            }
            
            // Unexpected return type - should not happen if resolver is correct
            return UseCaseCriteria.constructionFailed(
                new IllegalStateException("criteria() method returned " + 
                    (returnValue == null ? "null" : returnValue.getClass().getName()) +
                    " instead of UseCaseCriteria"));
                    
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            return UseCaseCriteria.constructionFailed(cause != null ? cause : e);
            
        } catch (IllegalAccessException e) {
            return UseCaseCriteria.constructionFailed(e);
            
        } catch (RuntimeException e) {
            return UseCaseCriteria.constructionFailed(e);
        }
    }

    /**
     * Creates the default (trivial) criteria when a use case has no explicit criteria method.
     *
     * <p>Returns empty criteria that trivially passes. See
     * {@link UseCaseCriteria#defaultCriteria()} for the Design by Contract rationale.
     *
     * @param result the use case result (not used, but kept for method signature consistency)
     * @return default criteria that trivially passes
     */
    private UseCaseCriteria createDefaultCriteria(UseCaseResult result) {
        return UseCaseCriteria.defaultCriteria();
    }

    /**
     * Gets the resolver used by this invoker.
     *
     * @return the criteria resolver
     */
    public CriteriaResolver getResolver() {
        return resolver;
    }
}
