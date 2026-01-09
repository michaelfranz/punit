package org.javai.punit.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Provides use case instances for experiments and probabilistic tests.
 *
 * <p>The {@code UseCaseProvider} is the bridge between dependency injection
 * (Spring, Guice, CDI, or manual construction) and PUnit's use case injection.
 * Configure it in {@code @BeforeEach} or {@code @BeforeAll} to specify how
 * use cases should be constructed.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * class ShoppingTest {
 *
 *     @RegisterExtension
 *     UseCaseProvider provider = new UseCaseProvider();
 *
 *     @BeforeEach
 *     void setUp() {
 *         provider.register(ShoppingUseCase.class, () ->
 *             new ShoppingUseCase(new MockShoppingAssistant())
 *         );
 *     }
 *
 *     @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
 *     void testJsonValidity(ShoppingUseCase useCase) {
 *         // useCase is injected by the provider
 *     }
 * }
 * }</pre>
 *
 * <h2>Spring Boot Integration</h2>
 * <pre>{@code
 * @SpringBootTest
 * class ShoppingIntegrationTest {
 *
 *     @Autowired
 *     private OpenAIClient openAIClient;
 *
 *     @RegisterExtension
 *     UseCaseProvider provider = new UseCaseProvider();
 *
 *     @BeforeEach
 *     void setUp() {
 *         // Bridge Spring dependencies to PUnit
 *         provider.register(ShoppingUseCase.class, () ->
 *             new ShoppingUseCase(new OpenAIShoppingAssistant(openAIClient))
 *         );
 *     }
 *
 *     @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
 *     void testWithRealLLM(ShoppingUseCase useCase) {
 *         // Uses real OpenAI client!
 *     }
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Register factories in {@code @BeforeEach}: {@code provider.register(Class, Supplier)}</li>
 *   <li>PUnit reads {@code useCase = Foo.class} from annotation</li>
 *   <li>PUnit asks provider for an instance: {@code provider.getInstance(Foo.class)}</li>
 *   <li>Provider invokes the registered factory</li>
 *   <li>Instance is injected into the test/experiment method</li>
 * </ol>
 *
 * @see UseCase
 * @see ProbabilisticTest#useCase()
 */
public class UseCaseProvider implements ParameterResolver {

    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private boolean useSingletons = false;

    /**
     * Creates a new use case provider with per-invocation instance creation.
     */
    public UseCaseProvider() {
    }

    /**
     * Creates a new use case provider with optional singleton behavior.
     *
     * @param useSingletons if true, each use case class gets one instance per test class
     */
    public UseCaseProvider(boolean useSingletons) {
        this.useSingletons = useSingletons;
    }

    /**
     * Registers a factory for creating instances of a use case class.
     *
     * <p>Call this in {@code @BeforeEach} or {@code @BeforeAll} to configure
     * how use cases should be constructed.
     *
     * @param useCaseClass the use case class
     * @param factory      a supplier that creates instances
     * @param <T>          the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider register(Class<T> useCaseClass, Supplier<T> factory) {
        factories.put(useCaseClass, factory);
        // Clear any cached singleton when re-registering
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Gets an instance of the specified use case class.
     *
     * <p>If singleton mode is enabled, returns the same instance for repeated calls.
     * Otherwise, invokes the factory each time.
     *
     * @param useCaseClass the use case class
     * @param <T>          the use case type
     * @return an instance of the use case
     * @throws IllegalStateException if no factory is registered for the class
     */
    @SuppressWarnings("unchecked")
    public <T> T getInstance(Class<T> useCaseClass) {
        Supplier<?> factory = factories.get(useCaseClass);

        if (factory == null) {
            throw new IllegalStateException(
                    "No factory registered for use case: " + useCaseClass.getName() + ". " +
                    "Register one in @BeforeEach: provider.register(" + useCaseClass.getSimpleName() +
                    ".class, () -> new " + useCaseClass.getSimpleName() + "(...))");
        }

        if (useSingletons) {
            return (T) singletons.computeIfAbsent(useCaseClass, k -> factory.get());
        }

        return (T) factory.get();
    }

    /**
     * Checks if a factory is registered for the specified class.
     *
     * @param useCaseClass the use case class
     * @return true if a factory is registered
     */
    public boolean isRegistered(Class<?> useCaseClass) {
        return factories.containsKey(useCaseClass);
    }

    /**
     * Clears all registered factories and cached singletons.
     */
    public void clear() {
        factories.clear();
        singletons.clear();
    }

    /**
     * Resolves the use case ID from a class.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code @UseCase} annotation present with non-empty value, use that</li>
     *   <li>Otherwise, use the simple class name</li>
     * </ol>
     *
     * @param useCaseClass the use case class
     * @return the resolved ID
     */
    public static String resolveId(Class<?> useCaseClass) {
        UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return useCaseClass.getSimpleName();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JUnit 5 ParameterResolver implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        return isRegistered(paramType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        return getInstance(paramType);
    }
}

