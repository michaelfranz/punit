package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.javai.punit.experiment.model.EmpiricalBaseline;

/**
 * Loads empirical baselines from YAML files.
 *
 * <p>This is the inverse of {@link BaselineWriter}.
 */
public final class BaselineLoader {

    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*(\\w+):\\s*(.+?)\\s*$");

    private BaselineLoader() {
    }

    /**
     * Loads a baseline from a YAML file.
     *
     * @param path the file path
     * @return the loaded baseline
     * @throws IOException if loading fails
     */
    public static EmpiricalBaseline load(Path path) throws IOException {
        String content = Files.readString(path);
        return parseYaml(content);
    }

    /**
     * Parses a baseline from YAML content.
     *
     * @param content the YAML content
     * @return the parsed baseline
     */
    public static EmpiricalBaseline parseYaml(String content) {
        EmpiricalBaseline.Builder builder = EmpiricalBaseline.builder();
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Integer> failureDistribution = new LinkedHashMap<>();

        String[] lines = content.split("\n");
        String currentSection = null;
        String currentSubSection = null;

        // Execution summary values
        int samplesPlanned = 0;
        int samplesExecuted = 0;
        String terminationReason = null;
        String terminationDetails = null;

        // Statistics values
        double observedSuccessRate = 0.0;
        double standardError = 0.0;
        double ciLower = 0.0;
        double ciUpper = 0.0;
        int successes = 0;
        int failures = 0;

        // Cost values
        long totalTimeMs = 0;
        long avgTimePerSampleMs = 0;
        long totalTokens = 0;
        long avgTokensPerSample = 0;

        for (String line : lines) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // Detect top-level sections
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                if (line.startsWith("useCaseId:")) {
                    builder.useCaseId(extractValue(line));
                } else if (line.startsWith("experimentId:")) {
                    builder.experimentId(extractValue(line));
                } else if (line.startsWith("generatedAt:")) {
                    builder.generatedAt(parseInstant(extractValue(line)));
                } else if (line.startsWith("experimentClass:")) {
                    builder.experimentClass(extractValue(line));
                } else if (line.startsWith("experimentMethod:")) {
                    builder.experimentMethod(extractValue(line));
                } else if (line.startsWith("context:")) {
                    currentSection = "context";
                } else if (line.startsWith("execution:")) {
                    currentSection = "execution";
                } else if (line.startsWith("statistics:")) {
                    currentSection = "statistics";
                } else if (line.startsWith("cost:")) {
                    currentSection = "cost";
                } else if (line.startsWith("successCriteria:")) {
                    currentSection = "successCriteria";
                }
                continue;
            }

            String trimmed = line.trim();

            // Handle section content
            if ("context".equals(currentSection)) {
                Matcher m = KEY_VALUE.matcher(trimmed);
                if (m.matches()) {
                    context.put(m.group(1), parseValue(m.group(2)));
                }
            } else if ("execution".equals(currentSection)) {
                if (trimmed.startsWith("samplesPlanned:")) {
                    samplesPlanned = Integer.parseInt(extractValue(trimmed));
                } else if (trimmed.startsWith("samplesExecuted:")) {
                    samplesExecuted = Integer.parseInt(extractValue(trimmed));
                } else if (trimmed.startsWith("terminationReason:")) {
                    terminationReason = extractValue(trimmed);
                } else if (trimmed.startsWith("terminationDetails:")) {
                    terminationDetails = extractValue(trimmed);
                }
            } else if ("statistics".equals(currentSection)) {
                if (trimmed.startsWith("successRate:")) {
                    currentSubSection = "successRate";
                } else if (trimmed.startsWith("failureDistribution:")) {
                    currentSubSection = "failureDistribution";
                } else if ("successRate".equals(currentSubSection)) {
                    if (trimmed.startsWith("observed:")) {
                        observedSuccessRate = Double.parseDouble(extractValue(trimmed));
                    } else if (trimmed.startsWith("standardError:")) {
                        standardError = Double.parseDouble(extractValue(trimmed));
                    } else if (trimmed.startsWith("confidenceInterval95:")) {
                        // Parse [lower, upper]
                        String ciValue = extractValue(trimmed);
                        if (ciValue.startsWith("[") && ciValue.endsWith("]")) {
                            String[] parts = ciValue.substring(1, ciValue.length() - 1).split(",");
                            if (parts.length == 2) {
                                ciLower = Double.parseDouble(parts[0].trim());
                                ciUpper = Double.parseDouble(parts[1].trim());
                            }
                        }
                    }
                } else if ("failureDistribution".equals(currentSubSection)) {
                    Matcher m = KEY_VALUE.matcher(trimmed);
                    if (m.matches()) {
                        failureDistribution.put(m.group(1), Integer.parseInt(m.group(2)));
                    }
                } else if (trimmed.startsWith("successes:")) {
                    successes = Integer.parseInt(extractValue(trimmed));
                    currentSubSection = null;
                } else if (trimmed.startsWith("failures:")) {
                    failures = Integer.parseInt(extractValue(trimmed));
                }
            } else if ("cost".equals(currentSection)) {
                if (trimmed.startsWith("totalTimeMs:")) {
                    totalTimeMs = Long.parseLong(extractValue(trimmed));
                } else if (trimmed.startsWith("avgTimePerSampleMs:")) {
                    avgTimePerSampleMs = Long.parseLong(extractValue(trimmed));
                } else if (trimmed.startsWith("totalTokens:")) {
                    totalTokens = Long.parseLong(extractValue(trimmed));
                } else if (trimmed.startsWith("avgTokensPerSample:")) {
                    avgTokensPerSample = Long.parseLong(extractValue(trimmed));
                }
            } else if ("successCriteria".equals(currentSection)) {
                if (trimmed.startsWith("definition:")) {
                    builder.successCriteriaDefinition(extractValue(trimmed));
                }
            }
        }

        // Build the baseline
        builder.context(context);
        builder.execution(new EmpiricalBaseline.ExecutionSummary(
                samplesPlanned, samplesExecuted, terminationReason, terminationDetails));
        builder.statistics(new EmpiricalBaseline.StatisticsSummary(
                observedSuccessRate, standardError, ciLower, ciUpper,
                successes, failures, failureDistribution));
        builder.cost(new EmpiricalBaseline.CostSummary(
                totalTimeMs, avgTimePerSampleMs, totalTokens, avgTokensPerSample));

        return builder.build();
    }

    private static String extractValue(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return "";
        String value = line.substring(colonIdx + 1).trim();
        // Remove quotes if present
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Object parseValue(String value) {
        // Remove quotes if present
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

