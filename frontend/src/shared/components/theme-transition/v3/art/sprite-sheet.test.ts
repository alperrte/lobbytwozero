// @vitest-environment node
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { deflateSync, inflateSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import { CELL, FRAME_ORDER, POSES, renderPose } from "./operator-art";
import { decodePng, encodePng } from "./png-codec";
import { buildCharacterSheet, buildFramesModule, buildSpriteSheet, CHARACTER_SHEET_COLUMNS, type Rgba } from "./sprite-sheet";

const here = dirname(fileURLToPath(import.meta.url));
const assets = join(here, "..", "assets");
const outputs = {
  sprites: join(assets, "operator-sprites.png"),
  sheet: join(assets, "operator-character-sheet.png"),
  frames: join(here, "..", "operator-frames.ts")
};

const write = process.env.LTZ_WRITE_SPRITES === "1";

function png(image: Rgba) {
  return encodePng(image.width, image.height, image.rgba, (data) => deflateSync(data, { level: 9 }));
}

describe("V3 operatör sprite üretimi", () => {
  it.runIf(write)("varlıkları yeniden üretir", () => {
    mkdirSync(assets, { recursive: true });
    writeFileSync(outputs.sprites, png(buildSpriteSheet()));
    writeFileSync(outputs.sheet, png(buildCharacterSheet()));
    writeFileSync(outputs.frames, buildFramesModule());
  });

  it("depodaki sprite sayfası kaynakla piksel piksel aynıdır", () => {
    expect(existsSync(outputs.sprites)).toBe(true);
    const decoded = decodePng(new Uint8Array(readFileSync(outputs.sprites)), (data) => inflateSync(data));
    const fresh = buildSpriteSheet();
    expect([decoded.width, decoded.height]).toEqual([FRAME_ORDER.length * CELL, CELL]);
    expect(Buffer.from(decoded.rgba).equals(Buffer.from(fresh.rgba))).toBe(true);
  });

  it("karakter sayfası ön, sol, sağ ve dört poz sütunu içerir ve kaynakla aynıdır", () => {
    const decoded = decodePng(new Uint8Array(readFileSync(outputs.sheet)), (data) => inflateSync(data));
    expect(decoded.width).toBe(CHARACTER_SHEET_COLUMNS.length * CELL);
    expect(Buffer.from(decoded.rgba).equals(Buffer.from(buildCharacterSheet().rgba))).toBe(true);
  });

  it("çalışma zamanı kare meta verisi güncel", () => {
    expect(readFileSync(outputs.frames, "utf8").replace(/\r\n/g, "\n")).toBe(buildFramesModule());
  });

  it("her karede zemin saydam, ayaklar alt satıra basar ve figür hücreye sığar", () => {
    for (const name of FRAME_ORDER) {
      const { canvas } = renderPose(POSES[name]);
      const bottom = Array.from({ length: CELL }, (_, x) => canvas.filled(x, CELL - 1)).some(Boolean);
      expect(bottom, `${name} yere basmalı`).toBe(true);
      expect(canvas.filled(0, 0) || canvas.filled(CELL - 1, 0), `${name} köşesi saydam olmalı`).toBe(false);
      const columns = Array.from({ length: CELL }, (_, x) => canvas.filled(x, CELL - 1) ? x : -1).filter((x) => x >= 0);
      // Sabit pivot: ayak izi kalça (x = 35) çevresinde kalır.
      expect(Math.min(...columns)).toBeGreaterThan(20);
      expect(Math.max(...columns)).toBeLessThan(50);
    }
  });

  it("nişan karelerinde namlu ağzı, NVG aşağı karelerinde mercek noktası vardır", () => {
    for (const name of ["aimLow", "aimMid", "aimHigh", "flareAim", "flareKick"] as const) {
      expect(renderPose(POSES[name]).anchors.muzzle, name).toBeDefined();
    }
    for (const name of ["nvgOn", "idleGlow", "nvgPull", "liftReach"] as const) {
      expect(renderPose(POSES[name]).anchors.lens, name).toBeDefined();
    }
    // Yükselen nişan açısı namluyu yukarı taşır; namlu ucu hücrenin içinde kalır (kesilmez).
    const muzzles = (["aimLow", "aimMid", "aimHigh"] as const).map((name) => renderPose(POSES[name]).anchors.muzzle?.[1] ?? -1);
    expect(muzzles[0]).toBeGreaterThan(muzzles[2]);
    for (const name of ["aimLow", "aimLowK", "aimMid", "aimMidK", "aimHigh", "aimHighK", "flareKick"] as const) {
      expect(renderPose(POSES[name]).anchors.muzzle?.[1] ?? -1, name).toBeGreaterThanOrEqual(1);
    }
  });
});
