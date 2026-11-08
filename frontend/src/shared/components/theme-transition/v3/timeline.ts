import { OPERATOR_AIM_ANGLES, type OperatorFrame } from "./operator-frames";

/**
 * V3 sahnelerinin zaman çizelgesi ve geometrisi (saf TS; DOM yok, test edilebilir).
 * Tüm anlar ms cinsindendir ve geçişin başlangıcına göredir. `applyAtMs` değerleri
 * shared/lib/theme/transition-scenes.ts ile birebir aynıdır (timeline.test.ts korur).
 */
export type Pt = readonly [number, number];
export type FrameCue = readonly [ms: number, frame: OperatorFrame];
export type AimFrame = keyof typeof OPERATOR_AIM_ANGLES;
export type Shot = { aim: AimFrame; aimAt: number; fireAt: number; hitAt: number; lampOffAt: number };

export type BlackoutPlan = {
  totalMs: number;
  applyAtMs: number;
  frames: FrameCue[];
  walkIn: readonly [number, number];
  captionAt: number;
  shots: Shot[];
  glowAt: number;
  fadeOutAt: number;
};

export type DaybreakPlan = {
  totalMs: number;
  applyAtMs: number;
  frames: FrameCue[];
  glowOffAt: number;
  fireAt: number;
  flareApexAt: number;
  flareEndAt: number;
  captionAt: number;
  sunrise: readonly [number, number];
  wash: readonly [number, number];
  exit: readonly [number, number];
  fadeOutAt: number;
};

/** Mermi namludan lambaya bu sürede ulaşır; lamba isabetten sonra kısa bir titremeyle söner. */
const TRACER_MS = 40;
const FLICKER_MS = 40;
const RECOIL_MS = 60;

function cycle(names: readonly OperatorFrame[], from: number, to: number, step: number): FrameCue[] {
  const cues: FrameCue[] = [];
  for (let at = from, index = 0; at < to; at += step, index += 1) {
    cues.push([at, names[index % names.length]]);
  }
  return cues;
}

function shot(aim: AimFrame, aimAt: number, fireAt: number): Shot {
  const hitAt = fireAt + TRACER_MS;
  return { aim, aimAt, fireAt, hitAt, lampOffAt: hitAt + FLICKER_MS };
}

function shotFrames(item: Shot): FrameCue[] {
  return [
    [item.aimAt, item.aim],
    [item.fireAt, `${item.aim}K` as OperatorFrame],
    [item.fireAt + RECOIL_MS, item.aim]
  ];
}

const WALK = ["walk1", "walk2", "walk3", "walk4"] as const;
const WALK_SLUNG = ["walkS1", "walkS2", "walkS3", "walkS4"] as const;

/**
 * Light → Dark: gizli yürüyüş, çömel-kontrol, lambaları sırayla vur, NVG'yi elle indir, gölgeye karış.
 * Dar ekranda iki lamba vurulur (orta ve yüksek) ve çizelge kısalır.
 */
export function blackoutPlan(compact: boolean): BlackoutPlan {
  if (compact) {
    const shots = [shot("aimMid", 720, 820), shot("aimHigh", 960, 1060)];
    return {
      totalMs: 2200,
      applyAtMs: shots[1].lampOffAt,
      walkIn: [0, 500],
      captionAt: 520,
      shots,
      glowAt: 1430,
      fadeOutAt: 1900,
      frames: [
        ...cycle(WALK, 0, 500, 100),
        [500, "crouch"],
        [660, "raise"],
        ...shots.flatMap(shotFrames),
        [1200, "reach"],
        [1320, "nvgPull"],
        [1430, "nvgOn"]
      ]
    };
  }
  const shots = [shot("aimLow", 900, 1000), shot("aimMid", 1140, 1240), shot("aimHigh", 1380, 1480)];
  return {
    totalMs: 2600,
    applyAtMs: shots[2].lampOffAt,
    walkIn: [0, 620],
    captionAt: 640,
    shots,
    glowAt: 1880,
    fadeOutAt: 2300,
    frames: [
      ...cycle(WALK, 0, 620, 105),
      [620, "crouch"],
      [820, "raise"],
      ...shots.flatMap(shotFrames),
      [1620, "reach"],
      [1760, "nvgPull"],
      [1880, "nvgOn"]
    ]
  };
}

/**
 * Dark → Light: NVG'yi kaldır (ışık söner), fişek tabancasını çek ve ateşle (geri tepme + toparlanma),
 * fişek yükselir, sırtların ardından güneş doğar ve sıcak ışık yayılır, şapkayı düzelt, çık.
 */
export function daybreakPlan(compact: boolean): DaybreakPlan {
  if (compact) {
    return {
      totalMs: 2200,
      applyAtMs: 1500,
      glowOffAt: 330,
      fireAt: 740,
      flareApexAt: 1150,
      flareEndAt: 1650,
      captionAt: 820,
      sunrise: [920, 1560],
      wash: [1080, 1500],
      exit: [1820, 2200],
      fadeOutAt: 1880,
      frames: [
        [0, "idleGlow"],
        [220, "liftReach"],
        [330, "liftUp"],
        [440, "draw1"],
        [530, "draw2"],
        [630, "flareAim"],
        [740, "flareKick"],
        [820, "flareAim"],
        [1540, "hatTouch"],
        [1640, "hatLift"],
        [1740, "hatTouch"],
        ...cycle(WALK_SLUNG, 1820, 2200, 80)
      ]
    };
  }
  return {
    totalMs: 2600,
    applyAtMs: 1770,
    glowOffAt: 420,
    fireAt: 900,
    flareApexAt: 1400,
    flareEndAt: 2000,
    captionAt: 1000,
    sunrise: [1150, 1900],
    wash: [1300, 1770],
    exit: [2140, 2600],
    fadeOutAt: 2250,
    frames: [
      [0, "idleGlow"],
      [300, "liftReach"],
      [420, "liftUp"],
      [560, "draw1"],
      [660, "draw2"],
      [780, "flareAim"],
      [900, "flareKick"],
      [990, "flareAim"],
      [1820, "hatTouch"],
      [1940, "hatLift"],
      [2060, "hatTouch"],
      ...cycle(WALK_SLUNG, 2140, 2600, 80)
    ]
  };
}

/** `ms` anında görünen kare (son başlamış ipucu). */
export function frameAt(frames: readonly FrameCue[], ms: number): OperatorFrame {
  let current = frames[0][1];
  for (const [at, frame] of frames) {
    if (at > ms) {
      break;
    }
    current = frame;
  }
  return current;
}

/** Namlu yönü: açı yataydan yukarı, sola doğru (sprite'lar sola bakar). */
export function aimDirection(angleDeg: number): Pt {
  const radians = (angleDeg * Math.PI) / 180;
  return [-Math.cos(radians), -Math.sin(radians)];
}

/**
 * Lambayı namlu ışını üzerine yerleştirir: tercihen `hangY` yüksekliğinde; ışın o yüksekliğe
 * ekran dışında ulaşıyorsa sol kenar payında durur (lamba daha alçakta, kablo daha uzun asılır).
 */
export function placeOnRay(muzzle: Pt, angleDeg: number, hangY: number, minX: number): Pt {
  const [dx, dy] = aimDirection(angleDeg);
  let distance = (hangY - muzzle[1]) / dy;
  if (muzzle[0] + dx * distance < minX) {
    distance = (minX - muzzle[0]) / dx;
  }
  return [muzzle[0] + dx * distance, muzzle[1] + dy * distance];
}

/**
 * Fişek yörüngesi: namludan atış açısıyla çıkar, tepe noktasına yavaşlayarak yükselir (balistik yay).
 * `samples` adet nokta döner; ilk nokta namlu, son nokta tepe.
 */
export function flareArc(muzzle: Pt, angleDeg: number, apexY: number, samples = 10): Pt[] {
  const [dx, dy] = aimDirection(angleDeg);
  const rise = Math.max(muzzle[1] - apexY, 1);
  // Dikey hız tepe noktasında sıfırlanır: y(t) = muzzle.y - rise * (2t - t²), x doğrusal ilerler.
  const run = (rise * dx) / -dy;
  return Array.from({ length: samples }, (_, index) => {
    const t = index / (samples - 1);
    return [muzzle[0] + run * t * 0.8, muzzle[1] - rise * (2 * t - t * t)] as const;
  });
}
