package org.javai.punit.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SpecificationRegistry")
class SpecificationRegistryTest {

    @TempDir
    Path tempDir;

    private SpecificationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SpecificationRegistry(tempDir);
    }

    private void createSpecFile(String useCaseId, double minPassRate) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("specId: ").append(useCaseId).append("\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: ").append(minPassRate).append("\n");
        sb.append("schemaVersion: punit-spec-1\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
        
        Files.writeString(tempDir.resolve(useCaseId + ".yaml"), sb.toString());
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
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("loads spec by use case ID")
        void loadsSpecByUseCaseId() throws IOException {
            createSpecFile("TestUseCase", 0.85);

            var spec = registry.resolve("TestUseCase");

            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("strips legacy version suffix")
        void stripsLegacyVersionSuffix() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            var spec = registry.resolve("TestUseCase:v1");

            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
        }

        @Test
        @DisplayName("caches loaded specs")
        void cachesLoadedSpecs() throws IOException {
            createSpecFile("TestUseCase", 0.85);

            var spec1 = registry.resolve("TestUseCase");
            var spec2 = registry.resolve("TestUseCase");

            assertThat(spec1).isSameAs(spec2);
        }

        @Test
        @DisplayName("throws when spec not found")
        void throwsWhenSpecNotFound() {
            assertThatThrownBy(() -> registry.resolve("NonExistent"))
                .isInstanceOf(SpecificationNotFoundException.class)
                .hasMessageContaining("NonExistent");
        }

        @Test
        @DisplayName("throws on null spec ID")
        void throwsOnNullSpecId() {
            assertThatThrownBy(() -> registry.resolve(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("finds .yml files")
        void findsYmlFiles() throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: YmlCase\n");
            sb.append("useCaseId: YmlCase\n");
            sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
            sb.append("approvedBy: tester\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.8\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            Files.writeString(tempDir.resolve("YmlCase.yml"), sb.toString());

            var spec = registry.resolve("YmlCase");

            assertThat(spec.getUseCaseId()).isEqualTo("YmlCase");
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true for existing spec")
        void returnsTrueForExistingSpec() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            assertThat(registry.exists("TestUseCase")).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existing spec")
        void returnsFalseForNonExistingSpec() {
            assertThat(registry.exists("NonExistent")).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(registry.exists(null)).isFalse();
        }

        @Test
        @DisplayName("returns true for cached spec")
        void returnsTrueForCachedSpec() throws IOException {
            createSpecFile("TestUseCase", 0.9);
            registry.resolve("TestUseCase"); // Load into cache

            assertThat(registry.exists("TestUseCase")).isTrue();
        }

        @Test
        @DisplayName("handles legacy version suffix")
        void handlesLegacyVersionSuffix() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            assertThat(registry.exists("TestUseCase:v1")).isTrue();
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCache {

        @Test
        @DisplayName("clears cached specs")
        void clearsCachedSpecs() throws IOException {
            createSpecFile("TestUseCase", 0.85);
            var spec1 = registry.resolve("TestUseCase");
            
            registry.clearCache();
            var spec2 = registry.resolve("TestUseCase");

            assertThat(spec1).isNotSameAs(spec2);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("throws on null specs root")
        void throwsOnNullSpecsRoot() {
            assertThatThrownBy(() -> new SpecificationRegistry(null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}

