/**
 * ÜRETİLMİŞ DOSYA — elle düzenleme. Kaynak: art/operator-art.ts, üretim: art/sprite-sheet.test.ts
 * (LTZ_WRITE_SPRITES=1 npx vitest run src/shared/components/theme-transition/v3/art/sprite-sheet.test.ts).
 * Hücre 64×64 px; tüm kareler sola bakar, ayaklar y = 63, kalça x = 35 (sabit pivot).
 */
export const OPERATOR_CELL = 64;
export const OPERATOR_FRAME_COUNT = 28;

export const OPERATOR_FRAMES = {
  walk1: 0,
  walk2: 1,
  walk3: 2,
  walk4: 3,
  crouch: 4,
  raise: 5,
  aimLow: 6,
  aimLowK: 7,
  aimMid: 8,
  aimMidK: 9,
  aimHigh: 10,
  aimHighK: 11,
  reach: 12,
  nvgPull: 13,
  nvgOn: 14,
  idleGlow: 15,
  liftReach: 16,
  liftUp: 17,
  draw1: 18,
  draw2: 19,
  flareAim: 20,
  flareKick: 21,
  hatTouch: 22,
  hatLift: 23,
  walkS1: 24,
  walkS2: 25,
  walkS3: 26,
  walkS4: 27
} as const;

export type OperatorFrame = keyof typeof OPERATOR_FRAMES;

/** Nişan karelerinin namlu açısı (derece, yataydan yukarı, sola doğru). */
export const OPERATOR_AIM_ANGLES = { aimLow: 38, aimMid: 51, aimHigh: 63 } as const;

/** Fişek tabancasının ateş karesindeki (flareKick) namlu açısı; fişek yayı bu yönde başlar. */
export const OPERATOR_FLARE_ANGLE = 58;

/** Hücre içi noktalar (px): namlu ağzı (atış / fişek çıkışı) ve NVG merceği (yeşil ışık). */
export const OPERATOR_ANCHORS: Partial<Record<OperatorFrame, { muzzle?: readonly [number, number]; lens?: readonly [number, number] }>> = {
  walk1: { muzzle: [11, 44] },
  walk2: { muzzle: [11.5, 43.5] },
  walk3: { muzzle: [11, 44] },
  walk4: { muzzle: [11.5, 43.5] },
  crouch: { muzzle: [8.5, 41] },
  raise: { muzzle: [8, 19.5] },
  aimLow: { muzzle: [12.5, 10] },
  aimLowK: { muzzle: [14, 10] },
  aimMid: { muzzle: [17.5, 5] },
  aimMidK: { muzzle: [19.5, 6] },
  aimHigh: { muzzle: [23.5, 2] },
  aimHighK: { muzzle: [25, 3] },
  reach: { muzzle: [17.5, 49] },
  nvgPull: { muzzle: [17.5, 49], lens: [25.5, 16.5] },
  nvgOn: { muzzle: [12, 43.5], lens: [25.5, 16.5] },
  idleGlow: { lens: [25.5, 16.5] },
  liftReach: { lens: [25.5, 16.5] },
  draw2: { muzzle: [19.5, 35.5] },
  flareAim: { muzzle: [21, 7] },
  flareKick: { muzzle: [25.5, 7] }
};
