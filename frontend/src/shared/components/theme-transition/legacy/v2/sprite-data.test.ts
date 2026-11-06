import { describe, expect, it } from "vitest";
import {
  compiledLayers,
  compileLayer,
  SPRITE_HEIGHT,
  SPRITE_WIDTH,
  spriteLayers,
  spritePaletteKeys,
  type SpriteLayerName
} from "./sprite-data";

const layerNames = Object.keys(spriteLayers) as SpriteLayerName[];

describe("LTZ operatör sprite verisi", () => {
  it.each(layerNames)("%s katmanı ızgaraya sığar ve yalnız palet renkleri kullanır", (name) => {
    for (const [row, column, text] of spriteLayers[name]) {
      expect(row, `${name} satır`).toBeGreaterThanOrEqual(0);
      expect(row, `${name} satır`).toBeLessThan(SPRITE_HEIGHT);
      expect(column, `${name} sütun`).toBeGreaterThanOrEqual(0);
      expect(column + text.length, `${name} "${text}" taşıyor`).toBeLessThanOrEqual(SPRITE_WIDTH);
      for (const char of text) {
        expect(char === "." || (spritePaletteKeys as readonly string[]).includes(char), `${name}: ${char}`).toBe(true);
      }
    }
  });

  it("her katman en az bir dolu piksel üretir", () => {
    for (const name of layerNames) {
      expect(compiledLayers[name].length, name).toBeGreaterThan(0);
    }
  });

  it("derleme çıktısı deterministiktir ve renk başına tek path verir", () => {
    const first = compileLayer(spriteLayers.body);
    const second = compileLayer(spriteLayers.body);
    expect(first).toEqual(second);
    expect(new Set(first.map((run) => run.key)).size).toBe(first.length);
  });

  it("yatay koşulları birleştirir ve sonraki parça öncekini ezer", () => {
    const runs = compileLayer([
      [0, 0, "hhhh"],
      [0, 2, "ss"]
    ]);
    expect(runs).toEqual([
      { key: "h", d: "M0 0h2v1h-2z" },
      { key: "s", d: "M2 0h2v1h-2z" }
    ]);
  });

  it("ızgara dışına taşan parçalar sessizce kırpılır", () => {
    const runs = compileLayer([[0, SPRITE_WIDTH - 1, "hh"]]);
    expect(runs).toEqual([{ key: "h", d: `M${SPRITE_WIDTH - 1} 0h1v1h-1z` }]);
  });

  it("sahnenin kullandığı yüz, gövde, bacak ve poz katmanları tanımlıdır", () => {
    for (const name of ["body", "legsStand", "legsA", "legsB", "carry", "aim", "flare", "salute", "gogglesUp", "gogglesDown"] as const) {
      expect(spriteLayers[name].length).toBeGreaterThan(0);
    }
  });
});
