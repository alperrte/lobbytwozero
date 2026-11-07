"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { AUTH_BACKDROP, chooseNext, initialPlaylistState, type PlaylistState } from "../services/backdrop-playlist";

function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", notify);
      return () => list.removeEventListener("change", notify);
    },
    () => window.matchMedia(query).matches,
    () => false
  );
}

const noop = () => undefined;

/**
 * Giriş/kayıt sayfalarının SOL PANELİ için arka plan videosu. `(auth)/layout.tsx` içinde durur: sayfalar arası geçişte
 * layout yeniden kurulmadığı için video kesilmeden akmaya devam eder.
 *
 * - Rastgele başlar; biri bitince bir sonraki, önceden yüklenmiş ikinci video etiketine çapraz solmayla geçilir
 *   (tek etiketin kaynağını değiştirmek geçişte donma/siyah kare yapardı). Seçim kuralı: `backdrop-playlist.ts`.
 * - Yalnız geniş ekranda (lg+) ve "hareketi azalt" kapalıyken yüklenir/oynar; aksi halde sabit poster görünür, video
 *   hiç indirilmez. Her an en fazla iki video (oynayan + sıradaki) yüklüdür.
 * - Sessiz, kontrolsüz, ekran okuyuculardan gizli (süs). Sekme gizlenince durur. Yüklenemeyen video atlanır.
 */
export function AuthBackdrop() {
  const wide = useMediaQuery("(min-width: 1024px)");
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const enabled = wide && !reduced;
  const firstRef = useRef<HTMLVideoElement>(null);
  const secondRef = useRef<HTMLVideoElement>(null);
  const [visible, setVisible] = useState<0 | 1 | null>(null);

  useEffect(() => {
    const first = firstRef.current;
    const second = secondRef.current;
    if (!enabled || !first || !second) {
      return;
    }
    const players: [HTMLVideoElement, HTMLVideoElement] = [first, second];
    const weights = AUTH_BACKDROP.videos.map((video) => video.weight);
    const fadeMs = AUTH_BACKDROP.crossfadeSeconds * 1000;
    let state: PlaylistState = initialPlaylistState;
    let active: 0 | 1 = 0;
    let swapping = false;
    let activeEnded = false;
    let disposed = false;
    let timer: number | undefined;

    function pick(): number {
      const result = chooseNext({ weights, state, factor: AUTH_BACKDROP.playedWeightFactor, rng: Math.random });
      state = result.state;
      return result.index;
    }

    function load(player: HTMLVideoElement) {
      player.src = AUTH_BACKDROP.videos[pick()]?.src ?? "";
      player.load();
    }

    function swap() {
      const next = players[active === 0 ? 1 : 0];
      if (swapping || disposed || next.readyState < 3) {
        return; // sıradaki henüz hazır değil; hazır olunca (canplay) ya da sonraki zaman güncellemesinde yeniden denenir
      }
      swapping = true;
      activeEnded = false;
      const old = players[active];
      active = active === 0 ? 1 : 0;
      next.currentTime = 0;
      next.play().catch(noop);
      setVisible(active);
      timer = window.setTimeout(() => {
        if (disposed) {
          return;
        }
        old.pause();
        load(old); // eski etiket artık "sıradaki"
        swapping = false;
      }, fadeMs + 150);
    }

    const listeners: Array<() => void> = [];
    players.forEach((player, index) => {
      const onTime = () => {
        if (index === active && Number.isFinite(player.duration) && player.duration - player.currentTime <= AUTH_BACKDROP.crossfadeSeconds) {
          swap();
        }
      };
      const onEnded = () => {
        if (index === active) {
          activeEnded = true;
          swap();
        }
      };
      const onCanPlay = () => {
        if (index !== active && activeEnded) {
          swap();
        }
      };
      const onPlaying = () => {
        if (index === active) {
          setVisible(active);
        }
      };
      const onError = () => {
        if (disposed) {
          return;
        }
        if (index !== active) {
          load(player); // bozuk/eksik video: başka birini dene
        } else {
          activeEnded = true;
          swap();
        }
      };
      player.addEventListener("timeupdate", onTime);
      player.addEventListener("ended", onEnded);
      player.addEventListener("canplay", onCanPlay);
      player.addEventListener("playing", onPlaying);
      player.addEventListener("error", onError);
      listeners.push(() => {
        player.removeEventListener("timeupdate", onTime);
        player.removeEventListener("ended", onEnded);
        player.removeEventListener("canplay", onCanPlay);
        player.removeEventListener("playing", onPlaying);
        player.removeEventListener("error", onError);
      });
    });

    const onVisibility = () => {
      const current = players[active];
      if (document.hidden) {
        current.pause();
      } else {
        current.play().catch(noop);
      }
    };
    document.addEventListener("visibilitychange", onVisibility);

    load(players[0]);
    players[0].play().catch(noop);
    load(players[1]);

    return () => {
      disposed = true;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibility);
      listeners.forEach((remove) => remove());
      for (const player of players) {
        player.pause();
        player.removeAttribute("src");
        player.load();
      }
      setVisible(null);
    };
  }, [enabled]);

  const videoClass = (index: 0 | 1) =>
    `absolute inset-0 size-full object-cover transition-opacity duration-700 motion-reduce:transition-none ${
      visible === index ? "opacity-100" : "opacity-0"
    }`;

  return (
    <div
      aria-hidden="true"
      data-testid="auth-backdrop"
      className="pointer-events-none fixed inset-y-0 start-0 z-0 hidden w-[53.49%] overflow-hidden bg-neutral-950 bg-cover bg-center lg:block"
      style={{ backgroundImage: `url(${AUTH_BACKDROP.poster})` }}
    >
      {enabled ? (
        <>
          <video ref={firstRef} muted playsInline preload="auto" tabIndex={-1} disablePictureInPicture disableRemotePlayback className={videoClass(0)} />
          <video ref={secondRef} muted playsInline preload="auto" tabIndex={-1} disablePictureInPicture disableRemotePlayback className={videoClass(1)} />
        </>
      ) : null}
      {/* Okunabilirlik: soldaki yazı için koyu perde; tema renginden bağımsızdır. */}
      <div className="absolute inset-0 bg-gradient-to-r from-black/75 via-black/45 to-black/20" />
      <div className="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/60 to-transparent" />
    </div>
  );
}
