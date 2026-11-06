"use client";

import { useEffect, type CSSProperties } from "react";
import { useThemeTransition } from "@/shared/hooks/use-theme-transition";
import {
  finishThemeTransition,
  TRANSITION_TOTAL_MS
} from "@/shared/lib/theme/theme-transition";
import {
  BoltScene,
  CityScene,
  CloudScene,
  RainScene,
  RidgeScene
} from "./scenes";
import "./theme-transition.css";

/**
 * Örtü üst katmana (top layer) alınır; böylece açık bir <dialog> (ayarlar çekmecesi) üzerinde de
 * görünür. Popover API yoksa ya da çağrı başarısız olursa z-index ile sıradan örtü olarak kalır.
 */
function showOnTop(element: HTMLDivElement | null) {
  try {
    element?.showPopover?.();
  } catch {
    // Zaten açık ya da desteklenmiyor: örtü yine de çizilir.
  }
}

/**
 * Tema geçişi örtüsü. Yalnız bir geçiş sürerken DOM'dadır; dekoratiftir (aria-hidden),
 * tıklamaları engellemez ve süre bitince kaldırılır. Sunucuda ve ilk istemci çiziminde null döner.
 */
export function ThemeTransitionLayer() {
  const transition = useThemeTransition();

  // Örtü kaldırılırken bekleyen tema değişimi varsa şimdi uygulanır.
  useEffect(() => finishThemeTransition, []);

  if (!transition) {
    return null;
  }

  const style = { "--fx-dur": `${TRANSITION_TOTAL_MS}ms` } as CSSProperties;

  if (transition.scene === "sunrise") {
    return (
      <div
        key={transition.id}
        ref={showOnTop}
        popover="manual"
        aria-hidden="true"
        data-testid="theme-transition"
        data-scene="sunrise"
        className="ltz-fx ltz-fx--sunrise"
        style={style}
      >
        <div className="fx-sky-night" />
        <div className="fx-sky-dawn" />
        <div className="fx-glow" />
        <div className="fx-sun" />
        <RidgeScene />
      </div>
    );
  }

  return (
    <div
      key={transition.id}
      ref={showOnTop}
      popover="manual"
      aria-hidden="true"
      data-testid="theme-transition"
      data-scene="storm"
      className="ltz-fx ltz-fx--storm"
      style={style}
    >
      <div className="fx-sky-day" />
      <div className="fx-sky-storm" />
      <div className="fx-sky-night" />
      <div className="fx-moon" />
      <CloudScene />
      <RainScene />
      <div className="fx-flash" />
      <BoltScene />
      <CityScene />
    </div>
  );
}
