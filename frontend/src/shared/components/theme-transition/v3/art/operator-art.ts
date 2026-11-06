import {
  EMPTY,
  fillDisc,
  fillLimb,
  fillPolygon,
  outlineOnto,
  PixelCanvas,
  shadeMask,
  solveJoint,
  stamp,
  type Pt
} from "./pixel-canvas";

/**
 * LTZ operatörü — V3 piksel-art karakter kaynağı (yalnız üretimde kullanılır, tarayıcı paketine girmez).
 *
 * Özgün LTZ karakteri: boonie şapka, kızıl sakal, olgun ve gerçekçi oran, zeytin üniforma,
 * şarjör cepli plaka taşıyıcı, eldiven, dizlik, ten rengi bacak kılıfı, bot, kolda küçük LTZ kırmızısı yama,
 * baş bandına takılı NVG, stilize tüfek ve işaret fişeği tabancası.
 *
 * Tüm yan pozlar sola bakar; ayaklar hücrenin altına (y = 63) basar, kalça kökü x = 35'tedir.
 * Kafa, gövde, bot ve aksesuarlar elle çizilmiş ızgaralardır; kollar, bacaklar ve silahlar
 * eklem noktalarından (iskelet + iki kemikli IK) çizilir. Böylece her poz aynı orana ve pivota sahiptir.
 * Sola bakan figürde izleyiciye yakın taraf sol koldur: sağ el (uzak) kabzayı, sol el (yakın) el kundağını tutar.
 */
export const CELL = 64;

export const PALETTE = {
  k: "#14150e",
  // şapka
  H: "#3a4227",
  h: "#555f39",
  j: "#717c4b",
  c: "#2d321f",
  w: "#8e9563",
  q: "#272b1b",
  // ten
  S: "#8a5136",
  s: "#bf7b58",
  a: "#dd9e78",
  e: "#1b1310",
  // sakal / saç
  B: "#5a2513",
  r: "#8d3c1b",
  u: "#b65828",
  y: "#d7803e",
  // üniforma
  O: "#3b4327",
  o: "#57613b",
  l: "#76814f",
  // taşıyıcı
  V: "#272c1d",
  v: "#3c442c",
  x: "#56603f",
  P: "#1f2317",
  p: "#474f35",
  // eldiven / dizlik
  G: "#1c1d17",
  g: "#33352b",
  z: "#4d5042",
  // ten rengi kılıf
  T: "#6a5435",
  t: "#977c53",
  i: "#c2a676",
  // bot
  D: "#1d1913",
  d: "#3a3125",
  f: "#57493a",
  // metal
  M: "#17181b",
  m: "#2b2e33",
  A: "#454a51",
  E: "#6c737c",
  // NVG merceği (yeşil yalnız burada)
  N: "#123d22",
  L: "#7dffa0",
  // LTZ kırmızısı
  R: "#e11d2e",
  X: "#97131f",
  // işaret fişeği tabancası
  F: "#9c3d17",
  b: "#e46c26",
  Z: "#ffb04a"
} as const;

export type PaletteKey = keyof typeof PALETTE;

const OUTLINE = "k";
const HIP: Pt = [35, 37];
const HAT_KEYS = new Set(["H", "h", "j", "c", "w", "q"]);

const ARM_UPPER = 9.5;
const ARM_LOWER = 8.5;
const LEG_UPPER = 12;
const LEG_LOWER = 11.5;

/* ───────────────────────── elle çizilmiş parçalar ───────────────────────── */

/** Kafa (sola bakar). Boyun noktası (8, 14). */
const HEAD_BASE = [
  ".....jjjjj.....",
  "....jhwhhhH....",
  "....hhhhchhhH..",
  "...Hqqqqqqqqq..",
  ".jjhhhhhhhhhhhH",
  "jhHSSSSSSSShhHH",
  "...SBBsssSBB...",
  "...aeassSSB....",
  "..aasssSsSB....",
  "..ruruusSSB....",
  "..ryuuuurrB....",
  "...ruyuurBB....",
  "...BruurBSS....",
  "....BrrBSSS....",
  ".......SSSS...."
];
const HEAD_NECK: Pt = [8, 14];

/** Gözlerin önüne indirilmiş NVG (kafa ızgarası koordinatında (-3, 5) köşesine basılır). */
const NVG_DOWN = ["....Am.", "EAAAAm.", "LmmmmM.", "NMMMMM."];
const NVG_DOWN_AT: Pt = [-3, 5];
const NVG_LENS: Pt = [-3, 7];

/** Şapka önünde yukarı kaldırılmış NVG (kafa ızgarası koordinatında (0, 0)). */
const NVG_UP = [".EA.", "AmmA", "AmmM", ".mM."];
const NVG_UP_AT: Pt = [0, 0];

/** Gövde: üniforma yakası, plaka taşıyıcı, şarjör cepleri, kemer. Kalça (7, 15), boyun (8, 0). */
const TORSO = [
  "......OllO.....",
  "....VVvxvvVV...",
  "...VxxvvvvvvV..",
  "..VxxvvvvvvvvV.",
  "..VxvvvvvvvvvV.",
  ".PVxvvvvvvvvvVV",
  "PpPxvvvvvvvvvVV",
  "PppVvvvvvvvvvVV",
  "PpPVvvvvvvvvvVV",
  "PppVvvvvvvvvvV.",
  ".PPVvvvvvvvvvV.",
  "..VVVVVVVVVVVV.",
  "..OoooooooooooO",
  "..GGGzGGGGGGGG.",
  "..OooooooooooO.",
  "...OoooooooO..."
];
const TORSO_HIP: Pt = [7, 15];
const TORSO_NECK: Pt = [8, 0];

/** Bot, burnu sola. Bilek noktası (4, 0). */
const BOOT = ["..ddd..", ".dfddd.", "dfddddd", "DDDDDDD"];
const BOOT_ANKLE: Pt = [4, 0];

const KNEE_PAD = ["gzg", "gzg", "GgG"];
const HOLSTER = ["TtT.", "TitT", "TttT", "TttT", ".TT."];

/* Ön görünüm (karakter sayfası için). */
const HEAD_FRONT = [
  "....jjjjjjj....",
  "...jhwhhhhhH...",
  "...hhhhhhhhhH..",
  "..qqqqqqqqqqq..",
  "jjhhhhhhhhhhhHH",
  "jhHSSSSSSSSShHH",
  "...SBBsssBBS...",
  "...saeassaeS...",
  "...asssSsssS...",
  "...ruusSsuuB...",
  "...ryuuuuurB...",
  "....ruyyyur....",
  ".....rruurr....",
  "......BrB......",
  "......SSS......"
];
const HEAD_FRONT_NECK: Pt = [7, 14];

const TORSO_FRONT = [
  ".....VOlllOV.....",
  "...VVvxlllxvVV...",
  "..VxvvvvvvvvvxV..",
  "..VxvvvvvvvvvvV..",
  "..VxvvvvvvvvvvV..",
  "..VxvvvvvvvvvvV..",
  "..VPPPPPPPPPPPV..",
  "..VPpPpPpPpPpPV..",
  "..VPpPpPpPpPpPV..",
  "..VPpPpPpPpPpPV..",
  "..VPPPPPPPPPPPV..",
  "..VVVVVVVVVVVVV..",
  "...OoooooooooO...",
  "...GGGGzzzGGGG...",
  "...OoooooooooO...",
  "....OoooooooO...."
];
const TORSO_FRONT_HIP: Pt = [8, 15];
const BOOT_FRONT = [".ddd.", "dfddd", "DDDDD"];

/* ───────────────────────── poz tanımı ───────────────────────── */

export type HeadState = "nvgUp" | "nvgDown" | "nvgGlow";

export type Pose = {
  /** Tüm gövde kayması (yürüyüş zıplaması, çömelme). */
  offset?: Pt;
  /** Üst gövdenin geriye (sağa) yaslanması; dik nişanda yüz tüfeğin arkasında kalır. */
  lean?: number;
  legs: { near: Pt; far: Pt };
  head?: HeadState;
  /** Şapka 1 px kalkık (şapka düzeltme). */
  hatLift?: boolean;
  /** Tüfek: dipçik omuzda, açı derece (pozitif = yukarı-sola). `kick` geri tepme (px). `slung` sırtta. */
  rifle?: { angle: number; kick?: number; oneHand?: boolean } | "slung";
  /** Kollar silah tutmuyorsa elin hedef noktası; `nvg`/`brim` kafaya göre hesaplanır. */
  hands?: { near?: Pt | "nvg" | "nvgLow" | "brim" | "brimHigh"; far?: Pt };
  /** Fişek tabancası yakın elde: namlu açısı, kolun uzanma mesafesi ve geri tepme. */
  flare?: { angle: number; reach?: number; kick?: number };
};

type Rig = { hip: Pt; neck: Pt; shoulderNear: Pt; shoulderFar: Pt; headOrigin: Pt };

function add(a: Pt, b: Pt): Pt {
  return [a[0] + b[0], a[1] + b[1]];
}

/** origin'den `angle` (0 = sola, pozitif = yukarı) yönünde `distance`, normal yönünde `side` kadar. */
function along(origin: Pt, angle: number, distance: number, side = 0): Pt {
  const radians = (angle * Math.PI) / 180;
  const dir: Pt = [-Math.cos(radians), -Math.sin(radians)];
  const normal: Pt = [-dir[1], dir[0]];
  return [origin[0] + dir[0] * distance + normal[0] * side, origin[1] + dir[1] * distance + normal[1] * side];
}

function rigFor(pose: Pose): Rig {
  const hip = add(HIP, pose.offset ?? [0, 0]);
  const lean = pose.lean ?? 0;
  const neck: Pt = [hip[0] + TORSO_NECK[0] - TORSO_HIP[0] + lean, hip[1] + TORSO_NECK[1] - TORSO_HIP[1]];
  const headOrigin: Pt = [Math.round(neck[0]) - HEAD_NECK[0], Math.round(neck[1]) + 1 - HEAD_NECK[1]];
  return {
    hip,
    neck,
    headOrigin,
    shoulderNear: [neck[0] - 1 - lean * 0.2, neck[1] + 3.5],
    shoulderFar: [neck[0] + 1.5 - lean * 0.2, neck[1] + 3]
  };
}

function shadedLimb(segments: { a: Pt; b: Pt; ra: number; rb: number }[], ramp: readonly [string, string, string]) {
  const mask = new PixelCanvas(CELL, CELL);
  for (const { a, b, ra, rb } of segments) {
    fillLimb(mask, a, b, ra, rb, "#");
  }
  return shadeMask(mask, ramp);
}

function layer(target: PixelCanvas, part: PixelCanvas, separate: boolean) {
  outlineOnto(target, part, OUTLINE, !separate);
  target.draw(part);
}

function lerp(a: Pt, b: Pt, t: number): Pt {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
}

function drawLeg(target: PixelCanvas, hip: Pt, ankle: Pt, near: boolean) {
  // Figür sola bakar: diz öne (sola, bend = 1) kırılır; ters yön "geri dönük diz" gibi yamuk görünür.
  const knee = solveJoint(hip, ankle, LEG_UPPER, LEG_LOWER, 1);
  const ramp = near ? (["O", "o", "l"] as const) : (["c", "O", "o"] as const);
  // Uyluk kalın, baldır arkada (sağda) şişkin, bilek ince: tüp değil insan bacağı.
  const calf = add(lerp(knee, ankle, 0.35), [0.8, 0]);
  const part = shadedLimb(
    [
      { a: hip, b: knee, ra: 3.3, rb: 2.4 },
      { a: knee, b: calf, ra: 2.3, rb: 2.5 },
      { a: calf, b: ankle, ra: 2.5, rb: 1.6 }
    ],
    ramp
  );
  stamp(part, KNEE_PAD, Math.round(knee[0]) - 2, Math.round(knee[1]) - 1);
  if (near) {
    const mid = lerp(hip, knee, 0.5);
    stamp(part, HOLSTER, Math.round(mid[0]) - 1, Math.round(mid[1]) - 2);
  }
  stamp(part, BOOT, Math.round(ankle[0]), Math.round(ankle[1]), { anchor: BOOT_ANKLE });
  layer(target, part, near);
}

function drawArm(target: PixelCanvas, shoulder: Pt, hand: Pt, near: boolean, bend: 1 | -1 = 1) {
  const elbow = solveJoint(shoulder, hand, ARM_UPPER, ARM_LOWER, bend);
  const ramp = near ? (["O", "o", "l"] as const) : (["c", "O", "o"] as const);
  const part = shadedLimb(
    [
      { a: shoulder, b: elbow, ra: 2.6, rb: 2 },
      { a: elbow, b: hand, ra: 2, rb: 1.5 }
    ],
    ramp
  );
  if (near) {
    // Kolda küçük LTZ kırmızısı yama.
    const patch = lerp(shoulder, elbow, 0.35);
    const px = Math.round(patch[0] - 1);
    const py = Math.round(patch[1] - 1);
    part.set(px, py, "R");
    part.set(px + 1, py, "R");
    part.set(px, py + 1, "X");
    part.set(px + 1, py + 1, "R");
  }
  const glove = new PixelCanvas(CELL, CELL);
  fillDisc(glove, hand[0], hand[1], 1.9, "#");
  part.draw(shadeMask(glove, ["G", "g", "z"]));
  layer(target, part, near);
}

/** Tüfek: dipçik, gövde, şarjör, nişangâh, el kundağı, namlu. Namlu ağzı noktasını döndürür. */
function drawRifle(target: PixelCanvas, butt: Pt, angle: number): Pt {
  const mask = new PixelCanvas(CELL, CELL);
  const quad = (from: number, to: number, top: number, bottom: number, key: string) =>
    fillPolygon(
      mask,
      [along(butt, angle, from, -top), along(butt, angle, to, -top), along(butt, angle, to, bottom), along(butt, angle, from, bottom)],
      key
    );
  quad(0, 6, 1.6, 1.9, "m"); // dipçik
  quad(6, 15, 1.4, 1.6, "m"); // gövde
  quad(9, 12.5, 2.7, -1.2, "A"); // nişangâh
  quad(11.5, 14, -1.4, 5, "M"); // şarjör
  quad(15, 22, 1.1, 1.2, "m"); // el kundağı
  quad(22, 27.5, 0.55, 0.55, "M"); // namlu
  quad(7, 9, -1.4, 3.6, "M"); // kabza
  const shaded = new PixelCanvas(CELL, CELL);
  for (let y = 0; y < CELL; y += 1) {
    for (let x = 0; x < CELL; x += 1) {
      const key = mask.get(x, y);
      if (key === "m" && !mask.filled(x, y - 1)) {
        shaded.set(x, y, "E");
      } else if (key === "m" && !mask.filled(x, y + 1)) {
        shaded.set(x, y, "M");
      } else {
        shaded.set(x, y, key);
      }
    }
  }
  layer(target, shaded, true);
  return along(butt, angle, 28);
}

/** Sırtta çapraz taşınan tüfek (namlu sağ omuzun arkasından yukarı çıkar). */
function drawSlungRifle(target: PixelCanvas, rig: Rig) {
  const butt: Pt = [rig.hip[0] + 6, rig.hip[1] + 1];
  drawRifle(target, butt, 118);
}

function drawFlarePistol(target: PixelCanvas, hand: Pt, angle: number): Pt {
  const mask = new PixelCanvas(CELL, CELL);
  const quad = (from: number, to: number, top: number, bottom: number, key: string) =>
    fillPolygon(
      mask,
      [along(hand, angle, from, -top), along(hand, angle, to, -top), along(hand, angle, to, bottom), along(hand, angle, from, bottom)],
      key
    );
  quad(-1.5, 7.5, 1.7, 1.1, "b"); // gövde + kalın namlu
  quad(5.5, 7.5, 1.9, 1.3, "F"); // namlu ağzı bileziği
  quad(-1.5, 1.5, -1, 4.2, "F"); // kabza
  quad(0, 6, 1.7, 0.6, "Z"); // üst parlak çizgi
  layer(target, mask, true);
  return along(hand, angle, 8);
}

function drawHead(target: PixelCanvas, rig: Rig, pose: Pose): FrameAnchors {
  const head = new PixelCanvas(CELL, CELL);
  const [ox, oy] = rig.headOrigin;
  const hat = new PixelCanvas(CELL, CELL);
  const face = new PixelCanvas(CELL, CELL);
  HEAD_BASE.forEach((row, y) => {
    for (let x = 0; x < row.length; x += 1) {
      (HAT_KEYS.has(row[x]) ? hat : face).set(ox + x, oy + y, row[x]);
    }
  });
  head.draw(face);
  if (pose.hatLift) {
    // Şapka 1 px kalkar; altından kızıl saç görünür.
    for (let x = ox + 3; x <= ox + 11; x += 1) {
      for (let y = oy + 3; y <= oy + 5; y += 1) {
        if (hat.filled(x, y) && !hat.filled(x, y + 1) && !face.filled(x, y)) {
          head.set(x, y, "r");
        }
      }
    }
    head.draw(hat, 0, -1);
  } else {
    head.draw(hat);
  }
  const anchors: FrameAnchors = {};
  const state = pose.head ?? "nvgUp";
  if (state === "nvgUp") {
    stamp(head, NVG_UP, ox + NVG_UP_AT[0], oy + NVG_UP_AT[1] - (pose.hatLift ? 1 : 0));
  } else {
    const grid = state === "nvgGlow" ? NVG_DOWN : NVG_DOWN.map((row) => row.replace("L", "N"));
    stamp(head, grid, ox + NVG_DOWN_AT[0], oy + NVG_DOWN_AT[1]);
    anchors.lens = [ox + NVG_LENS[0] + 0.5, oy + NVG_LENS[1] + 0.5];
  }
  layer(target, head, false);
  return anchors;
}

export type FrameAnchors = { muzzle?: Pt; lens?: Pt };

function handTarget(spec: NonNullable<Pose["hands"]>["near"], rig: Rig, fallback: Pt): Pt {
  const [ox, oy] = rig.headOrigin;
  switch (spec) {
    case undefined:
      return fallback;
    case "nvg":
      return [ox + 1, oy + 1.5];
    case "nvgLow":
      return [ox - 0.5, oy + 7];
    case "brim":
      return [ox + 0.5, oy + 4.5];
    case "brimHigh":
      return [ox + 0.5, oy + 3.5];
    default:
      return spec;
  }
}

export function renderPose(pose: Pose): { canvas: PixelCanvas; anchors: FrameAnchors } {
  const canvas = new PixelCanvas(CELL, CELL);
  const rig = rigFor(pose);
  const anchors: FrameAnchors = {};
  const nearHip: Pt = [rig.hip[0] - 1, rig.hip[1]];
  const farHip: Pt = [rig.hip[0] + 1.5, rig.hip[1]];

  const rifle = pose.rifle;
  const held = rifle && rifle !== "slung" ? rifle : null;
  const butt: Pt | null = held ? along([rig.shoulderNear[0] - 0.5, rig.shoulderNear[1] + 1.5], held.angle, -(held.kick ?? 0)) : null;

  // Uzak taraf (koyu): bacak, sırttaki tüfek, kabzayı tutan sağ kol.
  drawLeg(canvas, farHip, pose.legs.far, false);
  if (rifle === "slung") {
    drawSlungRifle(canvas, rig);
  }
  const farHand = held && butt ? along(butt, held.angle, 7.8, 2.6) : pose.hands?.far ?? [rig.hip[0] + 1, rig.hip[1] + 5];
  drawArm(canvas, rig.shoulderFar, farHand, false);

  // Gövde (yaslanmada yarım kayar) ve yakın bacak.
  const torso = new PixelCanvas(CELL, CELL);
  stamp(torso, TORSO, Math.round(rig.hip[0] + (pose.lean ?? 0) * 0.5), Math.round(rig.hip[1]), { anchor: TORSO_HIP });
  layer(canvas, torso, false);
  drawLeg(canvas, nearHip, pose.legs.near, true);

  if (held && butt) {
    anchors.muzzle = drawRifle(canvas, butt, held.angle);
  }

  // Kafa tüfeğin üstünde: dik nişanda yanak dipçiğe yaslanır, yüz kaybolmaz.
  Object.assign(anchors, drawHead(canvas, rig, pose));

  // Yakın sol kol: el kundağı, NVG / şapka ya da fişek tabancası.
  let nearHand = handTarget(pose.hands?.near, rig, [rig.hip[0] - 2, rig.hip[1] + 5]);
  if (held && butt && !held.oneHand && pose.hands?.near === undefined) {
    nearHand = along(butt, held.angle, 16.5, 1.8);
  }
  if (pose.flare) {
    const base = pose.hands?.near === undefined ? along(rig.shoulderNear, pose.flare.angle, pose.flare.reach ?? 16) : nearHand;
    nearHand = along(base, pose.flare.angle, -(pose.flare.kick ?? 0));
    anchors.muzzle = drawFlarePistol(canvas, nearHand, pose.flare.angle);
  }
  // Dirsek öne-aşağı kırılır: destek kolu el kundağının altında kalır, başa uzanan kol yüzü kapatmaz.
  drawArm(canvas, rig.shoulderNear, nearHand, true, -1);
  return { canvas, anchors };
}

/** Ön görünüm (karakter sayfası). Tüfek gövde önünde çapraz, NVG yukarıda. */
export function renderFront(): PixelCanvas {
  const canvas = new PixelCanvas(CELL, CELL);
  const hip: Pt = [32, 37];
  const neck: Pt = [32, 22];
  for (const side of [-1, 1] as const) {
    const legHip: Pt = [hip[0] + side * 3, hip[1]];
    const ankle: Pt = [hip[0] + side * 4, 59];
    const part = shadedLimb(
      [
        { a: legHip, b: [legHip[0] + side * 0.3, 48], ra: 3.2, rb: 2.4 },
        { a: [legHip[0] + side * 0.3, 48], b: ankle, ra: 2.4, rb: 1.6 }
      ],
      side < 0 ? ["O", "o", "l"] : ["c", "O", "o"]
    );
    stamp(part, KNEE_PAD, Math.round(legHip[0] + side * 0.3) - 1, 47);
    if (side > 0) {
      stamp(part, HOLSTER, Math.round(legHip[0]) + 1, 40);
    }
    stamp(part, BOOT_FRONT, Math.round(ankle[0]) - 2, 60);
    layer(canvas, part, true);
  }
  const torso = new PixelCanvas(CELL, CELL);
  stamp(torso, TORSO_FRONT, hip[0], hip[1], { anchor: TORSO_FRONT_HIP });
  layer(canvas, torso, false);
  const head = new PixelCanvas(CELL, CELL);
  stamp(head, HEAD_FRONT, neck[0], neck[1] + 1, { anchor: HEAD_FRONT_NECK });
  stamp(head, [".EAAE.", "AmmmmA", ".mMMm."], neck[0] - 3, neck[1] - 13);
  layer(canvas, head, false);
  // Tüfek gövde önünde, dipçik sağ omuzda (izleyicinin solu), namlu aşağı-sağa.
  const rifle = new PixelCanvas(CELL, CELL);
  drawRifle(rifle, [CELL - 1 - (neck[0] - 6), neck[1] + 6], -32);
  const mirrored = rifle.mirrored();
  for (const side of [-1, 1] as const) {
    const shoulder: Pt = [neck[0] + side * 7, neck[1] + 3];
    const hand: Pt = side < 0 ? [neck[0] - 3, neck[1] + 12] : [neck[0] + 8, neck[1] + 16];
    drawArm(canvas, shoulder, hand, side > 0, side < 0 ? -1 : 1);
    if (side < 0) {
      layer(canvas, mirrored, true);
    }
  }
  return canvas;
}

/* ───────────────────────── kare listesi ───────────────────────── */

const ground = (dx: number, lift = 0): Pt => [HIP[0] + dx, 59 - lift];
const stance = { near: ground(-4), far: ground(4) };
const LOW_READY = -36;

/** Nişan karelerinin namlu açıları (derece, yataydan yukarı, sola doğru). Sahne ışını buradan hesaplar. */
export const AIM_ANGLES = { aimLow: 38, aimMid: 51, aimHigh: 63 } as const;

export const POSES = {
  // Gizli yürüyüş (çökük, tüfek alçak hazır, NVG yukarıda).
  walk1: { offset: [0, 2], legs: { near: ground(-7), far: ground(6) }, rifle: { angle: LOW_READY + 4 } },
  walk2: { offset: [0, 1], legs: { near: ground(-1), far: ground(1, 4) }, rifle: { angle: LOW_READY + 2 } },
  walk3: { offset: [0, 2], legs: { near: ground(6), far: ground(-7) }, rifle: { angle: LOW_READY + 4 } },
  walk4: { offset: [0, 1], legs: { near: ground(1, 4), far: ground(-1) }, rifle: { angle: LOW_READY + 2 } },
  // Çömel / kontrol et.
  crouch: { offset: [1, 7], legs: { near: ground(-7), far: ground(5) }, rifle: { angle: -14 } },
  // Doğrul ve tüfeği kaldır.
  raise: { offset: [0, 1], legs: stance, rifle: { angle: 18 } },
  // Üç lamba için üç nişan açısı (AIM_ANGLES); "k" kareleri geri tepme. Namlu 63°'den dik olursa hücreden taşar.
  aimLow: { legs: stance, rifle: { angle: AIM_ANGLES.aimLow } },
  aimLowK: { legs: stance, rifle: { angle: AIM_ANGLES.aimLow + 2, kick: 1.5 } },
  aimMid: { legs: stance, lean: 1, rifle: { angle: AIM_ANGLES.aimMid } },
  aimMidK: { legs: stance, lean: 1, rifle: { angle: AIM_ANGLES.aimMid + 2, kick: 1.5 } },
  aimHigh: { legs: stance, lean: 2, rifle: { angle: AIM_ANGLES.aimHigh } },
  aimHighK: { legs: stance, lean: 2, rifle: { angle: AIM_ANGLES.aimHigh + 2, kick: 1.5 } },
  // NVG'yi elle indir.
  reach: { legs: stance, rifle: { angle: -52, oneHand: true }, hands: { near: "nvg" } },
  nvgPull: { legs: stance, head: "nvgDown", rifle: { angle: -52, oneHand: true }, hands: { near: "nvgLow" } },
  nvgOn: { legs: stance, head: "nvgGlow", rifle: { angle: LOW_READY } },
  // Gün doğumu: tüfek sırtta.
  idleGlow: { legs: stance, head: "nvgGlow", rifle: "slung" },
  liftReach: { legs: stance, head: "nvgDown", rifle: "slung", hands: { near: "nvgLow" } },
  liftUp: { legs: stance, rifle: "slung", hands: { near: "nvg" } },
  draw1: { legs: stance, rifle: "slung", hands: { near: [32, 42] } },
  draw2: { legs: stance, rifle: "slung", flare: { angle: -18 }, hands: { near: [27, 33] } },
  flareAim: { legs: stance, lean: 2, rifle: "slung", flare: { angle: 50 } },
  flareKick: { legs: stance, lean: 3, rifle: "slung", flare: { angle: 58, kick: 2 } },
  hatTouch: { legs: stance, rifle: "slung", hands: { near: "brim" } },
  hatLift: { legs: stance, rifle: "slung", hatLift: true, hands: { near: "brimHigh" } },
  walkS1: { offset: [0, 1], legs: { near: ground(-7), far: ground(6) }, rifle: "slung", hands: { near: [36, 42], far: [30, 41] } },
  walkS2: { legs: { near: ground(-1), far: ground(1, 4) }, rifle: "slung" },
  walkS3: { offset: [0, 1], legs: { near: ground(6), far: ground(-7) }, rifle: "slung", hands: { near: [30, 41], far: [37, 41] } },
  walkS4: { legs: { near: ground(1, 4), far: ground(-1) }, rifle: "slung" }
} satisfies Record<string, Pose>;

export type PoseName = keyof typeof POSES;

/** Sprite sayfasındaki kare sırası (çalışma zamanı bu sıraya göre kare seçer). */
export const FRAME_ORDER = Object.keys(POSES) as PoseName[];

export { EMPTY };
