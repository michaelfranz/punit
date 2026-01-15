package org.javai.punit.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Formats and emits PUnit reports using a consistent visual header/footer.
 *
 * <p>Each report includes a title on the left and "PUnit" branding on the right:
 * <pre>
 * ═ VERDICT: PASS ════════════════════════════════════════════════════ PUnit ═
 * shouldReturnValidJson
 * Observed pass rate: 95.0% (95/100) >= min pass rate: 90.0%
 * Elapsed: 1234ms
 * ══════════════════════════════════════════════════════════════════════════════
 * </pre>
 */
public final class PUnitReporter {

    private static final Logger logger = LogManager.getLogger(PUnitReporter.class);
    private static final int DEFAULT_WIDTH = 78;
    private static final String SUFFIX = " PUnit ═";

    private final int width;

    public PUnitReporter() {
        this(DEFAULT_WIDTH);
    }

    public PUnitReporter(int width) {
        this.width = Math.max(24, width);
    }

    /**
     * Logs a report at INFO level.
     *
     * @param title the report title (e.g., "VERDICT: PASS", "BASELINE SELECTED")
     * @param body the report body content
     */
    public void reportInfo(String title, String body) {
        logger.info(format(title, body));
    }

    /**
     * Logs a report at WARN level.
     *
     * @param title the report title (e.g., "BASELINE EXPIRED")
     * @param body the report body content
     */
    public void reportWarn(String title, String body) {
        logger.warn(format(title, body));
    }

    /**
     * Logs a report at ERROR level.
     *
     * @param title the report title
     * @param body the report body content
     */
    public void reportError(String title, String body) {
        logger.error(format(title, body));
    }

    /**
     * Returns the header divider with title on left and "PUnit" on right.
     *
     * <p>Format: {@code ═ {TITLE} ════════════════════════════════════ PUnit ═}
     *
     * @param title the title to include in the header
     * @return the formatted header divider
     */
    public String headerDivider(String title) {
        String prefix = "═ " + (title == null ? "" : title) + " ";
        int fillerLength = width - prefix.length() - SUFFIX.length();
        if (fillerLength <= 0) {
            // Title too long, just show what we can
            return prefix.trim() + SUFFIX;
        }
        return prefix + "═".repeat(fillerLength) + SUFFIX;
    }

    /**
     * Returns the footer divider (plain line).
     */
    public String footerDivider() {
        return "═".repeat(width);
    }

    private String format(String title, String body) {
        String trimmed = body == null ? "" : body.trim();
        return headerDivider(title) + "\n" + trimmed + "\n" + footerDivider();
    }
}
