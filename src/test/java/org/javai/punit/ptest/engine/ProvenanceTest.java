package org.javai.punit.ptest.engine;

import org.javai.punit.api.TargetSource;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceContractRefOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceEmpiricalSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenancePolicySourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSlaSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSloSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceTargetSourceOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceUnspecifiedTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceWithBothTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysPassingTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SLA provenance feature in probabilistic tests.
 * 
 * <p>These tests verify that:
 * <ul>
 *   <li>Provenance information is included in verdict output when specified</li>
 *   <li>Provenance is omitted when not specified</li>
 *   <li>All TargetSource values are rendered correctly</li>
 * </ul>
 */
class ProvenanceTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Test
    void noProvenanceSet_verdictDoesNotIncludeProvenance() {
        String output = captureTestOutput(AlwaysPassingTest.class);
        
        assertThat(output).doesNotContain("Target source:");
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void provenanceUnspecified_verdictDoesNotIncludeProvenance() {
        String output = captureTestOutput(ProvenanceUnspecifiedTest.class);
        
        assertThat(output).doesNotContain("Target source:");
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void targetSourceOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceTargetSourceOnlyTest.class);
        
        assertThat(output).contains("Target source: SLO");
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void contractRefOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceContractRefOnlyTest.class);
        
        assertThat(output).contains("Contract ref: Internal Policy DOC-001");
        assertThat(output).doesNotContain("Target source:");
    }

    @Test
    void bothSet_includedInCorrectOrder() {
        String output = captureTestOutput(ProvenanceWithBothTest.class);
        
        assertThat(output).contains("Target source: SLA");
        assertThat(output).contains("Contract ref: Acme API SLA v3.2 §2.1");
        
        // Verify order: targetSource before contractRef
        int targetSourceIndex = output.indexOf("Target source:");
        int contractRefIndex = output.indexOf("Contract ref:");
        assertThat(targetSourceIndex)
                .as("Target source should appear before Contract ref")
                .isLessThan(contractRefIndex);
    }

    @Test
    void slaSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSlaSourceTest.class);
        assertThat(output).contains("Target source: SLA");
    }

    @Test
    void sloSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSloSourceTest.class);
        assertThat(output).contains("Target source: SLO");
    }

    @Test
    void policySource_renderedCorrectly() {
        String output = captureTestOutput(ProvenancePolicySourceTest.class);
        assertThat(output).contains("Target source: POLICY");
    }

    @Test
    void empiricalSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceEmpiricalSourceTest.class);
        assertThat(output).contains("Target source: EMPIRICAL");
    }

    @Test
    void configurationResolver_extractsProvenance() {
        // Unit test for ConfigurationResolver.ResolvedConfiguration provenance methods
        ConfigurationResolver.ResolvedConfiguration config = 
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                TargetSource.SLA, "Test Contract"
            );
        
        assertThat(config.hasProvenance()).isTrue();
        assertThat(config.hasTargetSource()).isTrue();
        assertThat(config.hasContractRef()).isTrue();
        assertThat(config.targetSource()).isEqualTo(TargetSource.SLA);
        assertThat(config.contractRef()).isEqualTo("Test Contract");
    }

    @Test
    void configurationResolver_noProvenance_hasProvenanceFalse() {
        ConfigurationResolver.ResolvedConfiguration config = 
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5
            );
        
        assertThat(config.hasProvenance()).isFalse();
        assertThat(config.hasTargetSource()).isFalse();
        assertThat(config.hasContractRef()).isFalse();
    }

    @Test
    void configurationResolver_emptyContractRef_hasContractRefFalse() {
        ConfigurationResolver.ResolvedConfiguration config = 
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                TargetSource.SLA, ""
            );
        
        assertThat(config.hasProvenance()).isTrue();  // has targetSource
        assertThat(config.hasTargetSource()).isTrue();
        assertThat(config.hasContractRef()).isFalse();  // empty string
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Captures stdout output from running a test class.
     */
    private String captureTestOutput(Class<?> testClass) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(baos));
            
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .execute();
            
            return baos.toString();
        } finally {
            System.setOut(originalOut);
        }
    }
}

