"use client";

import { useEffect, type ComponentType, type CSSProperties } from "react";
import { useThemeTransition } from "@/shared/hooks/use-theme-transition";
import { finishThemeTransition, type ThemeScene } from "@/shared/lib/theme/theme-transition";
import { StormScene, SunriseScene } from "./legacy/v1/classic-scenes";
import { BlackoutScene, DaybreakScene } from "./legacy/v2/tactical-scenes";
import { BlackoutV3Scene, DaybreakV3Scene, type V3SceneProps } from "./v3/v3-scenes";
import "./theme-transition.css";
import "./legacy/v1/classic.css";
import "./legacy/v2/tactical.css";
import "./v3/v3.css";

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
 * Yeni sahne eklemek: ThemeScene'e adı yaz (transition-scenes.ts), buraya bileşenini kaydet.
 * Sahneler `compact` (dar ekran çizelgesi) ve `startedAt` (denetleyici saati) alır; legacy sahneler yok sayar.
 */
const sceneContent: Record<ThemeScene, ComponentType<V3SceneProps>> = {
  sunrise: SunriseScene,
  storm: StormScene,
  daybreak: DaybreakScene,
  blackout: BlackoutScene,
  "v3-daybreak": DaybreakV3Scene,
  "v3-blackout": BlackoutV3Scene
};

/**
 * Tema geçişi örtüsü. Yalnız bir geçiş sürerken DOM'dadır; dekoratiftir (aria-hidden),
 * tıklamaları engellemez ve süre bitince kaldırılır. Sunucuda ve ilk istemci çiziminde null döner.
 * Hangi sahnenin çalacağını paylaşılan denetleyici (theme-transition.ts) seçer; burada yalnız çizilir.
 */
export function ThemeTransitionLayer() {
  const transition = useThemeTransition();

  // Örtü kaldırılırken bekleyen tema değişimi varsa şimdi uygulanır.
  useEffect(() => finishThemeTransition, []);

  if (!transition) {
    return null;
  }

  const Scene = sceneContent[transition.scene];
  const style = { "--fx-dur": `${transition.totalMs}ms` } as CSSProperties;

  return (
    <div
      key={transition.id}
      ref={showOnTop}
      popover="manual"
      aria-hidden="true"
      data-testid="theme-transition"
      data-scene={transition.scene}
      className={`ltz-fx ltz-fx--${transition.scene}`}
      style={style}
    >
      <Scene compact={transition.compact} startedAt={transition.startedAt} />
    </div>
  );
}
