"use client";

import { useEffect, useState } from "react";

/** Gizleme/gösterme kararı için kaydırma eşikleri (px). Küçük titremeler yok sayılır. */
export const AUTOHIDE_DELTA = 10;
export const AUTOHIDE_REVEAL_ZONE = 24;
export const AUTOHIDE_MIN_OFFSET = 96;

type Decision = { hidden: boolean; anchor: number };

/**
 * Saf karar fonksiyonu: son "dönüm noktası" (anchor) ile şimdiki konum arasındaki farka bakar.
 * Yönün değiştiği anda anchor sıfırlanır; böylece küçük ileri-geri hareketler titreme yapmaz.
 */
export function decideNavbarVisibility(
  previous: Decision,
  y: number
): Decision {
  const position = Math.max(0, y);

  if (position <= AUTOHIDE_REVEAL_ZONE) {
    return { hidden: false, anchor: position };
  }

  const delta = position - previous.anchor;

  if (!previous.hidden) {
    if (delta >= AUTOHIDE_DELTA && position > AUTOHIDE_MIN_OFFSET) {
      return { hidden: true, anchor: position };
    }
    // Görünürken yukarı hareket: anchor'u izle, gizleme sayacı sıfırlansın.
    return delta < 0 ? { hidden: false, anchor: position } : previous;
  }

  if (delta <= -AUTOHIDE_DELTA) {
    return { hidden: false, anchor: position };
  }
  // Gizliyken aşağı hareket: anchor'u izle, yukarı dönüş eşiği yeniden ölçülsün.
  return delta > 0 ? { hidden: true, anchor: position } : previous;
}

/**
 * Otomatik gizleme modunda navbar'ın kaydırmaya göre gizlenip gizlenmeyeceğini döndürür.
 * Klavye odağı navbar içindeyken ve açık bir menü varken görünürlük CSS (`focus-within`) ve
 * çağıran tarafın `locked` bilgisi ile korunur; bu hook yalnız kaydırma yönünü izler.
 */
export function useNavbarAutoHide(enabled: boolean): boolean {
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    let state: Decision = { hidden: false, anchor: window.scrollY };
    let frame = 0;

    function update() {
      frame = 0;
      const next = decideNavbarVisibility(state, window.scrollY);
      if (next.hidden !== state.hidden) {
        setHidden(next.hidden);
      }
      state = next;
    }
    function onScroll() {
      if (frame === 0) {
        frame = window.requestAnimationFrame(update);
      }
    }

    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame !== 0) {
        window.cancelAnimationFrame(frame);
      }
      // Mod değişince veya kaldırılınca navbar'ı görünür bırak.
      setHidden(false);
    };
  }, [enabled]);

  return enabled && hidden;
}
