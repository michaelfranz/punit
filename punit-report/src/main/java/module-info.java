/**
 * The punit-report module.
 *
 * <p>Verdict XML reader/writer, the bundled verdict schemas, and the
 * verdict verifier. Registers its {@code VerdictXmlSink} via
 * {@code provides} so that {@code punit-core}'s {@code uses VerdictSink}
 * discovers it automatically when this module is on the modulepath.
 *
 * <p>This module renders nothing. HTML reports over the emitted verdict
 * XML come from the family's shared {@code mavai} renderer.
 */
module org.mavai.punit.report {

    // ── Public API surface ────────────────────────────────────
    exports org.mavai.punit.report;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.mavai.punit.core;
    requires java.xml;
    requires org.apache.logging.log4j;

    // ── ServiceLoader: register the XML verdict sink ──────────
    provides org.mavai.punit.verdict.VerdictSink
        with org.mavai.punit.report.VerdictXmlSink;
}
