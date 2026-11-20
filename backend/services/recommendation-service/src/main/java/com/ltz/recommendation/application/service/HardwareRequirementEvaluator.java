package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Component;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Profile;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Marka/model hiyerarşisi uydurmaz; yalnız aynı tek model ve kanıtlı sayısal sınırlar. */
@org.springframework.stereotype.Component
public class HardwareRequirementEvaluator {
    public enum Status { MEETS, BELOW, UNKNOWN }
    public record Check(String field, Status status, String actual, String required, String reason) { }
    public record Level(String level, Status computeStatus, Status overallStatus, List<Check> checks) { }
    public record Upgrade(String code, String field, String requirementLevel, String required, String reason) { }

    public Level evaluate(Profile profile, Requirement requirement, String level) {
        if (requirement == null) return new Level(level, Status.UNKNOWN, Status.UNKNOWN,
                List.of(new Check("REQUIREMENTS", Status.UNKNOWN, null, null, "REQUIREMENTS_MISSING")));
        List<Check> checks = new ArrayList<>();
        checks.add(model(profile, "CPU", requirement.cpu()));
        checks.add(model(profile, "GPU", requirement.gpu()));
        checks.add(capacity(profile, "RAM", requirement.ramMb(), false));
        Status compute = combine(checks);
        checks.add(vram(profile));
        checks.add(capacity(profile, "STORAGE", requirement.storageMb(), true));
        checks.add(new Check("OS", Status.UNKNOWN, null, requirement.os(), "OS_AND_DRIVERS_NOT_VERIFIED"));
        return new Level(level, compute, combine(checks), List.copyOf(checks));
    }

    public List<Upgrade> upgrades(Level minimum, Level recommended) {
        List<Upgrade> result = new ArrayList<>();
        for (Level level : List.of(minimum, recommended)) {
            for (Check check : level.checks()) {
                if (check.status() == Status.BELOW) result.add(new Upgrade(
                        check.field().equals("RAM") ? "INCREASE_RAM" : "INCREASE_STORAGE_CAPACITY",
                        check.field(), level.level(), check.required(), check.reason()));
            }
        }
        return List.copyOf(result);
    }

    private Check model(Profile profile, String category, String required) {
        var components = profile.components().stream().filter(item -> item.category().equals(category)).toList();
        String actual = components.size() == 1 && components.getFirst().quantity() == 1 ? components.getFirst().modelName() : null;
        boolean same = actual != null && required != null && singleModel(required)
                && normalize(actual).equals(normalize(required));
        return new Check(category, same ? Status.MEETS : Status.UNKNOWN, actual, required,
                same ? "EXACT_DECLARED_MODEL_MATCH" : "MODEL_ORDER_OR_REQUIREMENT_UNKNOWN");
    }

    private Check capacity(Profile profile, String category, Integer requiredMb, boolean freeSpaceRequired) {
        Long totalGb = totalCapacity(profile, category);
        String actual = totalGb == null ? null : totalGb + " nominal GB";
        String required = requiredMb == null ? null : requiredMb + " nominal MB";
        if (totalGb == null || requiredMb == null || requiredMb < 1) {
            return new Check(category, Status.UNKNOWN, actual, required, "CAPACITY_OR_REQUIREMENT_MISSING");
        }
        // GB/MB conventions are not specified by the source contracts. Compare worst-case byte bounds.
        BigInteger capacityLower = BigInteger.valueOf(totalGb).multiply(BigInteger.valueOf(1_000_000_000));
        BigInteger capacityUpper = BigInteger.valueOf(totalGb).multiply(BigInteger.valueOf(1_073_741_824));
        BigInteger requirementLower = BigInteger.valueOf(requiredMb).multiply(BigInteger.valueOf(1_000_000));
        BigInteger requirementUpper = BigInteger.valueOf(requiredMb).multiply(BigInteger.valueOf(1_048_576));
        if (capacityUpper.compareTo(requirementLower) < 0) {
            return new Check(category, Status.BELOW, actual, required, "CAPACITY_UPPER_BOUND_BELOW_REQUIREMENT");
        }
        if (freeSpaceRequired) return new Check(category, Status.UNKNOWN, actual, required, "FREE_SPACE_NOT_MEASURED");
        if (capacityLower.compareTo(requirementUpper) >= 0) {
            return new Check(category, Status.MEETS, actual, required, "CAPACITY_LOWER_BOUND_MEETS_REQUIREMENT");
        }
        return new Check(category, Status.UNKNOWN, actual, required, "CAPACITY_UNIT_CONVENTION_UNSPECIFIED");
    }

    static Long totalCapacity(Profile profile, String category) {
        List<Component> items = profile.components().stream().filter(item -> item.category().equals(category)).toList();
        if (items.isEmpty() || items.stream().anyMatch(item -> item.specs() == null || item.specs().capacityGb() == null)) return null;
        return items.stream().mapToLong(item -> (long) item.specs().capacityGb() * item.quantity()).sum();
    }

    private static Check vram(Profile profile) {
        var gpu = profile.components().stream().filter(item -> item.category().equals("GPU")).toList();
        Integer capacity = gpu.size() == 1 && gpu.getFirst().quantity() == 1 && gpu.getFirst().specs() != null
                ? gpu.getFirst().specs().capacityGb() : null;
        return new Check("VRAM", Status.UNKNOWN, capacity == null ? null : capacity + " nominal GB", null,
                "STRUCTURED_VRAM_REQUIREMENT_UNAVAILABLE");
    }

    private static Status combine(List<Check> checks) {
        if (checks.stream().anyMatch(check -> check.status() == Status.BELOW)) return Status.BELOW;
        return checks.stream().allMatch(check -> check.status() == Status.MEETS) ? Status.MEETS : Status.UNKNOWN;
    }

    private static boolean singleModel(String value) {
        return !value.matches("(?is).*([/;,\\n]|\\b(or|and|ve|veya)\\b).*" ) && !normalize(value).isBlank();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
