package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Component;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Profile;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Specs;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HardwareRequirementEvaluatorTest {
    private final HardwareRequirementEvaluator evaluator = new HardwareRequirementEvaluator();

    @Test
    void exactModelsAndEnoughRamOnlyConfirmComputeNotOsOrFreeDiskSpace() {
        var level = evaluator.evaluate(profile(16, 512), requirement(8192, 10000), "MINIMUM");
        assertThat(level.computeStatus()).isEqualTo(Status.MEETS);
        assertThat(level.overallStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(level.checks()).filteredOn(check -> check.field().equals("STORAGE")).extracting(HardwareRequirementEvaluator.Check::reason)
                .containsExactly("FREE_SPACE_NOT_MEASURED");
    }

    @Test
    void minimumAndRecommendedRemainSeparateAndOnlyProvenDeficitsProduceUpgradeAdvice() {
        Profile profile = profile(16, 512);
        var minimum = evaluator.evaluate(profile, requirement(8192, 10000), "MINIMUM");
        var recommended = evaluator.evaluate(profile, requirement(32768, 10000), "RECOMMENDED");
        assertThat(minimum.computeStatus()).isEqualTo(Status.MEETS);
        assertThat(recommended.computeStatus()).isEqualTo(Status.BELOW);
        assertThat(evaluator.upgrades(minimum, recommended)).containsExactly(
                new HardwareRequirementEvaluator.Upgrade("INCREASE_RAM", "RAM", "RECOMMENDED", "32768 nominal MB",
                        "CAPACITY_UPPER_BOUND_BELOW_REQUIREMENT"));
    }

    @Test
    void nominalUnitAmbiguityDoesNotCauseFalsePassOrFalseFailure() {
        var ambiguous = evaluator.evaluate(profile(8, 512), requirement(8192, 10000), "MINIMUM");
        assertThat(ambiguous.computeStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(ambiguous.checks()).anyMatch(check -> check.reason().equals("CAPACITY_UNIT_CONVENTION_UNSPECIFIED"));
        assertThat(evaluator.evaluate(profile(4, 512), requirement(8192, 10000), "MINIMUM").computeStatus()).isEqualTo(Status.BELOW);
    }

    @Test
    void totalDiskCapacityBelowRequiredSpaceIsAProvenDeficitButCapacityIsNeverFreeSpace() {
        var minimum = evaluator.evaluate(profile(16, 1), requirement(8192, 10000), "MINIMUM");
        assertThat(minimum.overallStatus()).isEqualTo(Status.BELOW);
        assertThat(evaluator.upgrades(minimum, minimum)).allMatch(upgrade -> upgrade.code().equals("INCREASE_STORAGE_CAPACITY"));
    }

    @Test
    void differentModelNumbersAreNotPerformanceOrderingAndAlternativesAreNotParsedAsExactMatches() {
        var different = new Requirement("MINIMUM", "CPU 99", "GPU 999", 8192, 10000, "Windows");
        assertThat(evaluator.evaluate(profile(16, 512), different, "MINIMUM").computeStatus()).isEqualTo(Status.UNKNOWN);
        var alternative = new Requirement("MINIMUM", "CPU MODEL or OTHER", "GPU MODEL", 8192, 10000, null);
        assertThat(evaluator.evaluate(profile(16, 512), alternative, "MINIMUM").computeStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void missingRequirementsAndPartialComponentsRemainUnknown() {
        assertThat(evaluator.evaluate(profile(16, 512), null, "MINIMUM").overallStatus()).isEqualTo(Status.UNKNOWN);
        var partial = new Profile(UUID.randomUUID(), 1, List.of(component("RAM", "manual", 1, null, null, null)));
        assertThat(evaluator.evaluate(partial, requirement(8192, 10000), "MINIMUM").computeStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(evaluator.evaluate(profile(16, 512), new Requirement("MINIMUM", null, null, null, null, null), "MINIMUM").overallStatus())
                .isEqualTo(Status.UNKNOWN);
    }

    @Test
    void ramQuantityIsPerComponentAndDuplicateCpuOrGpuDoesNotConfirmSingleModel() {
        var items = new ArrayList<>(profile(8, 512).components());
        items.removeIf(item -> item.category().equals("RAM"));
        items.add(component("RAM", "manual", 2, 8, "DDR4", null));
        var profile = new Profile(UUID.randomUUID(), 1, items);
        assertThat(evaluator.evaluate(profile, requirement(8192, 10000), "MINIMUM").computeStatus()).isEqualTo(Status.MEETS);
        items.add(component("CPU", "CPU MODEL", 1, null, null, "AM4"));
        assertThat(evaluator.evaluate(new Profile(UUID.randomUUID(), 1, items), requirement(8192, 10000), "MINIMUM").computeStatus())
                .isEqualTo(Status.UNKNOWN);
    }

    @Test
    void absentRamOrOneUnknownModulePreventsSummingOnlyKnownModules() {
        var items = new ArrayList<>(profile(16, 512).components());
        items.add(component("RAM", "unknown module", 1, null, "DDR4", null));
        assertThat(HardwareRequirementEvaluator.totalCapacity(new Profile(UUID.randomUUID(), 1, items), "RAM")).isNull();
        assertThat(HardwareRequirementEvaluator.totalCapacity(new Profile(UUID.randomUUID(), 1, List.of()), "RAM")).isNull();
    }

    static Profile profile(int ramGb, int storageGb) {
        return new Profile(UUID.randomUUID(), 3, List.of(component("CPU", "CPU MODEL", 1, null, null, "AM4"),
                component("GPU", "GPU MODEL", 1, 8, null, null), component("RAM", "manual", 1, ramGb, "DDR4", null),
                component("STORAGE", "manual", 1, storageGb, null, null), component("MOTHERBOARD", "manual", 1, null, "DDR4", "AM4")));
    }
    static Component component(String category, String model, int quantity, Integer capacity, String memory, String socket) {
        return new Component(UUID.randomUUID(), category, model, quantity, new Specs(capacity, memory, socket));
    }
    private static Requirement requirement(int ram, int storage) {
        return new Requirement("MINIMUM", " cpu   model ", "GPU MODEL", ram, storage, "Windows");
    }
}
