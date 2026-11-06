import { describe, expect, it } from "vitest";
import { pixelDiscPath, ridgePath, ridgeRimPath, RIDGE_VIEW, RIDGES } from "./scenery";

function numbers(path: string): number[] {
  return (path.match(/-?\d+(\.\d+)?/g) ?? []).map(Number);
}

describe("scenery", () => {
  it("sırt yolları deterministiktir (hidrasyon güvenli)", () => {
    for (const spec of Object.values(RIDGES)) {
      expect(ridgePath(spec)).toBe(ridgePath(spec));
      expect(ridgeRimPath(spec)).toBe(ridgeRimPath(spec));
    }
  });

  it("sırt yolları tamsayı ızgarada ve viewBox içinde kalır", () => {
    for (const spec of Object.values(RIDGES)) {
      const values = numbers(ridgePath(spec));
      expect(values.every(Number.isInteger)).toBe(true);
      expect(Math.min(...values)).toBeGreaterThanOrEqual(0);
      expect(Math.max(...values)).toBeLessThanOrEqual(RIDGE_VIEW.width);
      expect(ridgePath(spec).endsWith(`V${RIDGE_VIEW.height}Z`)).toBe(true);
    }
  });

  it("katmanlar derinlik sırasındadır: uzak sırt en yüksekte", () => {
    expect(RIDGES.far.base).toBeLessThan(RIDGES.mid.base);
    expect(RIDGES.mid.base).toBeLessThan(RIDGES.near.base);
  });

  it("piksel disk çekirdeği dış diskten küçüktür", () => {
    const outer = pixelDiscPath(14);
    const core = pixelDiscPath(14, 3);
    expect(outer.length).toBeGreaterThan(0);
    expect(core.length).toBeGreaterThan(0);
    expect(core.split("M").length).toBeLessThan(outer.split("M").length);
    // Mutlak (M) ve göreli (h/v) koordinatlar 2r × 2r ızgarayı aşmaz.
    expect(numbers(outer).every((value) => Math.abs(value) <= 28)).toBe(true);
  });
});
