package org.javai.punit.experiment.optimize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Generates optimization history YAML files for @OptimizeExperiment.
 *
 * <p>Output directory: {@code src/test/resources/punit/optimizations/{useCaseId}/}
 * <p>Filename pattern: {@code {experimentId}_YYYYMMDD_HHMMSS.yaml}
 *
 * <p>The YAML output includes:
 * <ul>
 *   <li>Identification (useCaseId, experimentId)</li>
 *   <li>Treatment factor configuration</li>
 *   <li>Fixed factors</li>
 *   <li>Optimization settings (objective, scorer, mutator, termination policy)</li>
 *   <li>Timing information</li>
 *   <li>All iterations with their factor values, statistics, and scores</li>
 *   <li>Best iteration identified</li>
 *   <li>Termination reason</li>
 *   <li>Summary statistics (score improvement)</li>
 * </ul>
 */
public class OptimizeSpecGenerator {

    private static final String DEFAULT_OUTPUT_DIR = "src/test/resources/punit/optimizations";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String SCHEMA_VERSION = "punit-optimize-1";

    /**
     * Formats a score (0.0-1.0) as a percentage with 1 decimal place.
     * Example: 0.88 → "88.0%"
     */
    private static String formatAsPercent(double score) {
        return String.format("%.1f%%", score * 100);
    }

    /**
     * Generates the optimization history YAML file.
     *
     * @param context the extension context for publishing reports
     * @param history the complete optimization history
     */
    public void generateSpec(ExtensionContext context, OptimizeHistory history) {
        try {
            Path outputPath = resolveOutputPath(history);
            writeYaml(history, outputPath);

            context.publishReportEntry("punit.optimization.outputPath", outputPath.toString());
            publishFinalReport(context, history);
        } catch (IOException e) {
            context.publishReportEntry("punit.optimization.error", e.getMessage());
        }
    }

    private Path resolveOutputPath(OptimizeHistory history) throws IOException {
        String outputDirOverride = System.getProperty("punit.optimizations.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_OUTPUT_DIR);
        }

        // Create subdirectory for the use case
        Path useCaseDir = baseDir.resolve(sanitizeForFilename(history.useCaseId()));
        Files.createDirectories(useCaseDir);

        // Generate filename with timestamp
        String experimentId = history.experimentId();
        if (experimentId == null || experimentId.isEmpty()) {
            experimentId = "optimization";
        }
        String timestamp = TIMESTAMP_FORMAT.format(history.startTime());
        String filename = sanitizeForFilename(experimentId) + "_" + timestamp + ".yaml";

        return useCaseDir.resolve(filename);
    }

    private void writeYaml(OptimizeHistory history, Path outputPath) throws IOException {
        String content = toYaml(history);
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    /**
     * Converts optimization history to YAML format.
     *
     * @param history the optimization history
     * @return YAML string
     */
    public String toYaml(OptimizeHistory history) {
        String contentWithoutFingerprint = buildYamlContent(history);
        String fingerprint = computeFingerprint(contentWithoutFingerprint);

        StringBuilder sb = new StringBuilder(contentWithoutFingerprint);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String buildYamlContent(OptimizeHistory history) {
        YamlBuilder builder = YamlBuilder.create()
            .comment("Optimization History for " + history.useCaseId())
            .comment("Primary output: the best value for the control factor")
            .comment("Generated automatically by punit @OptimizeExperiment")
            .blankLine()
            .field("schemaVersion", SCHEMA_VERSION)
            .field("useCaseId", history.useCaseId())
            .fieldIfNotEmpty("experimentId", history.experimentId());

        // Control factor
        builder.startObject("controlFactor")
            .field("name", history.controlFactorName())
            .fieldIfPresent("type", history.controlFactorType())
            .endObject();

        // Fixed factors
        if (history.fixedFactors() != null && history.fixedFactors().size() > 0) {
            builder.startObject("fixedFactors");
            for (Map.Entry<String, Object> entry : history.fixedFactors().asMap().entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }

        // Optimization settings
        builder.startObject("optimization")
            .field("objective", history.objective().name())
            .field("scorer", history.scorerDescription())
            .field("mutator", history.mutatorDescription())
            .field("terminationPolicy", history.terminationPolicyDescription())
            .endObject();

        // Timing
        builder.startObject("timing");
        if (history.startTime() != null) {
            builder.field("startTime", ISO_FORMATTER.format(history.startTime()));
        }
        if (history.endTime() != null) {
            builder.field("endTime", ISO_FORMATTER.format(history.endTime()));
        }
        builder.field("totalDurationMs", history.totalDuration().toMillis())
            .endObject();

        // Best iteration (highlighted early for easy access)
        Optional<OptimizationRecord> bestOpt = history.bestIteration();
        if (bestOpt.isPresent()) {
            OptimizationRecord best = bestOpt.get();
            Object bestValue = best.aggregate().controlFactorValue();

            builder.startObject("bestIteration")
                .field("iterationNumber", best.aggregate().iterationNumber())
                .field("score", formatAsPercent(best.score()));

            // Handle multiline control factor values
            if (bestValue instanceof String s && s.contains("\n")) {
                builder.blockScalar("bestControlFactor", s);
            } else {
                builder.field("bestControlFactor", bestValue);
            }

            OptimizeStatistics stats = best.aggregate().statistics();
            builder.startObject("statistics")
                .field("sampleCount", stats.sampleCount())
                .field("successRate", formatAsPercent(stats.successRate()))
                .field("totalTokens", stats.totalTokens())
                .endObject()
                .endObject();
        }

        // Summary
        builder.startObject("summary")
            .field("totalIterations", history.iterationCount())
            .field("totalTokens", history.totalTokens());

        history.initialScore().ifPresent(score ->
            builder.field("initialScore", formatAsPercent(score)));
        history.bestScore().ifPresent(score ->
            builder.field("bestScore", formatAsPercent(score)));

        builder.field("scoreImprovement", formatAsPercent(history.scoreImprovement()))
            .endObject();

        // Termination reason
        if (history.terminationReason() != null) {
            builder.startObject("terminationReason")
                .field("cause", history.terminationReason().cause().name())
                .field("message", history.terminationReason().message())
                .endObject();
        }

        // All iterations
        builder.startList("iterations");
        for (OptimizationRecord record : history.iterations()) {
            appendIteration(builder, record);
        }
        builder.endList();

        return builder.build();
    }

    private void appendIteration(YamlBuilder builder, OptimizationRecord record) {
        OptimizationIterationAggregate agg = record.aggregate();
        Object controlFactorValue = agg.controlFactorValue();

        builder.startListItem()
            .field("iterationNumber", agg.iterationNumber())
            .field("status", record.status().name())
            .field("score", formatAsPercent(record.score()));

        // Handle multiline control factor values
        if (controlFactorValue instanceof String s && s.contains("\n")) {
            builder.blockScalar("controlFactor", s);
        } else {
            builder.field("controlFactor", controlFactorValue);
        }

        // Statistics
        OptimizeStatistics stats = agg.statistics();
        builder.startObject("statistics")
            .field("sampleCount", stats.sampleCount())
            .field("successRate", formatAsPercent(stats.successRate()))
            .field("successCount", stats.successCount())
            .field("failureCount", stats.failureCount())
            .field("totalTokens", stats.totalTokens())
            .field("meanLatencyMs", stats.meanLatencyMs(), "%.2f")
            .endObject();

        record.failureReason().ifPresent(reason ->
            builder.field("failureReason", reason));

        builder.endListItem();
    }

    private String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void publishFinalReport(ExtensionContext context, OptimizeHistory history) {
        context.publishReportEntry("punit.experiment.complete", "true");
        context.publishReportEntry("punit.mode", "OPTIMIZE");
        context.publishReportEntry("punit.useCaseId", history.useCaseId());
        context.publishReportEntry("punit.controlFactor", history.controlFactorName());
        context.publishReportEntry("punit.iterationsCompleted",
                String.valueOf(history.iterationCount()));

        history.bestScore().ifPresent(score ->
                context.publishReportEntry("punit.bestScore", formatAsPercent(score)));

        history.initialScore().ifPresent(score ->
                context.publishReportEntry("punit.initialScore", formatAsPercent(score)));

        context.publishReportEntry("punit.scoreImprovement", formatAsPercent(history.scoreImprovement()));

        if (history.terminationReason() != null) {
            context.publishReportEntry("punit.terminationReason",
                    history.terminationReason().cause().name());
        }

        context.publishReportEntry("punit.totalDurationMs",
                String.valueOf(history.totalDuration().toMillis()));
        context.publishReportEntry("punit.totalTokens",
                String.valueOf(history.totalTokens()));

        // Highlight the best factor value
        history.bestFactorValue().ifPresent(value -> {
            String valueStr = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
            if (valueStr.length() > 100) {
                valueStr = valueStr.substring(0, 100) + "...";
            }
            context.publishReportEntry("punit.bestFactorValue", valueStr);
        });
    }

    private String sanitizeForFilename(String input) {
        if (input == null) return "unnamed";
        return input.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}
