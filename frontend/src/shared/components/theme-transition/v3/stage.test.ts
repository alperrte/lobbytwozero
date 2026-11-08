import { describe, expect, it } from "vitest";
import { OPERATOR_AIM_ANGLES, OPERATOR_CELL } from "./operator-frames";
import { anchorOnScreen, layoutLamps, operatorBox, OPERATOR_SCALE, type Viewport } from "./stage";
import { blackoutPlan } from "./timeline";

const VIEWPORTS: Array<{ name: string; view: Viewport; compact: boolean }> = [
  { name: "375×812", view: { width: 375, height: 812, headerBottom: 64 }, compact: true },
  { name: "768×1024", view: { width: 768, height: 1024, headerBottom: 80 }, compact: false },
  { name: "1440×900", view: { width: 1440, height: 900, headerBottom: 88 }, compact: false },
  { name: "1920×1080", view: { width: 1920, height: 1080, headerBottom: 88 }, compact: false }
];

describe("operatorBox", () => {
  it.each(VIEWPORTS)("$name: operatör sağ altta, tamamen ekranda", ({ view, compact }) => {
    const box = operatorBox(view, compact);
    expect(box.scale).toBe(compact ? OPERATOR_SCALE.compact : OPERATOR_SCALE.desktop);
    expect(box.size).toBe(OPERATOR_CELL * box.scale);
    expect(box.left).toBeGreaterThan(view.width / 2);
    expect(box.left + box.size).toBeLessThanOrEqual(view.width);
    expect(box.top + box.size).toBeLessThanOrEqual(view.height);
    // Giriş kayması kutuyu sağ kenarın tamamen dışına taşır.
    expect(box.left + box.offRight).toBeGreaterThan(view.width);
  });

  it("boy masaüstünde 128 px, dar ekranda 96 px (90–130 px aralığı)", () => {
    expect(operatorBox(VIEWPORTS[2].view, false).size).toBe(128);
    expect(operatorBox(VIEWPORTS[0].view, true).size).toBe(96);
  });

  it("çapa noktası ölçekle birlikte ekrana taşınır", () => {
    const box = operatorBox(VIEWPORTS[2].view, false);
    const [x, y] = anchorOnScreen(box, "nvgOn", "lens");
    expect(x).toBeGreaterThan(box.left);
    expect(x).toBeLessThan(box.left + box.size);
    expect(y).toBeGreaterThan(box.top);
    expect(y).toBeLessThan(box.top + box.size / 2);
  });
});

describe("layoutLamps", () => {
  it.each(VIEWPORTS)("$name: her lamba görünür alanda ve navbar altında", ({ view, compact }) => {
    const lamps = layoutLamps(blackoutPlan(compact), view, compact);
    expect(lamps).toHaveLength(compact ? 2 : 3);
    for (const lamp of lamps) {
      expect(lamp.bulb[0]).toBeGreaterThanOrEqual(20);
      expect(lamp.bulb[0]).toBeLessThan(view.width);
      expect(lamp.bulb[1]).toBeGreaterThan(view.headerBottom);
      expect(lamp.bulb[1]).toBeLessThan(view.height);
      expect(lamp.distance).toBeGreaterThan(0);
      // Ampul, mermi izinin başladığı namlunun solunda ve yukarısındadır.
      expect(lamp.bulb[0]).toBeLessThan(lamp.muzzle[0]);
      expect(lamp.bulb[1]).toBeLessThan(lamp.muzzle[1]);
    }
  });

  it.each(VIEWPORTS)("$name: uzun kablo yerine duvar kolu kullanılır", ({ view, compact }) => {
    for (const lamp of layoutLamps(blackoutPlan(compact), view, compact)) {
      expect(lamp.mount).toBe(lamp.cable > 140 ? "wall" : "ceiling");
    }
  });

  it.each([
    { name: "375×812", view: { width: 375, height: 812, headerBottom: 76 } },
    { name: "360×640", view: { width: 360, height: 640, headerBottom: 76 } },
    { name: "320×568", view: { width: 320, height: 568, headerBottom: 64 } }
  ])("mobil $name: lambalar sol kenara yapışmaz, tavandan sarkar ve ışık konisi ekranda kalır", ({ view }) => {
    const lamps = layoutLamps(blackoutPlan(true), view, true);
    for (const lamp of lamps) {
      expect(lamp.mount).toBe("ceiling");
      // Dar ekran konisi yarı genişliği 40 px; tamamı ekranda.
      expect(lamp.bulb[0]).toBeGreaterThanOrEqual(40);
      expect(lamp.cable).toBeLessThanOrEqual(140);
      // Namlu sprite açısından en fazla 16° dikleşir; mermi izi gerçekten ampule gider.
      const sprite = OPERATOR_AIM_ANGLES[lamp.shot.aim];
      expect(lamp.angle).toBeGreaterThanOrEqual(sprite);
      expect(lamp.angle - sprite).toBeLessThanOrEqual(16);
      const traced = (Math.atan2(lamp.muzzle[1] - lamp.bulb[1], lamp.muzzle[0] - lamp.bulb[0]) * 180) / Math.PI;
      expect(traced).toBeCloseTo(lamp.angle, 5);
    }
    // İki lamba aynı dikeyde üst üste binmez (biri ötekinin kablosunu kesmez).
    expect(Math.abs(lamps[0].bulb[0] - lamps[1].bulb[0])).toBeGreaterThanOrEqual(40);
  });

  it("dik tablette de lambalar tavandan, farklı yerlerden sarkar", () => {
    const lamps = layoutLamps(blackoutPlan(false), VIEWPORTS[1].view, false);
    expect(lamps.every((lamp) => lamp.mount === "ceiling")).toBe(true);
    const xs = lamps.map((lamp) => lamp.bulb[0]).sort((a, b) => a - b);
    expect(xs[1] - xs[0]).toBeGreaterThanOrEqual(40);
    expect(xs[2] - xs[1]).toBeGreaterThanOrEqual(40);
  });

  it("geniş masaüstünde namlu açısı sprite ile aynıdır (dikleştirme gerekmez)", () => {
    for (const lamp of layoutLamps(blackoutPlan(false), VIEWPORTS[2].view, false)) {
      expect(lamp.angle).toBe(OPERATOR_AIM_ANGLES[lamp.shot.aim]);
    }
  });

  it("geniş masaüstünde lambalar tavandan sarkar ve soldan sağa farklı yerlerdedir", () => {
    const lamps = layoutLamps(blackoutPlan(false), VIEWPORTS[2].view, false);
    expect(lamps.every((lamp) => lamp.mount === "ceiling")).toBe(true);
    const xs = lamps.map((lamp) => Math.round(lamp.bulb[0]));
    expect(new Set(xs).size).toBe(3);
  });
});
