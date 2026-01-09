package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import org.javai.punit.experiment.model.ExecutionSpecification;

/**
 * Writes ExecutionSpecification objects to YAML format.
 *
 * <p>The YAML format is compatible with {@link org.javai.punit.spec.registry.SpecificationLoader}
 * and designed to be human-readable and version-control friendly.
 *
 * <h2>Output Format</h2>
 * <p>Specs are written as flat YAML files:
 * <pre>
 * specId: ShoppingUseCase
 * useCaseId: ShoppingUseCase
 * 
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
 * 
 * schemaVersion: punit-spec-1
 * contentFingerprint: a1b2c3...
 * </pre>
 */
public final class SpecificationWriter {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;
    
    /** Current schema version for spec files. */
    public static final String SCHEMA_VERSION = "punit-spec-1";
    
    /** Field name for the content fingerprint (used for tamper detection). */
    public static final String FINGERPRINT_FIELD = "contentFingerprint";

    private SpecificationWriter() {
    }

    /**
     * Writes a specification to a file using the flat directory structure.
     *
     * <p>For use case "ShoppingUseCase", writes to:
     * {@code specsRoot/ShoppingUseCase.yaml}
     *
     * @param spec the specification to write
     * @param specsRoot the root specs directory (e.g., src/test/resources/punit/specs)
     * @throws IOException if writing fails
     */
    public static void writeToRegistry(ExecutionSpecification spec, Path specsRoot) throws IOException {
        String useCaseId = spec.getUseCaseId();
        Path specPath = specsRoot.resolve(useCaseId + ".yaml");
        
        write(spec, specPath);
    }

    /**
     * Writes a specification to a specific file path.
     *
     * @param spec the specification to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public static void write(ExecutionSpecification spec, Path path) throws IOException {
        String yaml = toYaml(spec);
        Files.createDirectories(path.getParent());
        Files.writeString(path, yaml);
    }

    /**
     * Converts a specification to YAML format compatible with SpecificationLoader.
     *
     * @param spec the specification to convert
     * @return the YAML string
     */
    public static String toYaml(ExecutionSpecification spec) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# Execution Specification for ").append(spec.getUseCaseId()).append("\n");
        sb.append("# Auto-generated from empirical baseline\n");
        sb.append("# Format compatible with SpecificationLoader\n");
        sb.append("\n");

        // Core identity
        sb.append("specId: ").append(spec.getUseCaseId()).append("\n");
        sb.append("useCaseId: ").append(spec.getUseCaseId()).append("\n");
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

        // Requirements
        sb.append("requirements:\n");
        if (spec.getThresholds() != null) {
            sb.append("  minPassRate: ").append(formatDouble(spec.getThresholds().getMinSuccessRate())).append("\n");
        } else {
            sb.append("  minPassRate: 0.95\n");
        }
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

        // Execution context (only if it contains meaningful non-default values)
        Map<String, Object> meaningfulContext = filterMeaningfulContext(spec.getContext());
        if (!meaningfulContext.isEmpty()) {
            sb.append("executionContext:\n");
            for (Map.Entry<String, Object> entry : meaningfulContext.entrySet()) {
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
        sb.append("\n");

        // Schema version and content fingerprint (for integrity verification)
        sb.append("schemaVersion: ").append(SCHEMA_VERSION).append("\n");
        
        // Compute fingerprint of content up to this point
        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append(FINGERPRINT_FIELD).append(": ").append(fingerprint).append("\n");

        return sb.toString();
    }
    
    /**
     * Computes a SHA-256 fingerprint of the given content.
     *
     * @param content the content to hash
     * @return the hex-encoded SHA-256 hash
     */
    public static String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
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
    
    /**
     * Filters out default/meaningless context entries.
     * Returns only entries that provide useful information.
     */
    private static Map<String, Object> filterMeaningfulContext(Map<String, Object> context) {
        if (context == null) {
            return Map.of();
        }
        
        Map<String, Object> meaningful = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Skip default backend value
            if ("backend".equals(key) && "generic".equals(value)) {
                continue;
            }
            
            // Skip null or empty values
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            
            meaningful.put(key, value);
        }
        
        return meaningful;
    }
}

