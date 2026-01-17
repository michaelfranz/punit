package org.javai.punit.spec.criteria;

import java.util.List;
import java.util.Map;
import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;

/**
 * Formats success criteria outcomes for console output and reporting.
 *
 * <p>Provides both compact and detailed output formats suitable for:
 * <ul>
 *   <li>Console output during test/experiment execution</li>
 *   <li>Spec file generation</li>
 *   <li>Test failure messages</li>
 * </ul>
 */
public class CriteriaReporter {

    private static final String CHECKMARK = "✓";
    private static final String CROSS = "✗";
    private static final String WARNING = "⚠";
    private static final String SKIP = "○";

    /**
     * Formats criteria outcomes as a compact single-line summary.
     *
     * @param criteria the criteria to format
     * @return compact summary like "✓ JSON parsed  ✓ Has products  ✗ No hallucination"
     */
    public String formatCompact(UseCaseCriteria criteria) {
        List<CriterionOutcome> outcomes = criteria.evaluate();
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < outcomes.size(); i++) {
            if (i > 0) sb.append("  ");
            sb.append(formatOutcomeSymbol(outcomes.get(i)));
            sb.append(" ");
            sb.append(outcomes.get(i).description());
        }
        
        return sb.toString();
    }

    /**
     * Formats criteria outcomes as a multi-line detailed report.
     *
     * @param criteria the criteria to format
     * @return detailed report with one criterion per line
     */
    public String formatDetailed(UseCaseCriteria criteria) {
        List<CriterionOutcome> outcomes = criteria.evaluate();
        StringBuilder sb = new StringBuilder();
        sb.append("Success Criteria:\n");
        
        for (CriterionOutcome outcome : outcomes) {
            sb.append("  ");
            sb.append(formatOutcomeSymbol(outcome));
            sb.append(" ");
            sb.append(outcome.description());
            
            // Add reason for failures/errors
            String suffix = switch (outcome) {
                case CriterionOutcome.Failed failed -> " — " + failed.reason();
                case CriterionOutcome.Errored errored -> " — " + errored.reason();
                case CriterionOutcome.Passed p -> "";
                case CriterionOutcome.NotEvaluated n -> "";
            };
            sb.append(suffix);
            
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Formats aggregated criteria stats for spec files.
     *
     * @param stats the criterion stats map
     * @return YAML-friendly format
     */
    public String formatStatsForSpec(Map<String, CriteriaOutcomeAggregator.CriterionStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("criteriaPassRates:\n");
        
        for (Map.Entry<String, CriteriaOutcomeAggregator.CriterionStats> entry : stats.entrySet()) {
            CriteriaOutcomeAggregator.CriterionStats stat = entry.getValue();
            sb.append(String.format("  \"%s\": %.4f  # %d/%d%n",
                entry.getKey(),
                stat.getPassRate(),
                stat.getPassed(),
                stat.getTotal()));
        }
        
        return sb.toString();
    }

    /**
     * Formats aggregated criteria stats for console summary.
     *
     * @param stats the criterion stats map
     * @return human-readable console format
     */
    public String formatStatsForConsole(Map<String, CriteriaOutcomeAggregator.CriterionStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("Criteria Pass Rates:\n");
        
        int maxDescLen = stats.keySet().stream()
            .mapToInt(String::length)
            .max()
            .orElse(20);
        
        for (Map.Entry<String, CriteriaOutcomeAggregator.CriterionStats> entry : stats.entrySet()) {
            CriteriaOutcomeAggregator.CriterionStats stat = entry.getValue();
            String paddedDesc = String.format("%-" + maxDescLen + "s", entry.getKey());
            double rate = stat.getPassRate() * 100;
            
            // Color-code based on pass rate
            String rateStr = String.format("%5.1f%%", rate);
            String symbol = rate >= 95 ? CHECKMARK : (rate >= 80 ? WARNING : CROSS);
            
            sb.append(String.format("  %s %s  %s (%d/%d)%n",
                symbol,
                paddedDesc,
                rateStr,
                stat.getPassed(),
                stat.getTotal()));
        }
        
        return sb.toString();
    }

    /**
     * Formats a single outcome for failure messages.
     *
     * @param outcome the outcome to format
     * @return formatted string like "✓ JSON parsed" or "✗ No hallucination: Criterion not satisfied"
     */
    public String formatOutcome(CriterionOutcome outcome) {
        String suffix = switch (outcome) {
            case CriterionOutcome.Failed failed -> ": " + failed.reason();
            case CriterionOutcome.Errored errored -> ": " + errored.reason();
            case CriterionOutcome.Passed p -> "";
            case CriterionOutcome.NotEvaluated n -> "";
        };
        return formatOutcomeSymbol(outcome) + " " + outcome.description() + suffix;
    }

    /**
     * Gets the appropriate symbol for an outcome.
     */
    private String formatOutcomeSymbol(CriterionOutcome outcome) {
        return switch (outcome) {
            case CriterionOutcome.Passed p -> CHECKMARK;
            case CriterionOutcome.Failed f -> CROSS;
            case CriterionOutcome.Errored e -> WARNING;
            case CriterionOutcome.NotEvaluated n -> SKIP;
        };
    }

    /**
     * Creates a summary line for criteria (e.g., "3/4 criteria passed").
     *
     * @param criteria the criteria to summarize
     * @return summary string
     */
    public String formatSummary(UseCaseCriteria criteria) {
        List<CriterionOutcome> outcomes = criteria.evaluate();
        long passed = outcomes.stream().filter(CriterionOutcome::passed).count();
        int total = outcomes.size();
        
        if (passed == total) {
            return String.format("All %d criteria passed %s", total, CHECKMARK);
        } else {
            return String.format("%d/%d criteria passed", passed, total);
        }
    }
}

