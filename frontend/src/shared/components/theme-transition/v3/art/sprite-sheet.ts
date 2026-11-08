import { AIM_ANGLES, CELL, FRAME_ORDER, PALETTE, POSES, renderFront, renderPose, type FrameAnchors, type PoseName } from "./operator-art";
import { EMPTY, type PixelCanvas } from "./pixel-canvas";

/**
 * V3 operatör sprite sayfası ve karakter sayfası üretimi (yalnız Node / test ortamında çalışır).
 * Çıktılar `v3/assets/` altına yazılır ve depoya eklenir; `sprite-sheet.test.ts` dosyaların
 * kaynakla aynı kaldığını doğrular (LTZ_WRITE_SPRITES=1 ile yeniden üretir).
 */
export type Rgba = { width: number; height: number; rgba: Uint8Array };

function rgbOf(key: string): [number, number, number] {
  const hex = PALETTE[key as keyof typeof PALETTE];
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
}

function strip(canvases: PixelCanvas[]): Rgba {
  const width = canvases.length * CELL;
  const height = CELL;
  const rgba = new Uint8Array(width * height * 4);
  canvases.forEach((canvas, index) => {
    for (let y = 0; y < CELL; y += 1) {
      for (let x = 0; x < CELL; x += 1) {
        const key = canvas.get(x, y);
        if (key !== EMPTY) {
          rgba.set([...rgbOf(key), 255], (y * width + index * CELL + x) * 4);
        }
      }
    }
  });
  return { width, height, rgba };
}

/** Animasyon kareleri, FRAME_ORDER sırasıyla tek satır (64 px hücre, saydam zemin). */
export function buildSpriteSheet(): Rgba {
  return strip(FRAME_ORDER.map((name) => renderPose(POSES[name]).canvas));
}

/**
 * Karakter sayfası (tasarım referansı; çalışma zamanında yüklenmez). Sütunlar:
 * ön, sol, sağ, boşta (NVG yukarı, alçak hazır), silah hazır (nişan), NVG yukarı (elle), NVG aşağı (ışıklı).
 */
export const CHARACTER_SHEET_COLUMNS = ["front", "left", "right", "idle", "weaponReady", "nvgUp", "nvgDown"] as const;

export function buildCharacterSheet(): Rgba {
  const left = renderPose({ legs: POSES.nvgOn.legs, rifle: "slung" }).canvas;
  return strip([
    renderFront(),
    left,
    left.mirrored(),
    renderPose({ legs: POSES.nvgOn.legs, rifle: { angle: -36 } }).canvas,
    renderPose(POSES.aimLow).canvas,
    renderPose(POSES.liftUp).canvas,
    renderPose(POSES.nvgOn).canvas
  ]);
}

function round(anchor: readonly [number, number]): [number, number] {
  return [Math.round(anchor[0] * 2) / 2, Math.round(anchor[1] * 2) / 2];
}

/** Çalışma zamanı meta verisi: kare dizini ve pivot / namlu / mercek noktaları (hücre pikseli). */
export function buildFramesModule(): string {
  const anchors: Partial<Record<PoseName, FrameAnchors>> = {};
  for (const name of FRAME_ORDER) {
    const { anchors: found } = renderPose(POSES[name]);
    const entry: FrameAnchors = {};
    if (found.muzzle) {
      entry.muzzle = round(found.muzzle);
    }
    if (found.lens) {
      entry.lens = round(found.lens);
    }
    if (entry.muzzle || entry.lens) {
      anchors[name] = entry;
    }
  }
  const frames = FRAME_ORDER.map((name, index) => `  ${name}: ${index}`).join(",\n");
  const anchorLines = Object.entries(anchors)
    .map(([name, value]) => {
      const parts = Object.entries(value ?? {}).map(([key, point]) => `${key}: [${point[0]}, ${point[1]}]`);
      return `  ${name}: { ${parts.join(", ")} }`;
    })
    .join(",\n");
  return `/**
 * ÜRETİLMİŞ DOSYA — elle düzenleme. Kaynak: art/operator-art.ts, üretim: art/sprite-sheet.test.ts
 * (LTZ_WRITE_SPRITES=1 npx vitest run src/shared/components/theme-transition/v3/art/sprite-sheet.test.ts).
 * Hücre ${CELL}×${CELL} px; tüm kareler sola bakar, ayaklar y = 63, kalça x = 35 (sabit pivot).
 */
export const OPERATOR_CELL = ${CELL};
export const OPERATOR_FRAME_COUNT = ${FRAME_ORDER.length};

export const OPERATOR_FRAMES = {
${frames}
} as const;

export type OperatorFrame = keyof typeof OPERATOR_FRAMES;

/** Nişan karelerinin namlu açısı (derece, yataydan yukarı, sola doğru). */
export const OPERATOR_AIM_ANGLES = { aimLow: ${AIM_ANGLES.aimLow}, aimMid: ${AIM_ANGLES.aimMid}, aimHigh: ${AIM_ANGLES.aimHigh} } as const;

/** Fişek tabancasının ateş karesindeki (flareKick) namlu açısı; fişek yayı bu yönde başlar. */
export const OPERATOR_FLARE_ANGLE = ${POSES.flareKick.flare.angle};

/** Hücre içi noktalar (px): namlu ağzı (atış / fişek çıkışı) ve NVG merceği (yeşil ışık). */
export const OPERATOR_ANCHORS: Partial<Record<OperatorFrame, { muzzle?: readonly [number, number]; lens?: readonly [number, number] }>> = {
${anchorLines}
};
`;
}
