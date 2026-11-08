"use client";

import { useEffect, useId, useRef } from "react";
import Image from "next/image";
import { useTranslations } from "next-intl";
import { finishPublish, usePublishState } from "../services/publish-flow";
import "./community.css";

const SEGMENTS = 8;
/** Liste yenilemesi bir nedenle hiç tamamlanmazsa (ağ/gezinme) kullanıcı sonsuza dek kilitli kalmasın. */
const LIST_FAILSAFE_MS = 20_000;

/**
 * Paylaşım sırasında tüm ekranı kaplayan yükleme katmanı. Community yerleşiminde tek örnek olarak durur; böylece oluşturma
 * sayfasından akışa geçişte kaybolmaz ve yeni gönderi listede görünmeye hazır olana dek sürer (bkz. `publish-flow`).
 *
 * - Yerel `<dialog>` kipidir: ekrana (viewport) sabittir, kaydırma konumundan etkilenmez; arka plan etkileşime kapanır
 *   (odak ve tıklama içeri hapsolur), Escape ile kapanmaz; arka plan bulanıklaşır ve hafif kararır.
 * - Hareket belirsiz ilerlemedir (yüzde uydurulmaz); gösterilen aşama metni gerçek aşamayı söyler. Hareket tercihi
 *   "azalt" ise halkalar ve nabız durur, metin görünür kalır.
 */
export function PublishOverlay() {
  const t = useTranslations("Community.form");
  const state = usePublishState();
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const active = state.phase !== "idle";

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (active && !dialog.open) dialog.showModal();
    if (!active && dialog.open) dialog.close();
  }, [active]);

  // Katman açıkken arkadaki sayfa kaydırılamaz.
  useEffect(() => {
    if (!active) return;
    const root = document.documentElement;
    const previous = root.style.overflow;
    root.style.overflow = "hidden";
    return () => {
      root.style.overflow = previous;
    };
  }, [active]);

  useEffect(() => {
    if (state.phase !== "awaitingList") return;
    const timer = setTimeout(finishPublish, LIST_FAILSAFE_MS);
    return () => clearTimeout(timer);
  }, [state.phase]);

  const stage = state.stage;
  const stageText =
    stage?.kind === "image"
      ? t("publishingImage", { current: stage.current, total: stage.total })
      : stage?.kind === "list"
        ? t("publishingList")
        : t("publishingPost");

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-busy={active}
      onCancel={(event) => event.preventDefault()}
      className="m-auto max-h-none max-w-none overflow-visible border-0 bg-transparent p-0 text-text-primary backdrop:bg-scrim backdrop:backdrop-blur-md"
    >
      {active ? (
        <div className="ltz-overlay-in relative flex w-[min(19rem,calc(100vw-2rem))] flex-col items-center gap-5 rounded-panel border border-border bg-surface px-8 py-9 shadow-popover">
          <span aria-hidden="true" className="absolute -start-px -top-px size-5 rounded-ss-panel border-s-2 border-t-2 border-primary" />
          <span aria-hidden="true" className="absolute -bottom-px -end-px size-5 rounded-ee-panel border-b-2 border-e-2 border-primary" />

          <div aria-hidden="true" className="relative size-28">
            <svg viewBox="0 0 120 120" className="absolute inset-0 size-full">
              <circle className="ltz-ring ltz-ring-outer" cx="60" cy="60" r="54" fill="none" stroke="var(--ltz-primary)" strokeWidth="4" strokeDasharray="20 13.93" />
              <circle className="ltz-ring ltz-ring-inner" cx="60" cy="60" r="44" fill="none" stroke="var(--ltz-accent)" strokeOpacity="0.7" strokeWidth="2" strokeDasharray="6 17.04" />
            </svg>
            <Image
              src="/assets/brand/ltz-mark.png"
              alt=""
              width={256}
              height={181}
              sizes="48px"
              className="ltz-mark absolute left-1/2 top-1/2 w-12 -translate-x-1/2 -translate-y-1/2"
            />
          </div>

          <div role="status" aria-live="polite" className="flex flex-col items-center gap-2.5 text-center">
            <p id={titleId} className="font-display text-lg font-semibold text-brand-text">
              {t("publishingOverlay")}
            </p>
            <div aria-hidden="true" className="ltz-publish-segments">
              {Array.from({ length: SEGMENTS }, (_, index) => (
                <span key={index} style={{ "--i": index } as React.CSSProperties} />
              ))}
            </div>
            <p className="text-xs text-text-secondary">{stageText}</p>
          </div>
        </div>
      ) : null}
    </dialog>
  );
}
