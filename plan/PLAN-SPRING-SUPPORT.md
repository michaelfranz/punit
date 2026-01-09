# Spring Boot Integration Pattern

This document describes how PUnit integrates seamlessly with Spring Boot (and other DI frameworks) without requiring any framework-specific modules.

---

## The Key Insight

**The test/experiment class itself is the bridge between DI and PUnit.**

When a test class is a Spring bean (`@SpringBootTest`), it can:
1. Receive dependencies via `@Autowired`
2. Use those dependencies to configure PUnit's `UseCaseProvider`
3. PUnit then constructs use cases using the provided factory

**Result**: PUnit stays framework-agnostic while supporting any DI framework.

---

## Spring Boot Example

```java
@SpringBootTest
class ShoppingExperiment {
    
    // ═══════════════════════════════════════════════════════════════
    // SPRING INJECTS THESE
    // ═══════════════════════════════════════════════════════════════
    
    @Autowired
    private OpenAIClient openAIClient;
    
    @Autowired
    private ProductRepository productRepo;
    
    @Autowired
    private MetricsService metrics;
    
    // ═══════════════════════════════════════════════════════════════
    // PUNIT USE CASE PROVIDER
    // ═══════════════════════════════════════════════════════════════
    
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();
    
    @BeforeEach
    void setUp() {
        // Bridge: Spring dependencies → PUnit use case construction
        provider.register(ShoppingUseCase.class, () -> 
            new ShoppingUseCase(
                new OpenAIShoppingAssistant(openAIClient, productRepo),
                metrics
            )
        );
    }
    
    // ═══════════════════════════════════════════════════════════════
    // EXPERIMENT (uses injected use case)
    // ═══════════════════════════════════════════════════════════════
    
    @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
    void measureProductSearchBaseline(ShoppingUseCase useCase) {
        // useCase was constructed with real Spring-managed OpenAIClient!
        useCase.searchProducts("wireless headphones", context);
    }
}
```

---

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SPRING CONTEXT                                    │
│                                                                             │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│   │ OpenAIClient │    │ ProductRepo  │    │ MetricsService│                │
│   └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
│          │                   │                   │                          │
│          └───────────────────┼───────────────────┘                          │
│                              │ @Autowired                                   │
│                              ▼                                              │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                    ShoppingExperiment                                 │  │
│   │                                                                       │  │
│   │   @BeforeEach                                                         │  │
│   │   void setUp() {                                                      │  │
│   │       provider.register(ShoppingUseCase.class, () ->                  │  │
│   │           new ShoppingUseCase(openAIClient, productRepo, metrics));   │  │
│   │   }                                                                   │  │
│   │                                                                       │  │
│   │   @Experiment(useCase = ShoppingUseCase.class)                        │  │
│   │   void measure(ShoppingUseCase useCase) { ... }                       │  │
│   │              ▲                                                        │  │
│   └──────────────│────────────────────────────────────────────────────────┘  │
│                  │                                                           │
└──────────────────│───────────────────────────────────────────────────────────┘
                   │
                   │ PUnit injects via provider
                   │
        ┌──────────┴──────────┐
        │   UseCaseProvider   │
        │                     │
        │  "ShoppingUseCase"  │──────▶ Factory lambda from @BeforeEach
        │        ↓            │
        │  new ShoppingUseCase│
        └─────────────────────┘
```

---

## Why This Works

1. **Spring owns the lifecycle** of the test class and its dependencies
2. **PUnit owns the use case lifecycle** via `UseCaseProvider`
3. **The test class bridges them** in `@BeforeEach`

No special Spring module needed. No classpath scanning. No magic.

---

## Comparison: Mock vs Real

### Unit Test with Mocks (No Spring)

```java
class ShoppingExperimentTest {
    
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();
    
    @BeforeEach
    void setUp() {
        // Pure mock - no Spring needed
        provider.register(ShoppingUseCase.class, () -> 
            new ShoppingUseCase(
                new MockShoppingAssistant(MockConfiguration.experimentRealistic())
            )
        );
    }
    
    @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
    void testJsonValidity(ShoppingUseCase useCase) {
        // Uses mock
    }
}
```

### Integration Test with Spring

```java
@SpringBootTest
class ShoppingIntegrationTest {
    
    @Autowired
    private OpenAIClient realClient;
    
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();
    
    @BeforeEach
    void setUp() {
        provider.register(ShoppingUseCase.class, () -> 
            new ShoppingUseCase(new OpenAIShoppingAssistant(realClient))
        );
    }
    
    @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
    void testJsonValidity(ShoppingUseCase useCase) {
        // Uses real OpenAI!
    }
}
```

**Same PUnit API. Different provider configuration.**

---

## Other DI Frameworks

The same pattern works with any DI framework:

### Google Guice

```java
class ShoppingExperiment {
    
    @Inject
    private OpenAIClient client;
    
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();
    
    @BeforeEach
    void setUp() {
        provider.register(ShoppingUseCase.class, () -> 
            new ShoppingUseCase(new OpenAIShoppingAssistant(client))
        );
    }
}
```

### CDI (Jakarta EE)

```java
class ShoppingExperiment {
    
    @Inject
    private OpenAIClient client;
    
    // Same pattern...
}
```

---

## Benefits of This Approach

| Aspect | Benefit |
|--------|---------|
| **No PUnit-Spring module** | Less maintenance, no version coupling |
| **Framework agnostic** | Works with Spring, Guice, CDI, or nothing |
| **Explicit wiring** | Clear what dependencies are used |
| **Testable** | Easy to swap real → mock in different contexts |
| **IDE friendly** | Navigate from provider registration to use case |

---

## Implementation Notes

The `UseCaseProvider` needs to:

1. Support registration: `provider.register(Class<T>, Supplier<T>)`
2. Support lookup: `provider.getInstance(Class<T>)`
3. Be a JUnit 5 extension (to participate in lifecycle)
4. Handle the case where no factory is registered (error with helpful message)

See: `src/main/java/org/javai/punit/experiment/spi/UseCaseProvider.java`

---

*Created: 2026-01-08*
*Status: Design approved, implementation pending*

