package org.javai.punit.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SpecificationLoader")
class SpecificationLoaderTest {

    // Helper to create valid spec YAML with fingerprint
    private String createValidYaml(String specId, double minPassRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("specId: ").append(specId).append("\n");
        sb.append("useCaseId: ").append(specId).append("\n");
        sb.append("\n");
        sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: ").append(minPassRate).append("\n");
        sb.append("\n");
        sb.append("schemaVersion: punit-spec-1\n");
        
        // Compute fingerprint
        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");
        
        return sb.toString();
    }
    
    private String computeFingerprint(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("parseYaml")
    class ParseYaml {

        @Test
        @DisplayName("parses valid YAML with all required fields")
        void parsesValidYaml() {
            String yaml = createValidYaml("TestUseCase", 0.85);
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            
            assertThat(spec.getSpecId()).isEqualTo("TestUseCase");
            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("parses approval metadata")
        void parsesApprovalMetadata() {
            String yaml = createValidYaml("TestCase", 0.9);
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            
            assertThat(spec.getApprovedBy()).isEqualTo("tester");
            assertThat(spec.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("parses execution context")
        void parsesExecutionContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
            sb.append("approvedBy: tester\n");
            sb.append("executionContext:\n");
            sb.append("  model: gpt-4\n");
            sb.append("  temperature: 0.7\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("model", "gpt-4");
            assertThat(spec.getExecutionContext()).containsEntry("temperature", 0.7);
        }

        @Test
        @DisplayName("parses source baselines list")
        void parsesSourceBaselines() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("sourceBaselines:\n");
            sb.append("  - baseline-1\n");
            sb.append("  - baseline-2\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getSourceBaselines()).containsExactly("baseline-1", "baseline-2");
        }

        @Test
        @DisplayName("parses cost envelope")
        void parsesCostEnvelope() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("costEnvelope:\n");
            sb.append("  maxTimePerSampleMs: 100\n");
            sb.append("  maxTokensPerSample: 500\n");
            sb.append("  totalTokenBudget: 10000\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getCostEnvelope()).isNotNull();
            assertThat(spec.getCostEnvelope().totalTokenBudget()).isEqualTo(10000);
        }

        @Test
        @DisplayName("skips comment lines")
        void skipsCommentLines() {
            StringBuilder sb = new StringBuilder();
            sb.append("# This is a comment\n");
            sb.append("specId: TestCase\n");
            sb.append("# Another comment\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getSpecId()).isEqualTo("TestCase");
        }

        @Test
        @DisplayName("handles quoted string values")
        void handlesQuotedStringValues() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: \"TestCase\"\n");
            sb.append("useCaseId: 'TestCase'\n");
            sb.append("approvalNotes: \"Some notes here\"\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getSpecId()).isEqualTo("TestCase");
            assertThat(spec.getApprovalNotes()).isEqualTo("Some notes here");
        }

        @Test
        @DisplayName("parses version field for backwards compatibility")
        void parsesVersionField() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("version: 2\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("parses boolean values in context")
        void parsesBooleanValuesInContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("executionContext:\n");
            sb.append("  enabled: true\n");
            sb.append("  disabled: false\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("enabled", true);
            assertThat(spec.getExecutionContext()).containsEntry("disabled", false);
        }

        @Test
        @DisplayName("parses integer values in context")
        void parsesIntegerValuesInContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("executionContext:\n");
            sb.append("  count: 42\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("count", 42L);
        }
    }

    @Nested
    @DisplayName("integrity validation")
    class IntegrityValidation {

        @Test
        @DisplayName("throws when schemaVersion is missing")
        void throwsWhenSchemaVersionMissing() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                contentFingerprint: abc123
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Missing schemaVersion");
        }

        @Test
        @DisplayName("throws when schemaVersion is unsupported")
        void throwsWhenSchemaVersionUnsupported() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                schemaVersion: punit-spec-99
                contentFingerprint: abc123
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Unsupported schema version");
        }

        @Test
        @DisplayName("throws when contentFingerprint is missing")
        void throwsWhenFingerprintMissing() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                schemaVersion: punit-spec-1
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Missing contentFingerprint");
        }

        @Test
        @DisplayName("throws when content has been tampered with")
        void throwsWhenContentTampered() {
            // Create valid YAML then modify it
            String validYaml = createValidYaml("TestCase", 0.85);
            String tamperedYaml = validYaml.replace("minPassRate: 0.85", "minPassRate: 0.5");
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(tamperedYaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("fingerprint mismatch");
        }

        @Test
        @DisplayName("accepts valid fingerprint")
        void acceptsValidFingerprint() {
            String yaml = createValidYaml("TestCase", 0.9);
            
            // Should not throw
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            assertThat(spec.getMinPassRate()).isEqualTo(0.9);
        }
    }

    @Nested
    @DisplayName("parseJson")
    class ParseJson {

        @Test
        @DisplayName("parses valid JSON")
        void parsesValidJson() {
            String json = """
                {
                    "specId": "TestCase",
                    "useCaseId": "TestCase",
                    "version": 1,
                    "approvedAt": "2026-01-09T12:00:00Z",
                    "approvedBy": "tester",
                    "minPassRate": 0.85
                }
                """;
            
            ExecutionSpecification spec = SpecificationLoader.parseJson(json);
            
            assertThat(spec.getSpecId()).isEqualTo("TestCase");
            assertThat(spec.getUseCaseId()).isEqualTo("TestCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
            assertThat(spec.getApprovedBy()).isEqualTo("tester");
        }

        @Test
        @DisplayName("uses default version when not specified")
        void usesDefaultVersion() {
            String json = """
                {
                    "specId": "TestCase",
                    "useCaseId": "TestCase"
                }
                """;
            
            ExecutionSpecification spec = SpecificationLoader.parseJson(json);
            
            assertThat(spec.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("uses default minPassRate when not specified")
        void usesDefaultMinPassRate() {
            String json = """
                {
                    "specId": "TestCase",
                    "useCaseId": "TestCase"
                }
                """;
            
            ExecutionSpecification spec = SpecificationLoader.parseJson(json);
            
            assertThat(spec.getMinPassRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("parses success criteria")
        void parsesSuccessCriteria() {
            String json = """
                {
                    "specId": "TestCase",
                    "useCaseId": "TestCase",
                    "successCriteria": "isValid == true"
                }
                """;
            
            ExecutionSpecification spec = SpecificationLoader.parseJson(json);
            
            assertThat(spec.getRequirements().successCriteria()).isEqualTo("isValid == true");
        }
    }
}

