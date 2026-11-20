package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Profile;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComponentCompatibilityEvaluatorTest {
    private final ComponentCompatibilityEvaluator evaluator = new ComponentCompatibilityEvaluator();

    @Test
    void matchingSocketAndDdrDoNotGuaranteeBiosGpuPowerOrWholeSystemCompatibility() {
        var result = evaluator.evaluate(HardwareRequirementEvaluatorTest.profile(16, 512));
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.checks()).allMatch(check -> check.status().equals("DECLARED_MATCH"));
        assertThat(result.limitations()).contains("CHIPSET_BIOS_NOT_VERIFIED", "GPU_SLOT_SIZE_POWER_NOT_VERIFIED");
    }

    @Test
    void differentDeclaredSocketAndDdrAreExplicitMismatches() {
        var profile = new Profile(UUID.randomUUID(), 1, List.of(
                HardwareRequirementEvaluatorTest.component("CPU", "manual", 1, null, null, "LGA1700"),
                HardwareRequirementEvaluatorTest.component("MOTHERBOARD", "manual", 1, null, "DDR4", "AM4"),
                HardwareRequirementEvaluatorTest.component("RAM", "manual", 1, 16, "DDR5", null)));
        assertThat(evaluator.evaluate(profile).status()).isEqualTo("INCOMPATIBLE");
        assertThat(evaluator.evaluate(profile).checks()).allMatch(check -> check.status().equals("INCOMPATIBLE"));
    }

    @Test
    void missingOrUnsupportedSpecificationsAndMultipleCpusRemainUnknown() {
        assertThat(evaluator.evaluate(new Profile(UUID.randomUUID(), 1, List.of())).status()).isEqualTo("UNKNOWN");
        var profile = new Profile(UUID.randomUUID(), 1, List.of(
                HardwareRequirementEvaluatorTest.component("CPU", "manual", 2, null, null, "AM4"),
                HardwareRequirementEvaluatorTest.component("MOTHERBOARD", "manual", 1, null, "unverified", "unknown"),
                HardwareRequirementEvaluatorTest.component("RAM", "manual", 1, 16, "DDR4", null)));
        assertThat(evaluator.evaluate(profile).checks()).allMatch(check -> check.status().equals("UNKNOWN"));
    }
}
