import { describe, expect, it } from "vitest";
import { resolveSceneTiming } from "@/shared/lib/theme/transition-scenes";
import { OPERATOR_AIM_ANGLES, OPERATOR_FRAMES } from "./operator-frames";
import {
  aimDirection,
  blackoutPlan,
  daybreakPlan,
  flareArc,
  frameAt,
  placeOnRay,
  type FrameCue
} from "./timeline";

const LAYOUTS = [false, true] as const;

function expectCuesValid(frames: readonly FrameCue[], totalMs: number) {
  expect(frames[0][0]).toBe(0);
  for (let index = 1; index < frames.length; index += 1) {
    expect(frames[index][0]).toBeGreaterThan(frames[index - 1][0]);
  }
  expect(frames.at(-1)?.[0]).toBeLessThan(totalMs);
  for (const [, frame] of frames) {
    expect(OPERATOR_FRAMES).toHaveProperty(frame);
  }
}

describe("blackoutPlan", () => {
  it.each(LAYOUTS)("compact=%s: süreler denetleyiciyle birebir aynıdır", (compact) => {
    const plan = blackoutPlan(compact);
    const timing = resolveSceneTiming("v3-blackout", compact);
    expect(plan.totalMs).toBe(timing.totalMs);
    expect(plan.applyAtMs).toBe(timing.applyAtMs);
  });

  it.each(LAYOUTS)("compact=%s: tema son lamba söndüğü an uygulanır", (compact) => {
    const plan = blackoutPlan(compact);
    expect(plan.applyAtMs).toBe(plan.shots.at(-1)?.lampOffAt);
  });

  it.each(LAYOUTS)("compact=%s: kareler artan sırada, geçerli ve süre içinde", (compact) => {
    const plan = blackoutPlan(compact);
    expectCuesValid(plan.frames, plan.totalMs);
  });

  it.each(LAYOUTS)("compact=%s: atışlar sırayla ve çakışmadan gerçekleşir", (compact) => {
    const { shots, walkIn, glowAt, fadeOutAt, totalMs, captionAt } = blackoutPlan(compact);
    expect(captionAt).toBeGreaterThanOrEqual(walkIn[1]);
    let previousOff = walkIn[1];
    for (const shot of shots) {
      expect(shot.aimAt).toBeGreaterThan(previousOff - 1);
      expect(shot.fireAt).toBeGreaterThan(shot.aimAt);
      expect(shot.hitAt).toBeGreaterThan(shot.fireAt);
      expect(shot.lampOffAt).toBeGreaterThan(shot.hitAt);
      previousOff = shot.lampOffAt;
    }
    // NVG ışığı tüm lambalar söndükten sonra yanar; operatör en son gölgeye karışır.
    expect(glowAt).toBeGreaterThan(previousOff);
    expect(fadeOutAt).toBeGreaterThan(glowAt);
    expect(fadeOutAt).toBeLessThan(totalMs);
  });

  it("masaüstünde üç, dar ekranda iki lamba vurulur", () => {
    expect(blackoutPlan(false).shots).toHaveLength(3);
    expect(blackoutPlan(true).shots).toHaveLength(2);
  });

  it("namlu yükselen açılarla nişan alır (alçak → yüksek lamba)", () => {
    const angles = blackoutPlan(false).shots.map((shot) => OPERATOR_AIM_ANGLES[shot.aim]);
    expect(angles).toEqual([...angles].sort((a, b) => a - b));
  });

  it("atış anında geri tepme karesi (K) görünür, ardından nişan karesine döner", () => {
    const plan = blackoutPlan(false);
    for (const shot of plan.shots) {
      expect(frameAt(plan.frames, shot.fireAt)).toBe(`${shot.aim}K`);
      expect(frameAt(plan.frames, shot.fireAt - 1)).toBe(shot.aim);
    }
    expect(frameAt(plan.frames, plan.glowAt)).toBe("nvgOn");
  });
});

describe("daybreakPlan", () => {
  it.each(LAYOUTS)("compact=%s: süreler denetleyiciyle birebir aynıdır", (compact) => {
    const plan = daybreakPlan(compact);
    const timing = resolveSceneTiming("v3-daybreak", compact);
    expect(plan.totalMs).toBe(timing.totalMs);
    expect(plan.applyAtMs).toBe(timing.applyAtMs);
  });

  it.each(LAYOUTS)("compact=%s: kareler artan sırada, geçerli ve süre içinde", (compact) => {
    const plan = daybreakPlan(compact);
    expectCuesValid(plan.frames, plan.totalMs);
  });

  it.each(LAYOUTS)("compact=%s: NVG kalkar → ateş → tepe → güneş → tema → çıkış sırası", (compact) => {
    const plan = daybreakPlan(compact);
    expect(plan.glowOffAt).toBeLessThan(plan.fireAt);
    expect(plan.fireAt).toBeLessThan(plan.flareApexAt);
    expect(plan.flareApexAt).toBeLessThan(plan.flareEndAt);
    expect(plan.sunrise[0]).toBeLessThan(plan.sunrise[1]);
    expect(plan.wash[0]).toBeLessThan(plan.wash[1]);
    // Tema, sıcak ışık ekrana yayıldığı an (wash sonu) uygulanır.
    expect(plan.applyAtMs).toBe(plan.wash[1]);
    expect(plan.exit[0]).toBeGreaterThan(plan.applyAtMs);
    expect(plan.exit[1]).toBe(plan.totalMs);
    expect(frameAt(plan.frames, plan.fireAt)).toBe("flareKick");
    expect(frameAt(plan.frames, 0)).toBe("idleGlow");
  });

  it("şapka düzeltme kareleri tema uygulandıktan sonra oynar", () => {
    const plan = daybreakPlan(false);
    const hatAt = plan.frames.find(([, frame]) => frame === "hatLift")?.[0] ?? 0;
    expect(hatAt).toBeGreaterThan(plan.applyAtMs);
    expect(hatAt).toBeLessThan(plan.exit[0]);
  });
});

describe("geometri", () => {
  it("aimDirection sola ve yukarı bakar", () => {
    for (const angle of [10, 45, 80]) {
      const [dx, dy] = aimDirection(angle);
      expect(dx).toBeLessThan(0);
      expect(dy).toBeLessThan(0);
      expect(Math.hypot(dx, dy)).toBeCloseTo(1);
    }
  });

  it("placeOnRay noktayı istenen yükseklikte ışın üzerine koyar", () => {
    const muzzle = [1000, 800] as const;
    const [x, y] = placeOnRay(muzzle, 45, 200, 40);
    expect(y).toBeCloseTo(200);
    expect(x).toBeCloseTo(400);
  });

  it("placeOnRay ışın kenardan taşarsa noktayı sol kenar payına sabitler", () => {
    const muzzle = [500, 800] as const;
    const [x, y] = placeOnRay(muzzle, 20, 100, 40);
    expect(x).toBeCloseTo(40);
    expect(y).toBeGreaterThan(100);
    // Nokta hâlâ aynı ışın üzerinde.
    const [dx, dy] = aimDirection(20);
    expect((x - muzzle[0]) * dy - (y - muzzle[1]) * dx).toBeCloseTo(0);
  });

  it("flareArc namludan başlar, tepede biter ve sürekli yükselir", () => {
    const muzzle = [1200, 800] as const;
    const points = flareArc(muzzle, 58, 140, 10);
    expect(points).toHaveLength(10);
    expect(points[0]).toEqual(muzzle);
    expect(points.at(-1)?.[1]).toBeCloseTo(140);
    for (let index = 1; index < points.length; index += 1) {
      expect(points[index][1]).toBeLessThan(points[index - 1][1]);
      expect(points[index][0]).toBeLessThan(points[index - 1][0]);
    }
  });
});
