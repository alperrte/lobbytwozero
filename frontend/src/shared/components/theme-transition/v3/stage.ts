import { OPERATOR_AIM_ANGLES, OPERATOR_ANCHORS, OPERATOR_CELL, type OperatorFrame } from "./operator-frames";
import { placeOnRay, type BlackoutPlan, type Pt, type Shot } from "./timeline";

/**
 * V3 sahne yerleşimi (saf TS): operatörün ekrandaki kutusu, lambaların namlu ışını üzerindeki yeri.
 * Tüm değerler CSS pikselidir ve görünür alana (viewport) göredir.
 */
export type Viewport = { width: number; height: number; headerBottom: number };

export type OperatorBox = {
  left: number;
  top: number;
  /** Piksel büyütme katsayısı (nearest-neighbour). */
  scale: number;
  /** Ekrandaki hücre kenarı (OPERATOR_CELL × scale). */
  size: number;
  /** Ekranın sağ kenarı dışına çıkmak için gereken yatay kayma. */
  offRight: number;
};

export const OPERATOR_SCALE = { desktop: 2, compact: 1.5 } as const;

export function operatorBox(view: Viewport, compact: boolean): OperatorBox {
  const scale = compact ? OPERATOR_SCALE.compact : OPERATOR_SCALE.desktop;
  const size = OPERATOR_CELL * scale;
  const right = compact ? 12 : Math.max(24, Math.round(view.width * 0.06));
  const bottom = compact ? 16 : 24;
  const left = Math.round(view.width - right - size);
  const top = Math.round(view.height - bottom - size);
  return { left, top, scale, size, offRight: view.width - left + 8 };
}

/** Kare içi noktayı (hücre pikseli) ekran noktasına çevirir. */
export function anchorOnScreen(box: OperatorBox, frame: OperatorFrame, kind: "muzzle" | "lens"): Pt {
  const point = OPERATOR_ANCHORS[frame]?.[kind] ?? [OPERATOR_CELL / 2, OPERATOR_CELL / 2];
  return [box.left + point[0] * box.scale, box.top + point[1] * box.scale];
}

export type LampLayout = {
  shot: Shot;
  angle: number;
  /** Ampul merkezi (mermi burada isabet eder). */
  bulb: Pt;
  /** Mermi izinin başladığı namlu ağzı ve uzunluğu. */
  muzzle: Pt;
  distance: number;
  /** Tavan kablosu (navbar altından sarkar) ya da sol duvara bağlı kol. */
  mount: "ceiling" | "wall";
  cable: number;
};

/** Lambaların navbar altından sarkma payı (vuruş sırasıyla) ve sol kenar payı. */
const HANG = { desktop: [64, 40, 88], compact: [48, 80] } as const;
const MIN_X = { desktop: 40, compact: 28 } as const;
/** Bundan uzun kablo yerine lamba sol duvara bağlanır (dar / dik ekranlar). */
const MAX_CABLE = 140;

export function layoutLamps(plan: BlackoutPlan, view: Viewport, compact: boolean): LampLayout[] {
  const box = operatorBox(view, compact);
  const hang = compact ? HANG.compact : HANG.desktop;
  const minX = compact ? MIN_X.compact : MIN_X.desktop;
  return plan.shots.map((shot, index) => {
    const angle = OPERATOR_AIM_ANGLES[shot.aim];
    const muzzle = anchorOnScreen(box, shot.aim, "muzzle");
    const bulb = placeOnRay(muzzle, angle, view.headerBottom + hang[index % hang.length], minX);
    const cable = Math.max(0, bulb[1] - view.headerBottom);
    return {
      shot,
      angle,
      bulb,
      muzzle,
      distance: Math.hypot(bulb[0] - muzzle[0], bulb[1] - muzzle[1]),
      mount: cable > MAX_CABLE ? "wall" : "ceiling",
      cable
    };
  });
}
