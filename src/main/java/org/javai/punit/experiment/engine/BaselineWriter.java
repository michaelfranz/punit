package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.ExpirationPolicy;

/**
 * Writes empirical baselines to YAML files.
 *
 * <p>Generated specs include:
 * <ul>
 *   <li>{@code schemaVersion} - version identifier for spec format</li>
 *   <li>{@code contentFingerprint} - SHA-256 hash for integrity verification</li>
 * </ul>
 */
public class BaselineWriter {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String SCHEMA_VERSION = "punit-spec-1";
    
    /**
     * Writes a baseline to the specified path in YAML format.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void write(EmpiricalBaseline baseline, Path path) throws IOException {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(path, "path must not be null");
        
        // Ensure parent directories exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        String content = toYaml(baseline);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    
    /**
     * Writes a baseline to YAML format.
     *
     * <p>The generated YAML includes:
     * <ul>
     *   <li>{@code schemaVersion} - version identifier for spec format</li>
     *   <li>{@code contentFingerprint} - SHA-256 hash for integrity verification</li>
     * </ul>
     *
     * @param baseline the baseline
     * @return YAML string
     */
    public String toYaml(EmpiricalBaseline baseline) {
        // Build the content without fingerprint first
        String contentWithoutFingerprint = buildYamlContent(baseline);
        
        // Compute fingerprint of the content
        String fingerprint = computeFingerprint(contentWithoutFingerprint);
        
        // Build final content with fingerprint at the end
        // Note: contentWithoutFingerprint already ends with \n, so no extra newline needed
        StringBuilder sb = new StringBuilder(contentWithoutFingerprint);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Builds the YAML content (without the fingerprint line).
     */
    private String buildYamlContent(EmpiricalBaseline baseline) {
        YamlBuilder builder = YamlBuilder.create()
            .comment("Empirical Baseline for " + baseline.getUseCaseId())
            .comment("Generated automatically by punit experiment runner")
            .comment("DO NOT EDIT - create a specification based on this baseline instead")
            .blankLine()
            .field("schemaVersion", SCHEMA_VERSION)
            .field("useCaseId", baseline.getUseCaseId())
            .fieldIfPresent("experimentId", baseline.getExperimentId())
            .field("generatedAt", ISO_FORMATTER.format(baseline.getGeneratedAt()))
            .fieldIfPresent("experimentClass", baseline.getExperimentClass())
            .fieldIfPresent("experimentMethod", baseline.getExperimentMethod());

        // Footprint
        if (baseline.hasFootprint()) {
            builder.field("footprint", baseline.getFootprint());
        }

        // Covariates
        if (baseline.hasCovariates()) {
            builder.startObject("covariates");
            CovariateProfile profile = baseline.getCovariateProfile();
            for (String key : profile.orderedKeys()) {
                CovariateValue value = profile.get(key);
                builder.field(key, value.toCanonicalString());
            }
            builder.endObject();
        }

        // Execution
        builder.startObject("execution")
            .field("samplesPlanned", baseline.getExecution().samplesPlanned())
            .field("samplesExecuted", baseline.getExecution().samplesExecuted())
            .field("terminationReason", baseline.getExecution().terminationReason().toString())
            .fieldIfPresent("terminationDetails", baseline.getExecution().terminationDetails())
            .endObject();

        // Requirements (derived from empirical data)
        // minPassRate is set to the lower bound of the 95% confidence interval
        double derivedMinPassRate = baseline.getStatistics().confidenceIntervalLower();
        builder.startObject("requirements")
            .field("minPassRate", derivedMinPassRate, "%.4f")
            .endObject();

        // Statistics
        builder.startObject("statistics")
            .startObject("successRate")
                .field("observed", baseline.getStatistics().observedSuccessRate(), "%.4f")
                .field("standardError", baseline.getStatistics().standardError(), "%.4f")
                .formattedInlineArray("confidenceInterval95", "%.4f",
                    baseline.getStatistics().confidenceIntervalLower(),
                    baseline.getStatistics().confidenceIntervalUpper())
            .endObject()
            .field("successes", baseline.getStatistics().successes())
            .field("failures", baseline.getStatistics().failures());

        if (!baseline.getStatistics().failureDistribution().isEmpty()) {
            builder.startObject("failureDistribution");
            for (var entry : baseline.getStatistics().failureDistribution().entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        builder.endObject();

        // Cost
        builder.startObject("cost")
            .field("totalTimeMs", baseline.getCost().totalTimeMs())
            .field("avgTimePerSampleMs", baseline.getCost().avgTimePerSampleMs())
            .field("totalTokens", baseline.getCost().totalTokens())
            .field("avgTokensPerSample", baseline.getCost().avgTokensPerSample())
            .endObject();

        // Success criteria
        if (baseline.getUseCaseCriteria() != null) {
            builder.startObject("successCriteria")
                .field("definition", baseline.getUseCaseCriteria())
                .endObject();
        }

        // Result projections (EXPLORE mode only)
        if (baseline.hasResultProjections()) {
            builder.startObject("resultProjection");
            for (ResultProjection projection : baseline.getResultProjections()) {
                String sampleKey = "sample[" + projection.sampleIndex() + "]";
                builder.startObject(sampleKey)
                    .field("executionTimeMs", projection.executionTimeMs())
                    .startList("diffableContent");
                for (String line : projection.diffableLines()) {
                    builder.listItem(line);
                }
                builder.endList().endObject();
            }
            builder.endObject();
        }

        // Expiration policy
        if (baseline.hasExpirationPolicy()) {
            ExpirationPolicy policy = baseline.getExpirationPolicy();
            builder.startObject("expiration")
                .field("expiresInDays", policy.expiresInDays())
                .field("baselineEndTime", ISO_FORMATTER.format(policy.baselineEndTime()));
            policy.expirationTime().ifPresent(exp ->
                builder.field("expirationDate", ISO_FORMATTER.format(exp)));
            builder.endObject();
        }

        return builder.build();
    }
    
    /**
     * Computes a SHA-256 fingerprint of the content.
     */
    private String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
}
