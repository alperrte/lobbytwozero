package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Component;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@org.springframework.stereotype.Component
public class ComponentCompatibilityEvaluator {
    public record Check(String field, String status, String reason) { }
    public record Result(String status, List<Check> checks, List<String> limitations) { }

    public Result evaluate(Profile profile) {
        List<Check> checks = new ArrayList<>();
        Component cpu = single(profile, "CPU");
        Component board = single(profile, "MOTHERBOARD");
        checks.add(compare("CPU_SOCKET", socket(cpu), socket(board), "(?:AM[0-9]+|LGA[0-9]+)"));
        var ram = profile.components().stream().filter(item -> item.category().equals("RAM")).toList();
        if (ram.isEmpty()) checks.add(new Check("RAM_MEMORY_TYPE", "UNKNOWN", "RAM_MISSING"));
        for (Component module : ram) checks.add(compare("RAM_MEMORY_TYPE", memory(module), memory(board), "DDR[0-9]+"));
        String status = checks.stream().anyMatch(check -> check.status().equals("INCOMPATIBLE")) ? "INCOMPATIBLE" : "UNKNOWN";
        return new Result(status, List.copyOf(checks), List.of("CHIPSET_BIOS_NOT_VERIFIED", "RAM_FORM_FACTOR_NOT_VERIFIED",
                "GPU_SLOT_SIZE_POWER_NOT_VERIFIED", "SAME_SOCKET_OR_DDR_IS_NOT_FULL_COMPATIBILITY"));
    }

    private static Check compare(String field, String actual, String supported, String pattern) {
        if (actual == null || supported == null || !actual.matches(pattern) || !supported.matches(pattern)) {
            return new Check(field, "UNKNOWN", "TECHNICAL_FIELDS_MISSING_OR_UNSUPPORTED");
        }
        return actual.equals(supported) ? new Check(field, "DECLARED_MATCH", "SINGLE_TECHNICAL_FIELD_MATCH")
                : new Check(field, "INCOMPATIBLE", "DECLARED_TECHNICAL_FIELD_MISMATCH");
    }

    private static Component single(Profile profile, String category) {
        var items = profile.components().stream().filter(item -> item.category().equals(category)).toList();
        return items.size() == 1 && items.getFirst().quantity() == 1 ? items.getFirst() : null;
    }
    private static String socket(Component component) { return normalized(component == null || component.specs() == null ? null : component.specs().socket()); }
    private static String memory(Component component) { return normalized(component == null || component.specs() == null ? null : component.specs().memoryType()); }
    private static String normalized(String value) { return value == null ? null : value.strip().toUpperCase(Locale.ROOT).replace(" ", ""); }
}
