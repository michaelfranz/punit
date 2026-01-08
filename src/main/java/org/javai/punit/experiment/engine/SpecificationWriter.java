package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.javai.punit.experiment.model.ExecutionSpecification;

/**
 * Writes ExecutionSpecification objects to YAML format.
 *
 * <p>The YAML format is compatible with {@link org.javai.punit.spec.registry.SpecificationLoader}
 * and designed to be human-readable and version-control friendly.
 *
 * <h2>Output Format</h2>
 * <p>Specs are written to match the expected loader format:
 * <pre>
 * specId: usecase.shopping.search:v1
 * useCaseId: usecase.shopping.search
 * version: 1
 * approvedAt: 2026-01-08T...
 * approvedBy: developer
 * approvalNotes: "..."
 * 
 * baselineData:
 *   samples: 1000
 *   successes: 983
 *   generatedAt: 2026-01-08T...
 * 
 * requirements:
 *   minPassRate: 0.975
 *   successCriteria: "isValidJson == true"
 * </pre>
 */
public final class SpecificationWriter {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private SpecificationWriter() {
    }

    /**
     * Writes a specification to a file using the standard directory structure.
     *
     * <p>For spec ID "usecase.shopping.search:v1", writes to:
     * {@code specsRoot/usecase.shopping.search/v1.yaml}
     *
     * @param spec the specification to write
     * @param specsRoot the root specs directory (e.g., src/test/resources/punit/specs)
     * @throws IOException if writing fails
     */
    public static void writeToRegistry(ExecutionSpecification spec, Path specsRoot) throws IOException {
        String useCaseId = spec.getUseCaseId();
        int version = 1; // Default version for new specs
        
        // Extract version from provenance if available
        if (spec.getProvenance() != null && spec.getProvenance().getExperimentId() != null) {
            String expId = spec.getProvenance().getExperimentId();
            // Try to extract version from experiment ID like "shopping-search-v1"
            int vIdx = expId.lastIndexOf("-v");
            if (vIdx > 0 && vIdx < expId.length() - 2) {
                try {
                    version = Integer.parseInt(expId.substring(vIdx + 2));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        
        Path specDir = specsRoot.resolve(useCaseId);
        Path specPath = specDir.resolve("v" + version + ".yaml");
        
        write(spec, specPath, version);
    }

    /**
     * Writes a specification to a specific file path.
     *
     * @param spec the specification to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public static void write(ExecutionSpecification spec, Path path) throws IOException {
        write(spec, path, 1);
    }

    /**
     * Writes a specification to a specific file path with explicit version.
     *
     * @param spec the specification to write
     * @param path the output path
     * @param version the spec version number
     * @throws IOException if writing fails
     */
    public static void write(ExecutionSpecification spec, Path path, int version) throws IOException {
        String yaml = toYaml(spec, version);
        Files.createDirectories(path.getParent());
        Files.writeString(path, yaml);
    }

    /**
     * Converts a specification to YAML format compatible with SpecificationLoader.
     *
     * @param spec the specification to convert
     * @param version the version number
     * @return the YAML string
     */
    public static String toYaml(ExecutionSpecification spec, int version) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# Execution Specification for ").append(spec.getUseCaseId()).append("\n");
        sb.append("# Auto-generated from empirical baseline\n");
        sb.append("# Format compatible with SpecificationLoader\n");
        sb.append("\n");

        // Core identity (required by SpecificationLoader)
        String specId = spec.getUseCaseId() + ":v" + version;
        sb.append("specId: ").append(specId).append("\n");
        sb.append("useCaseId: ").append(spec.getUseCaseId()).append("\n");
        sb.append("version: ").append(version).append("\n");
        sb.append("\n");

        // Approval metadata (required by SpecificationLoader)
        if (spec.getApproval() != null) {
            ExecutionSpecification.Approval approval = spec.getApproval();
            sb.append("approvedAt: ").append(formatInstant(approval.getApprovedAt())).append("\n");
            sb.append("approvedBy: ").append(approval.getApprover()).append("\n");
            if (approval.getNotes() != null && !approval.getNotes().isEmpty()) {
                sb.append("approvalNotes: \"").append(escapeYamlString(approval.getNotes())).append("\"\n");
            }
        } else {
            sb.append("approvedAt: ").append(formatInstant(spec.getCreatedAt())).append("\n");
            sb.append("approvedBy: system\n");
        }
        sb.append("\n");

        // Source baselines (for audit trail)
        if (spec.getProvenance() != null) {
            sb.append("sourceBaselines:\n");
            if (spec.getProvenance().getExperimentId() != null) {
                sb.append("  - ").append(spec.getProvenance().getExperimentId()).append("\n");
            } else {
                sb.append("  - ").append(spec.getProvenance().getBaselineId()).append("\n");
            }
            sb.append("\n");
        }

        // Baseline data (for threshold derivation at runtime)
        if (spec.getProvenance() != null) {
            ExecutionSpecification.Provenance prov = spec.getProvenance();
            int samples = prov.getBaselineSamples();
            // Calculate successes from observed rate
            int successes = spec.getThresholds() != null 
                ? (int) Math.round(samples * spec.getThresholds().getObservedSuccessRate())
                : samples;
            
            sb.append("baselineData:\n");
            sb.append("  samples: ").append(samples).append("\n");
            sb.append("  successes: ").append(successes).append("\n");
            if (prov.getBaselineGeneratedAt() != null) {
                sb.append("  generatedAt: ").append(formatInstant(prov.getBaselineGeneratedAt())).append("\n");
            }
            sb.append("\n");
        }

        // Requirements (required by SpecificationLoader)
        sb.append("requirements:\n");
        if (spec.getThresholds() != null) {
            sb.append("  minPassRate: ").append(formatDouble(spec.getThresholds().getMinSuccessRate())).append("\n");
        } else {
            sb.append("  minPassRate: 0.95\n");
        }
        sb.append("  successCriteria: \"isValidJson == true\"\n");
        sb.append("\n");

        // Cost envelope (optional)
        if (spec.getTolerances() != null) {
            ExecutionSpecification.Tolerances tol = spec.getTolerances();
            sb.append("costEnvelope:\n");
            sb.append("  maxTimePerSampleMs: 100\n");
            sb.append("  maxTokensPerSample: 500\n");
            sb.append("  totalTokenBudget: ").append(tol.getRecommendedMaxSamples() * 500).append("\n");
            sb.append("\n");
        }

        // Execution context (optional)
        if (spec.getContext() != null && !spec.getContext().isEmpty()) {
            sb.append("executionContext:\n");
            for (Map.Entry<String, Object> entry : spec.getContext().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ");
                appendYamlValue(sb, entry.getValue());
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Extended metadata (for documentation, not parsed by loader)
        sb.append("# Extended metadata (informational)\n");
        if (spec.getThresholds() != null) {
            ExecutionSpecification.Thresholds thresholds = spec.getThresholds();
            sb.append("# observedSuccessRate: ").append(formatDouble(thresholds.getObservedSuccessRate())).append("\n");
            sb.append("# standardError: ").append(formatDouble(thresholds.getStandardError())).append("\n");
            sb.append("# derivationMethod: ").append(thresholds.getDerivationMethod()).append("\n");
        }
        if (spec.getTolerances() != null) {
            ExecutionSpecification.Tolerances tol = spec.getTolerances();
            sb.append("# recommendedMinSamples: ").append(tol.getRecommendedMinSamples()).append("\n");
            sb.append("# recommendedMaxSamples: ").append(tol.getRecommendedMaxSamples()).append("\n");
        }

        return sb.toString();
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) return "null";
        return ISO_FORMAT.format(instant);
    }

    private static String formatDouble(double value) {
        // Format to 6 decimal places, removing trailing zeros
        String formatted = String.format("%.6f", value);
        // Remove trailing zeros after decimal point
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", ".0");
        }
        return formatted;
    }

    private static String escapeYamlString(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private static void appendYamlValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeYamlString((String) value)).append("\"");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            sb.append("\"").append(escapeYamlString(value.toString())).append("\"");
        }
    }
}

