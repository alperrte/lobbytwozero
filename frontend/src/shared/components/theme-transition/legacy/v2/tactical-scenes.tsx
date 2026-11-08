"use client";

import type { CSSProperties } from "react";
import { useTranslations } from "next-intl";
import { OperatorSprite } from "./operator-sprite";

/**
 * V2 (LTZ taktik) sahneleri. Mantık yok, yalnız işaretleme: zaman çizelgesi tactical.css içindedir
 * ve --fx-dur (örtünün toplam süresi) üzerinden yüzde olarak işler. Sahne metinleri çeviri dosyalarındandır.
 *
 * Blackout (Light → Dark): spot ışıkları, sağdan sızan operatör, ışıkları teker teker söndürme, gece görüş.
 * Daybreak (Dark → Light): fişek, ışığın yayılması, dağ silueti, doğan güneş, selam.
 */

/** Her lamba farklı anda söner (--out, toplam sürenin oranı). Çizelge tactical.css ile birebir uyumludur. */
const lamps = [0.385, 0.435, 0.485, 0.535] as const;

const sparkVectors = [
  [-14, -12],
  [11, -16],
  [-22, 6],
  [18, 10],
  [2, 18]
] as const;

function MuzzleFlash() {
  return (
    <span className="v2-muzzle">
      <svg viewBox="0 0 12 12" shapeRendering="crispEdges" focusable="false">
        <path className="mz-outer" d="M5 0h2v3h2v2h3v2H9v2H7v3H5V9H3V7H0V5h3V3h2z" />
        <path className="mz-core" d="M5 4h2v1h1v2H7v1H5V7H4V5h1z" />
      </svg>
    </span>
  );
}

function Lamp({ index, out }: { index: number; out: number }) {
  const style = { "--i": index, "--out": out } as CSSProperties;
  return (
    <div className="v2-lamp" style={style}>
      <span className="v2-cone" />
      <span className="v2-stem" />
      <span className="v2-lamp-body" />
      <span className="v2-lens" />
      <span className="v2-hit" />
      {sparkVectors.map(([dx, dy], sparkIndex) => (
        <span
          key={sparkIndex}
          className="v2-spark"
          style={{ "--dx": `${dx}px`, "--dy": `${dy}px` } as CSSProperties}
        />
      ))}
    </div>
  );
}

/** Light → Dark: "Operation Blackout". */
export function BlackoutScene() {
  const t = useTranslations("ThemeTransition");
  return (
    <>
      <div className="v2-dim" />
      <div className="v2-lamps">
        <div className="v2-truss" />
        {lamps.map((out, index) => (
          <Lamp key={out} index={index} out={out} />
        ))}
      </div>
      <div className="v2-catwalk" />
      <div className="v2-actor">
        <div className="v2-actor-move">
          <OperatorSprite>
            <MuzzleFlash />
          </OperatorSprite>
        </div>
      </div>
      <div className="v2-nv" />
      <div className="v2-caption">
        <p className="v2-cap-title">{t("blackout.title")}</p>
        <p className="v2-cap-sub">{t("blackout.status")}</p>
      </div>
    </>
  );
}

/** Taktik sahnenin dağ siluetleri: köşeli yüzeyler, ufuk çizgisinde ince sıcak ışık kenarı. */
function TacticalMountains() {
  return (
    <div className="v2-mountains">
    <svg viewBox="0 0 1440 420" preserveAspectRatio="xMidYMax slice" focusable="false">
      <polygon
        className="mt-far"
        points="0,420 0,250 110,190 190,230 300,130 380,200 470,160 590,240 700,120 800,210 900,170 1010,250 1120,150 1230,220 1330,170 1440,230 1440,420"
      />
      <polygon
        className="mt-mid"
        points="0,420 0,310 140,250 230,300 340,215 450,290 560,240 680,310 790,225 910,300 1030,245 1150,305 1270,250 1360,290 1440,262 1440,420"
      />
      <polygon
        className="mt-near"
        points="0,420 0,360 170,320 290,350 430,300 560,345 700,320 850,360 1000,315 1150,350 1290,325 1440,350 1440,420"
      />
      <polyline
        className="mt-rim"
        points="0,250 110,190 190,230 300,130 380,200 470,160 590,240 700,120 800,210 900,170 1010,250 1120,150 1230,220 1330,170 1440,230"
      />
    </svg>
    </div>
  );
}

/** Dark → Light: "Operation Daybreak". */
export function DaybreakScene() {
  const t = useTranslations("ThemeTransition");
  return (
    <>
      <div className="v2-dim" />
      <div className="v2-dawn-sky" />
      <div className="v2-sun-box">
        <div className="v2-sun" />
      </div>
      <TacticalMountains />
      <div className="v2-ground" />
      <div className="v2-actor">
        <div className="v2-actor-move">
          <OperatorSprite>
            <div className="v2-flare-path">
              <span className="v2-burst" />
              <span className="v2-flare-trail" />
              <span className="v2-flare-head" />
            </div>
          </OperatorSprite>
        </div>
      </div>
      <div className="v2-nv" />
      <div className="v2-caption">
        <p className="v2-cap-title">{t("daybreak.title")}</p>
        <p className="v2-cap-sub">{t("daybreak.status")}</p>
      </div>
    </>
  );
}
