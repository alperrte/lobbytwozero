"use client";

import { useLayoutEffect, useRef, type CSSProperties } from "react";
import { useTranslations } from "next-intl";
import spriteAsset from "./assets/operator-sprites.png";
import {
  OPERATOR_ANCHORS,
  OPERATOR_CELL,
  OPERATOR_FLARE_ANGLE,
  OPERATOR_FRAME_COUNT,
  OPERATOR_FRAMES,
  type OperatorFrame
} from "./operator-frames";
import { createScenePlayer, HOLD, readViewport, type ScenePlayer, type TrackPoint } from "./scene-player";
import { pixelDiscPath, RIDGE_VIEW, RIDGES, ridgePath, ridgeRimPath } from "./scenery";
import { anchorOnScreen, layoutLamps, operatorBox, OPERATOR_SCALE, type OperatorBox } from "./stage";
import { blackoutPlan, daybreakPlan, flareArc, type BlackoutPlan, type DaybreakPlan, type FrameCue } from "./timeline";

/**
 * V3 taktik tema geçişi (varsayılan paket): piksel-sanat operatör, sprite kareleri ve WAAPI izleri.
 * Sahne yalnız dekoratiftir (örtü aria-hidden, pointer-events: none); tema denetleyicisi temayı
 * kendi zamanlayıcısıyla değiştirir, sahne aynı çizelgeyi (timeline.ts) görüntüler.
 */
export type V3SceneProps = { compact: boolean; startedAt: number };

// Next statik görsel nesnesi ({ src }) ya da Vite/Vitest URL dizgesi; sprite sahne açılınca yüklenir.
const spriteSource = spriteAsset as unknown as string | { src: string };
const SPRITE_URL = typeof spriteSource === "string" ? spriteSource : spriteSource.src;

const PIXEL = { desktop: 4, compact: 3 } as const;
const TYPE_MS_PER_CHAR = 22;
// Levha sönmeden önce tam metnin okunabileceği en kısa süre; uzun metinde yazma bu süreye sığacak kadar hızlanır.
const CAPTION_READ_MS = 400;
const TYPE_MS_PER_CHAR_MIN = 10;
const LINE_GAP_MS = 40;

type Query = {
  one(part: string): HTMLElement | null;
  all(part: string): HTMLElement[];
};

function query(root: HTMLElement): Query {
  return {
    one: (part) => root.querySelector<HTMLElement>(`[data-part="${part}"]`),
    all: (part) => Array.from(root.querySelectorAll<HTMLElement>(`[data-part="${part}"]`))
  };
}

function px(value: number): string {
  return `${Math.round(value * 10) / 10}px`;
}

function framePosition(frame: OperatorFrame, size: number): string {
  return `${-OPERATOR_FRAMES[frame] * size}px 0px`;
}

function frameTrack(frames: readonly FrameCue[], size: number): TrackPoint[] {
  return frames.map(([ms, frame]) => [ms, framePosition(frame, size), HOLD] as const);
}

/** Hücre içi noktayı operatör kutusuna göre konumlar (parlama, NVG ışığı). */
function pinToCell(element: HTMLElement | null, box: OperatorBox, frame: OperatorFrame, kind: "muzzle" | "lens") {
  const point = OPERATOR_ANCHORS[frame]?.[kind];
  if (element && point) {
    element.style.left = px(point[0] * box.scale);
    element.style.top = px(point[1] * box.scale);
  }
}

function placeOperator(element: HTMLElement | null, box: OperatorBox) {
  if (element) {
    element.style.left = px(box.left);
    element.style.top = px(box.top);
  }
}

const CAPTION_GAP = 12;

/**
 * Telsiz levhası operatörün soluna, ayak hizasına yerleşir: konuşma ona aittir ve
 * levha kenar çubuğu gibi kabuk öğelerinin üstüne binmez.
 */
function placeCaption(element: HTMLElement | null, box: OperatorBox, view: { width: number; height: number }) {
  if (element) {
    element.style.left = "auto";
    element.style.right = px(view.width - box.left + CAPTION_GAP);
    element.style.bottom = px(Math.max(8, view.height - (box.top + box.size)));
    element.style.maxWidth = px(Math.max(160, box.left - CAPTION_GAP * 2));
  }
}

/** Telsiz levhası: satırlar daktilo gibi sırayla yazılır, levha sahne sonunda söner. */
function playCaption(player: ScenePlayer, q: Query, startAt: number, fadeOutAt: number) {
  player.track(q.one("caption"), "opacity", [
    [startAt, 0],
    [startAt + 90, 1],
    [fadeOutAt, 1],
    [fadeOutAt + 220, 0]
  ]);
  let at = startAt + 60;
  const lines = q.all("caption-line").map((line) => ({ line, length: Math.max(1, (line.textContent ?? "").length) }));
  const chars = lines.reduce((sum, { length }) => sum + length, 0);
  const budget = fadeOutAt - CAPTION_READ_MS - at - LINE_GAP_MS * Math.max(0, lines.length - 1);
  const msPerChar = Math.max(TYPE_MS_PER_CHAR_MIN, Math.min(TYPE_MS_PER_CHAR, budget / Math.max(1, chars)));
  for (const { line, length } of lines) {
    const end = Math.round(at + length * msPerChar);
    player.track(line, "clipPath", [
      [at, "inset(0 100% 0 0)", `steps(${length}, end)`],
      [end, "inset(0 0% 0 0)"]
    ]);
    at = end + LINE_GAP_MS;
  }
}

function sceneStyle(compact: boolean, firstFrame: OperatorFrame): CSSProperties {
  const size = OPERATOR_CELL * (compact ? OPERATOR_SCALE.compact : OPERATOR_SCALE.desktop);
  return {
    "--v3-sprite": `url(${SPRITE_URL})`,
    "--v3-size": `${size}px`,
    "--v3-sheet": `${size * OPERATOR_FRAME_COUNT}px`,
    "--v3-frame": framePosition(firstFrame, size)
  } as CSSProperties;
}

function Operator({ flashes }: { flashes: number }) {
  return (
    <div className="v3-op" data-part="op">
      <div className="v3-op__body" data-part="body" />
      <span className="v3-op__glow" data-part="glow" />
      {Array.from({ length: flashes }, (_, index) => (
        <span key={index} className="v3-flash" data-part="flash" />
      ))}
    </div>
  );
}

function Caption({ lead, sub }: { lead: string; sub: string }) {
  return (
    <div className="v3-caption" data-part="caption">
      <span className="v3-caption__dot" />
      <p className="v3-caption__lead" data-part="caption-line">
        {lead}
      </p>
      <p className="v3-caption__sub" data-part="caption-line">
        {sub}
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------- Karartma (Light → Dark) */

const SCRIM_LEVELS = { desktop: [0.18, 0.45, 0.7, 0.96], compact: [0.22, 0.6, 0.96] } as const;
const BODY_LEVELS = { desktop: [1, 0.82, 0.62, 0.44], compact: [1, 0.7, 0.44] } as const;
const SPARK_DIRECTIONS = [
  [-11, -7],
  [9, -10],
  [-7, 9],
  [12, 5]
] as const;

function runBlackout(root: HTMLElement, plan: BlackoutPlan, compact: boolean, player: ScenePlayer) {
  const q = query(root);
  const view = readViewport();
  const box = operatorBox(view, compact);
  const lamps = layoutLamps(plan, view, compact);
  const last = plan.shots[plan.shots.length - 1];

  // Ortam: her sönen lamba ekranı bir basamak karartır; son lambada tema değişir, sonra örtü açılır.
  const scrim = compact ? SCRIM_LEVELS.compact : SCRIM_LEVELS.desktop;
  player.track(q.one("scrim"), "opacity", [
    [0, 0],
    [260, scrim[0], HOLD],
    ...plan.shots.map((item, index) => [item.lampOffAt, scrim[index + 1], item === last ? "linear" : HOLD] as const),
    [plan.fadeOutAt, scrim[scrim.length - 1]],
    [plan.totalMs - 40, 0]
  ]);

  const lampEls = q.all("lamp");
  const tracers = q.all("tracer");
  lamps.forEach((lamp, index) => {
    const element = lampEls[index];
    if (!element) {
      return;
    }
    const { shot } = lamp;
    element.style.left = px(lamp.bulb[0]);
    element.style.top = px(lamp.bulb[1]);
    element.dataset.mount = lamp.mount;
    element.style.setProperty("--v3-cable", px(lamp.cable));
    element.style.setProperty("--v3-arm", px(lamp.bulb[0]));

    player.track(element, "opacity", [
      [index * 70, 0],
      [index * 70 + 200, 1]
    ]);
    player.track(element, "translate", [
      [index * 70, "0px -10px"],
      [index * 70 + 260, "0px 0px"]
    ]);
    if (lamp.mount === "ceiling") {
      // İsabetten sonra kablonun tepesi etrafında küçük bir salınım (patlama ya da sarsıntı yok).
      element.style.transformOrigin = `0px ${px(-lamp.cable)}`;
      player.track(element, "rotate", [
        [shot.hitAt, "0deg"],
        [shot.hitAt + 120, "5deg"],
        [shot.hitAt + 260, "-3deg"],
        [shot.hitAt + 400, "1.5deg"],
        [shot.hitAt + 540, "0deg"]
      ]);
    }

    const light = element.querySelector<HTMLElement>("[data-part='light']");
    const idleFlicker: TrackPoint[] = index === 1 ? [[430, 0.7, HOLD], [460, 1, HOLD]] : [];
    player.track(light, "opacity", [
      [0, 1, HOLD],
      ...idleFlicker,
      [shot.hitAt, 0.2, HOLD],
      [shot.hitAt + 14, 0.85, HOLD],
      [shot.hitAt + 28, 0.1, HOLD],
      [shot.lampOffAt, 0]
    ]);

    element.querySelectorAll<HTMLElement>("[data-part='spark']").forEach((spark, sparkIndex) => {
      const [dx, dy] = SPARK_DIRECTIONS[sparkIndex % SPARK_DIRECTIONS.length];
      player.track(spark, "opacity", [
        [shot.hitAt, 1],
        [shot.hitAt + 260, 0]
      ]);
      player.track(spark, "transform", [
        [shot.hitAt, "translate(0px, 0px)"],
        [shot.hitAt + 260, `translate(${dx}px, ${dy + 10}px)`]
      ]);
    });

    const tracer = tracers[index];
    if (tracer) {
      const rotation = `rotate(${lamp.angle - 180}deg)`;
      tracer.style.left = px(lamp.muzzle[0]);
      tracer.style.top = px(lamp.muzzle[1]);
      tracer.style.width = px(lamp.distance);
      player.track(tracer, "transform", [
        [shot.fireAt, `${rotation} scaleX(0)`],
        [shot.hitAt, `${rotation} scaleX(1)`]
      ]);
      player.track(tracer, "opacity", [
        [shot.fireAt, 0, HOLD],
        [shot.fireAt, 1],
        [shot.hitAt + 30, 1],
        [shot.hitAt + 90, 0]
      ]);
    }
  });

  // Operatör: sağ alttan gizli yürüyüş, çömel, nişan, üç atış, NVG'yi elle indir, gölgeye karış.
  const op = q.one("op");
  const body = q.one("body");
  placeOperator(op, box);
  player.track(op, "transform", [
    [plan.walkIn[0], `translateX(${px(box.offRight)})`],
    [plan.walkIn[1], "translateX(0px)"]
  ]);
  player.track(body, "backgroundPosition", frameTrack(plan.frames, box.size));
  const levels = compact ? BODY_LEVELS.compact : BODY_LEVELS.desktop;
  player.track(body, "filter", [
    [0, `brightness(${levels[0]})`, HOLD],
    ...plan.shots.map((item, index) => [item.lampOffAt, `brightness(${levels[index + 1]})`, HOLD] as const),
    [plan.fadeOutAt, `brightness(${levels[levels.length - 1]})`],
    [plan.fadeOutAt + 260, "brightness(0.15)"]
  ]);
  player.track(body, "opacity", [
    [plan.fadeOutAt, 1],
    [plan.fadeOutAt + 260, 0]
  ]);

  const glow = q.one("glow");
  pinToCell(glow, box, "nvgOn", "lens");
  player.track(glow, "opacity", [
    [0, 0, HOLD],
    [plan.glowAt, 0.55, HOLD],
    [plan.glowAt + 40, 0.15, HOLD],
    [plan.glowAt + 80, 1],
    [plan.totalMs - 140, 1],
    [plan.totalMs - 30, 0]
  ]);

  q.all("flash").forEach((flash, index) => {
    const shot = plan.shots[index];
    if (!shot) {
      return;
    }
    pinToCell(flash, box, `${shot.aim}K` as OperatorFrame, "muzzle");
    player.track(flash, "opacity", [
      [shot.fireAt, 0, HOLD],
      [shot.fireAt, 1, HOLD],
      [shot.fireAt + 50, 0]
    ]);
    player.track(flash, "scale", [
      [shot.fireAt, "1"],
      [shot.fireAt + 25, "1.35"],
      [shot.fireAt + 50, "0.6"]
    ]);
  });

  placeCaption(q.one("caption"), box, view);
  playCaption(player, q, plan.captionAt, plan.fadeOutAt);
}

export function BlackoutV3Scene({ compact, startedAt }: V3SceneProps) {
  const t = useTranslations("ThemeTransition.v3.blackout");
  const root = useRef<HTMLDivElement>(null);
  const lampCount = blackoutPlan(compact).shots.length;

  useLayoutEffect(() => {
    const element = root.current;
    if (!element) {
      return;
    }
    const plan = blackoutPlan(compact);
    const player = createScenePlayer(element, plan.totalMs, startedAt);
    if (player.live) {
      runBlackout(element, plan, compact, player);
    }
    return () => player.cancel();
  }, [compact, startedAt]);

  return (
    <div
      ref={root}
      className={`v3 v3-blackout${compact ? " v3--compact" : ""}`}
      style={sceneStyle(compact, "walk1")}
      data-v3="blackout"
    >
      <div className="v3-scrim" data-part="scrim" />
      {Array.from({ length: lampCount }, (_, index) => (
        <div key={index} className="v3-lamp" data-part="lamp" data-mount="ceiling">
          <span className="v3-lamp__mount" />
          <span className="v3-lamp__shade" />
          <span className="v3-lamp__glass" />
          <span className="v3-lamp__light" data-part="light">
            <span className="v3-lamp__cone" />
            <span className="v3-lamp__halo" />
            <span className="v3-lamp__bulb" />
          </span>
          {SPARK_DIRECTIONS.map((_, sparkIndex) => (
            <span key={sparkIndex} className="v3-spark" data-part="spark" />
          ))}
        </div>
      ))}
      {Array.from({ length: lampCount }, (_, index) => (
        <span key={index} className="v3-tracer" data-part="tracer" />
      ))}
      <Operator flashes={lampCount} />
      <Caption lead={t("status")} sub={t("title")} />
    </div>
  );
}

/* ---------------------------------------------------------------- Gün doğumu (Dark → Light) */

const RIDGE_LAYERS = [
  { key: "far", spec: RIDGES.far, night: "#1b1b24", lit: "#b5694a" },
  { key: "mid", spec: RIDGES.mid, night: "#121219", lit: "#7a3f33" },
  { key: "near", spec: RIDGES.near, night: "#0a0a0e", lit: "#3d2423" }
] as const;
const TRAIL_COUNT = 5;
const TRAIL_LAG_MS = 32;

function runDaybreak(root: HTMLElement, plan: DaybreakPlan, compact: boolean, player: ScenePlayer) {
  const q = query(root);
  const view = readViewport();
  const box = operatorBox(view, compact);
  const [sunFrom, sunTo] = plan.sunrise;

  // Gece göğü → şafak ışığı: ışık tam yayıldığında (applyAtMs) tema değişir, sonra örtü açılır.
  player.track(q.one("night"), "opacity", [
    [0, 0],
    [240, 0.88],
    [plan.wash[1], 0.88, HOLD],
    [plan.wash[1] + 10, 0]
  ]);
  player.track(q.one("wash"), "opacity", [
    [plan.wash[0], 0],
    [plan.wash[1], 1],
    [plan.fadeOutAt, 1],
    [plan.totalMs - 60, 0]
  ]);

  const horizon = q.one("horizon");
  const horizonHeight = horizon?.getBoundingClientRect().height ?? view.height * 0.3;
  player.track(horizon, "opacity", [
    [0, 0],
    [260, 1],
    [plan.fadeOutAt, 1],
    [plan.totalMs - 40, 0]
  ]);
  player.track(horizon, "transform", [
    [plan.fadeOutAt, "translateY(0px)"],
    [plan.totalMs, `translateY(${px(horizonHeight * 0.12)})`]
  ]);

  // Güneş sırtların ardından basamaklı (piksel) bir hareketle yükselir.
  const sun = q.one("sun");
  player.track(sun, "transform", [
    [sunFrom, `translateY(${px(horizonHeight * 0.4)})`, "steps(12, end)"],
    [sunTo, `translateY(${px(-horizonHeight * 0.55)})`]
  ]);
  player.track(sun, "opacity", [
    [sunFrom, 0],
    [sunFrom + 140, 1],
    [plan.fadeOutAt, 1],
    [plan.totalMs - 60, 0]
  ]);
  const sunGlow = q.one("sun-glow");
  player.track(sunGlow, "opacity", [
    [sunFrom, 0],
    [sunTo, 0.9],
    [plan.fadeOutAt, 0.9],
    [plan.totalMs - 60, 0]
  ]);
  player.track(sunGlow, "scale", [
    [sunFrom, "0.4"],
    [sunTo, "1.15"]
  ]);

  RIDGE_LAYERS.forEach((layer) => {
    player.track(q.one(`ridge-${layer.key}`), "fill", [
      [sunFrom, layer.night, "steps(5, end)"],
      [plan.applyAtMs, layer.lit]
    ]);
    player.track(q.one(`rim-${layer.key}`), "opacity", [
      [sunFrom + 120, 0, "steps(4, end)"],
      [plan.applyAtMs, 1]
    ]);
  });

  // Operatör: NVG'yi kaldır (ışık söner), fişek tabancasını çek, ateşle, şapkayı düzelt, sağdan çık.
  const op = q.one("op");
  const body = q.one("body");
  placeOperator(op, box);
  player.track(body, "backgroundPosition", frameTrack(plan.frames, box.size));
  player.track(body, "opacity", [
    [0, 0],
    [220, 1]
  ]);
  player.track(body, "filter", [
    [0, "brightness(0.5)"],
    [sunFrom, "brightness(0.5)"],
    [plan.applyAtMs, "brightness(1)"]
  ]);
  player.track(body, "transform", [
    [0, "scaleX(1)", HOLD],
    [plan.exit[0], "scaleX(-1)"]
  ]);
  player.track(op, "transform", [
    [plan.exit[0], "translateX(0px)"],
    [plan.exit[1], `translateX(${px(box.offRight)})`]
  ]);

  const glow = q.one("glow");
  pinToCell(glow, box, "idleGlow", "lens");
  player.track(glow, "opacity", [
    [0, 0],
    [220, 1],
    [plan.glowOffAt, 1, HOLD],
    [plan.glowOffAt + 40, 0.3, HOLD],
    [plan.glowOffAt + 70, 0]
  ]);

  const flash = q.one("flash");
  pinToCell(flash, box, "flareKick", "muzzle");
  player.track(flash, "opacity", [
    [plan.fireAt, 0, HOLD],
    [plan.fireAt, 1, HOLD],
    [plan.fireAt + 60, 0]
  ]);
  player.track(flash, "scale", [
    [plan.fireAt, "1"],
    [plan.fireAt + 30, "1.4"],
    [plan.fireAt + 60, "0.6"]
  ]);

  // Fişek: balistik yay boyunca yükselir, sıcak iz bırakır, tepede parlar ve yavaşça sönerek düşer.
  const muzzle = anchorOnScreen(box, "flareKick", "muzzle");
  const apexY = view.height * (compact ? 0.2 : 0.16);
  const arc = flareArc(muzzle, OPERATOR_FLARE_ANGLE, apexY);
  const apex = arc[arc.length - 1];
  const rise = plan.flareApexAt - plan.fireAt;
  const path = (lag: number): TrackPoint[] =>
    arc.map(([x, y], index) => [plan.fireAt + lag + (rise * index) / (arc.length - 1), `translate(${px(x)}, ${px(y)})`] as const);

  const head = q.one("flare");
  player.track(head, "transform", [
    ...path(0),
    [plan.flareEndAt, `translate(${px(apex[0] - 14)}, ${px(apex[1] + 30)})`]
  ]);
  player.track(head, "opacity", [
    [plan.fireAt, 0, HOLD],
    [plan.fireAt, 1],
    [plan.flareEndAt - 220, 1],
    [plan.flareEndAt, 0]
  ]);
  q.all("trail").forEach((trail, index) => {
    const lag = (index + 1) * TRAIL_LAG_MS;
    player.track(trail, "transform", path(lag));
    player.track(trail, "opacity", [
      [plan.fireAt + lag, 0, HOLD],
      [plan.fireAt + lag, 0.85 - index * 0.13],
      [plan.flareApexAt + lag + 40, 0]
    ]);
  });
  const burst = q.one("burst");
  if (burst) {
    burst.style.left = px(apex[0]);
    burst.style.top = px(apex[1]);
  }
  player.track(burst, "opacity", [
    [plan.flareApexAt - 40, 0],
    [plan.flareApexAt + 60, 0.85],
    [plan.flareApexAt + 520, 0]
  ]);
  player.track(burst, "scale", [
    [plan.flareApexAt - 40, "0.2"],
    [plan.flareApexAt + 520, "1.6"]
  ]);

  placeCaption(q.one("caption"), box, view);
  playCaption(player, q, plan.captionAt, plan.fadeOutAt);
}

export function DaybreakV3Scene({ compact, startedAt }: V3SceneProps) {
  const t = useTranslations("ThemeTransition.v3.daybreak");
  const root = useRef<HTMLDivElement>(null);
  const pixel = compact ? PIXEL.compact : PIXEL.desktop;
  const sunRadius = 14;

  useLayoutEffect(() => {
    const element = root.current;
    if (!element) {
      return;
    }
    const plan = daybreakPlan(compact);
    const player = createScenePlayer(element, plan.totalMs, startedAt);
    if (player.live) {
      runDaybreak(element, plan, compact, player);
    }
    return () => player.cancel();
  }, [compact, startedAt]);

  return (
    <div
      ref={root}
      className={`v3 v3-daybreak${compact ? " v3--compact" : ""}`}
      style={sceneStyle(compact, "idleGlow")}
      data-v3="daybreak"
    >
      <div className="v3-night" data-part="night" />
      <div className="v3-wash" data-part="wash" />
      <div className="v3-horizon" data-part="horizon">
        <div className="v3-sun" data-part="sun" style={{ width: sunRadius * 2 * pixel }}>
          <span className="v3-sun__glow" data-part="sun-glow" />
          <svg viewBox={`0 0 ${sunRadius * 2} ${sunRadius * 2}`} shapeRendering="crispEdges" focusable="false">
            <path d={pixelDiscPath(sunRadius)} className="v3-sun__rim" />
            <path d={pixelDiscPath(sunRadius, 3)} className="v3-sun__core" />
          </svg>
        </div>
        {RIDGE_LAYERS.map((layer) => (
          <svg
            key={layer.key}
            className={`v3-ridge v3-ridge--${layer.key}`}
            viewBox={`0 0 ${RIDGE_VIEW.width} ${RIDGE_VIEW.height}`}
            preserveAspectRatio="none"
            shapeRendering="crispEdges"
            focusable="false"
          >
            <path d={ridgePath(layer.spec)} fill={layer.night} data-part={`ridge-${layer.key}`} />
            <path d={ridgeRimPath(layer.spec)} className="v3-ridge__rim" data-part={`rim-${layer.key}`} />
          </svg>
        ))}
      </div>
      <span className="v3-burst" data-part="burst" />
      {Array.from({ length: TRAIL_COUNT }, (_, index) => (
        <span key={index} className={`v3-trail v3-trail--${index + 1}`} data-part="trail" />
      ))}
      <span className="v3-flare" data-part="flare" />
      <Operator flashes={1} />
      <Caption lead={t("status")} sub={t("title")} />
    </div>
  );
}
