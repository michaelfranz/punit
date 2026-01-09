package org.javai.punit.experiment.api;

/**
 * Defines the execution mode for an experiment.
 *
 * <p>Experiments serve two distinct purposes that share execution machinery
 * but have different intents and outputs.
 *
 * <h2>Mode Comparison</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    @Experiment Modes                             │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  Mode:     BASELINE (default)      │ EXPLORE                    │
 * │  Intent:   Precise estimation      │ Factor comparison          │
 * │  Configs:  1 (implicit)            │ N (from factor source)     │
 * │  Samples:  1000+ (default: 1000)   │ 1+/config (default: 1)     │
 * │  Output:   1 baseline file         │ N baseline files           │
 * │  Decision: "What's the true rate?" │ "Which config is best?"    │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see Experiment#mode()
 * @see FactorSource
 */
public enum ExperimentMode {
    
    /**
     * BASELINE mode establishes reliable statistics for a single configuration.
     *
     * <p>This is the default mode. Use it when you want to:
     * <ul>
     *   <li>Measure the true success rate with high precision</li>
     *   <li>Generate an empirical baseline for deriving test specifications</li>
     *   <li>Establish confidence intervals for probabilistic assertions</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples:</b> 1000+ (default: 1000)</li>
     *   <li><b>Output:</b> Single baseline file</li>
     * </ul>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using < 100 samples produces imprecise baselines with wide
     * confidence intervals.
     */
    BASELINE,
    
    /**
     * EXPLORE mode compares multiple configurations to understand factor effects.
     *
     * <p>Use this mode when you want to:
     * <ul>
     *   <li>Compare different LLM models</li>
     *   <li>Evaluate temperature/prompt variations</li>
     *   <li>Find which configuration works best</li>
     * </ul>
     *
     * <h3>Typical Workflow</h3>
     * <p>Exploration usually happens in two phases:
     *
     * <p><b>Phase 1: "Which configs work at all?"</b>
     * <pre>{@code
     * @Experiment(mode = ExperimentMode.EXPLORE)
     * // samplesPerConfig = 1 (default)
     * }</pre>
     * <p>Fast pass through all configurations to filter out broken ones.
     *
     * <p><b>Phase 2: "Which config is best?"</b>
     * <pre>{@code
     * @Experiment(mode = ExperimentMode.EXPLORE, samplesPerConfig = 10)
     * }</pre>
     * <p>More samples for remaining configs to enable statistical comparison.
     *
     * <h3>Output</h3>
     * <p>One baseline file per configuration, enabling comparison via:
     * <ul>
     *   <li>IDE diff tools</li>
     *   <li>Command-line diff</li>
     *   <li>Future PUnit comparison tooling</li>
     * </ul>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using > 50 samples per config during exploration is wasteful.
     * Use BASELINE mode once you've chosen the best configuration.
     */
    EXPLORE
}

