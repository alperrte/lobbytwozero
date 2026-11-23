import { describe, expect, it } from "vitest";
import { clampSize, isGraphicsPreset, isRequirementPlatform, isResolution, isUuid, normalizeCode, optionalCode } from "./filters";

describe("istek filtreleri", () => {
  it.each([
    ["games", undefined, 20],
    ["games", 0, 1],
    ["games", 51, 50],
    ["games", 2.5, 20],
    ["runs", 30, 20],
    ["people", -4, 1],
    ["events", 7, 7],
    ["compatible", Number.NaN, 10]
  ] as const)("clampSize(%s, %s) = %s", (kind, value, expected) => {
    expect(clampSize(kind, value)).toBe(expected);
  });

  it("boş veya desene uymayan kod gönderilmez", () => {
    expect(optionalCode(undefined)).toBeUndefined();
    expect(optionalCode("  ")).toBeUndefined();
    expect(optionalCode("Action")).toBeUndefined();
    expect(optionalCode("a".repeat(65))).toBeUndefined();
    expect(optionalCode(" role-playing ")).toBe("role-playing");
  });

  it("UUID, platform, preset ve çözünürlük doğrulanır", () => {
    expect(isUuid("11111111-1111-4111-8111-111111111111")).toBe(true);
    expect(isUuid("not-a-uuid")).toBe(false);
    expect(isUuid(null)).toBe(false);
    expect(isRequirementPlatform("linux")).toBe(true);
    expect(isRequirementPlatform("ps5")).toBe(false);
    expect(isGraphicsPreset("ULTRA")).toBe(true);
    expect(isGraphicsPreset("EPIC")).toBe(false);
    expect(isResolution("1920x1080")).toBe(true);
    expect(isResolution("0x0")).toBe(false);
    expect(isResolution("1920×1080")).toBe(false);
  });

  it("etkinlik kodu normalizasyonu yalnız [a-z0-9] bırakır", () => {
    expect(normalizeCode("Role-Playing")).toBe("roleplaying");
    expect(normalizeCode("PC_Windows 11")).toBe("pcwindows11");
  });
});
