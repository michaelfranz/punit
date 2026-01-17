package org.javai.punit.spec.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Optional;
import org.javai.punit.api.UseCaseContract;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CriteriaResolver")
class CriteriaResolverTest {

    private CriteriaResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CriteriaResolver();
    }

    @Nested
    @DisplayName("resolveCriteriaMethod()")
    class ResolveCriteriaMethod {

        @Test
        @DisplayName("should find valid criteria method")
        void shouldFindValidCriteriaMethod() {
            Optional<Method> method = resolver.resolveCriteriaMethod(ValidUseCase.class);
            
            assertThat(method).isPresent();
            assertThat(method.get().getName()).isEqualTo("criteria");
        }

        @Test
        @DisplayName("should return empty for class without criteria method")
        void shouldReturnEmptyForMissingCriteria() {
            Optional<Method> method = resolver.resolveCriteriaMethod(NoCriteriaUseCase.class);
            
            assertThat(method).isEmpty();
        }

        @Test
        @DisplayName("should return empty for static criteria method")
        void shouldReturnEmptyForStaticCriteria() {
            Optional<Method> method = resolver.resolveCriteriaMethod(StaticCriteriaUseCase.class);
            
            assertThat(method).isEmpty();
        }

        @Test
        @DisplayName("should return empty for wrong parameter type")
        void shouldReturnEmptyForWrongParameterType() {
            Optional<Method> method = resolver.resolveCriteriaMethod(WrongParameterUseCase.class);
            
            assertThat(method).isEmpty();
        }

        @Test
        @DisplayName("should return empty for wrong return type")
        void shouldReturnEmptyForWrongReturnType() {
            Optional<Method> method = resolver.resolveCriteriaMethod(WrongReturnTypeUseCase.class);
            
            assertThat(method).isEmpty();
        }

        @Test
        @DisplayName("should reject null class")
        void shouldRejectNullClass() {
            assertThatThrownBy(() -> resolver.resolveCriteriaMethod(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("useCaseClass");
        }
    }

    @Nested
    @DisplayName("hasCriteriaMethod()")
    class HasCriteriaMethod {

        @Test
        @DisplayName("should return true for valid criteria method")
        void shouldReturnTrueForValidCriteria() {
            assertThat(resolver.hasCriteriaMethod(ValidUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("should return false for missing criteria method")
        void shouldReturnFalseForMissingCriteria() {
            assertThat(resolver.hasCriteriaMethod(NoCriteriaUseCase.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("describeMissingCriteria()")
    class DescribeMissingCriteria {

        @Test
        @DisplayName("should describe missing method")
        void shouldDescribeMissingMethod() {
            String description = resolver.describeMissingCriteria(NoCriteriaUseCase.class);
            
            assertThat(description).contains("No criteria() method found");
            assertThat(description).contains("NoCriteriaUseCase");
        }

        @Test
        @DisplayName("should describe static method issue")
        void shouldDescribeStaticMethodIssue() {
            String description = resolver.describeMissingCriteria(StaticCriteriaUseCase.class);
            
            assertThat(description).contains("static");
            assertThat(description).contains("instance method");
        }

        @Test
        @DisplayName("should describe wrong parameter type")
        void shouldDescribeWrongParameterType() {
            String description = resolver.describeMissingCriteria(WrongParameterUseCase.class);
            
            assertThat(description).contains("signature is wrong");
            assertThat(description).contains("String");
        }

        @Test
        @DisplayName("should describe wrong return type")
        void shouldDescribeWrongReturnType() {
            String description = resolver.describeMissingCriteria(WrongReturnTypeUseCase.class);
            
            assertThat(description).contains("return type is wrong");
            assertThat(description).contains("boolean");
        }
    }

    @Nested
    @DisplayName("UseCaseContract interface support")
    class UseCaseContractSupport {

        @Test
        @DisplayName("should detect class implementing UseCaseContract")
        void shouldDetectImplementingClass() {
            assertThat(resolver.implementsUseCaseContract(ContractUseCase.class)).isTrue();
            assertThat(resolver.implementsUseCaseContract(ValidUseCase.class)).isFalse();
        }

        @Test
        @DisplayName("should get ID from UseCaseContract implementation")
        void shouldGetIdFromContract() {
            ContractUseCase useCase = new ContractUseCase();
            
            assertThat(resolver.getUseCaseId(useCase)).isEqualTo("custom-contract-id");
        }

        @Test
        @DisplayName("should get default ID for non-contract class")
        void shouldGetDefaultIdForNonContract() {
            ValidUseCase useCase = new ValidUseCase();
            
            assertThat(resolver.getUseCaseId(useCase)).isEqualTo("ValidUseCase");
        }

        @Test
        @DisplayName("should get criteria from UseCaseContract implementation")
        void shouldGetCriteriaFromContract() {
            ContractUseCase useCase = new ContractUseCase();
            UseCaseResult result = UseCaseResult.builder()
                    .value("testValue", true)
                    .build();
            
            UseCaseCriteria criteria = resolver.getCriteria(useCase, result);
            
            assertThat(criteria).isNotNull();
            assertThat(criteria.entries()).hasSize(1);
            assertThat(criteria.entries().get(0).getKey()).isEqualTo("Contract criterion");
        }

        @Test
        @DisplayName("should get criteria via reflection for non-contract class")
        void shouldGetCriteriaViaReflection() {
            ValidUseCase useCase = new ValidUseCase();
            UseCaseResult result = UseCaseResult.builder().build();
            
            UseCaseCriteria criteria = resolver.getCriteria(useCase, result);
            
            assertThat(criteria).isNotNull();
            // ValidUseCase returns empty criteria
            assertThat(criteria.entries()).isEmpty();
        }

        @Test
        @DisplayName("should get default criteria when no method defined")
        void shouldGetDefaultCriteriaWhenNoMethod() {
            NoCriteriaUseCase useCase = new NoCriteriaUseCase();
            UseCaseResult result = UseCaseResult.builder().build();
            
            UseCaseCriteria criteria = resolver.getCriteria(useCase, result);
            
            assertThat(criteria).isNotNull();
            assertThat(criteria.entries()).isEmpty();
            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("should prefer interface over reflection")
        void shouldPreferInterfaceOverReflection() {
            // ContractWithReflectionMethod has both interface and reflection-discoverable method
            ContractWithReflectionMethod useCase = new ContractWithReflectionMethod();
            UseCaseResult result = UseCaseResult.builder().build();
            
            UseCaseCriteria criteria = resolver.getCriteria(useCase, result);
            
            // Should use interface method, which has "Interface criterion"
            assertThat(criteria.entries().get(0).getKey()).isEqualTo("Interface criterion");
        }
    }

    // ========== Test Fixtures ==========

    static class ValidUseCase {
        public UseCaseCriteria criteria(UseCaseResult result) {
            return UseCaseCriteria.ordered().build();
        }
    }

    static class NoCriteriaUseCase {
        public void doSomething() {}
    }

    static class StaticCriteriaUseCase {
        public static UseCaseCriteria criteria(UseCaseResult result) {
            return UseCaseCriteria.ordered().build();
        }
    }

    static class WrongParameterUseCase {
        public UseCaseCriteria criteria(String notAResult) {
            return UseCaseCriteria.ordered().build();
        }
    }

    static class WrongReturnTypeUseCase {
        public boolean criteria(UseCaseResult result) {
            return true;
        }
    }

    static class ContractUseCase implements UseCaseContract {
        @Override
        public String useCaseId() {
            return "custom-contract-id";
        }

        @Override
        public UseCaseCriteria criteria(UseCaseResult result) {
            return UseCaseCriteria.ordered()
                    .criterion("Contract criterion", () -> true)
                    .build();
        }
    }

    static class ContractWithReflectionMethod implements UseCaseContract {
        @Override
        public String useCaseId() {
            return "contract-with-reflection";
        }

        // This overrides the interface method (used via interface)
        @Override
        public UseCaseCriteria criteria(UseCaseResult result) {
            return UseCaseCriteria.ordered()
                    .criterion("Interface criterion", () -> true)
                    .build();
        }
    }
}

