"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { SearchField } from "@/shared/components/ui/search-field";
import { cn } from "@/shared/utils/cn";
import { useMessaging } from "../hooks/use-messaging";
import { isAbort, messagingErrorKey } from "../services/messaging-errors";
import type { GifItem } from "../types/messaging.types";

const PAGE = 24;
const DEBOUNCE_MS = 350;
/** Yalnız GIPHY medya sunucularındaki HTTPS adresleri gösterilir (sunucu da süzer; istemci ikinci kez denetler). */
const GIPHY_MEDIA = /^https:\/\/media\d*\.giphy\.com\//;

export function isSafeGifUrl(url: string): boolean {
  return GIPHY_MEDIA.test(url);
}

type GifState = { key: string; items: GifItem[]; nextOffset: number | null; error?: unknown; loading: boolean };

/**
 * GIF seçici (MSG-22). Arama backend üzerinden geçer (platform anahtarı istemciye gelmez). Boş aramada öne çıkanlar gösterilir.
 * GIPHY kullanım şartı gereği "Powered by GIPHY" atfı görünür. Platform kapalıysa (`503`) diğer mesajlaşma etkilenmez.
 */
export function GifPicker({ onPick, className }: { onPick: (gif: GifItem) => void; className?: string }) {
  const t = useTranslations("Messaging");
  const locale = useLocale();
  const { api } = useMessaging();
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<GifState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setQuery(draft.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [draft]);

  const lang = locale === "en" ? "en" : "tr";
  const fetchPage = (key: string, offset: number, signal?: AbortSignal) =>
    key ? api.gifSearch({ q: key, limit: PAGE, offset, lang }, signal) : api.gifTrending({ limit: PAGE, offset }, signal);
  const safe = (items: GifItem[]) => items.filter((item) => isSafeGifUrl(item.previewUrl) && isSafeGifUrl(item.url));

  useEffect(() => {
    const controller = new AbortController();
    const key = query;
    (key ? api.gifSearch({ q: key, limit: PAGE, offset: 0, lang }, controller.signal) : api.gifTrending({ limit: PAGE, offset: 0 }, controller.signal)).then(
      (page) => setResult({ key, items: safe(page.items), nextOffset: page.nextOffset, loading: false }),
      (error: unknown) => {
        if (!isAbort(error) && !controller.signal.aborted) setResult({ key, items: [], nextOffset: null, error, loading: false });
      }
    );
    return () => controller.abort();
  }, [api, query, lang]);

  const current = result?.key === query ? result : null;
  const state: GifState = current
    ? { ...current, loading: loadingMore }
    : { key: query, items: [], nextOffset: null, loading: true };

  function loadMore() {
    if (!current || current.nextOffset === null) return;
    const key = current.key;
    setLoadingMore(true);
    fetchPage(key, current.nextOffset).then(
      (page) => {
        setLoadingMore(false);
        setResult((previous) =>
          previous?.key === key ? { ...previous, items: [...previous.items, ...safe(page.items)], nextOffset: page.nextOffset } : previous
        );
      },
      (error: unknown) => {
        setLoadingMore(false);
        setResult((previous) => (previous?.key === key ? { ...previous, error } : previous));
      }
    );
  }

  return (
    <div className={cn("flex w-[22rem] max-w-[calc(100vw-2rem)] flex-col rounded-float border border-border bg-surface p-3 shadow-popover", className)}>
      <SearchField
        size="sm"
        label={t("gif.search")}
        placeholder={t("gif.searchPlaceholder")}
        maxLength={50}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
      <p className="mt-2 text-xs font-semibold text-text-secondary">{query ? t("gif.results") : t("gif.trending")}</p>
      <div className="mt-2 h-64 overflow-y-auto overscroll-contain [scrollbar-width:thin]" aria-busy={state.loading || undefined}>
        {state.error ? (
          <p role="alert" className="px-2 py-8 text-center text-sm text-text-secondary">
            {t(`errors.${messagingErrorKey(state.error)}`)}
          </p>
        ) : state.items.length === 0 && !state.loading ? (
          <p className="px-2 py-8 text-center text-sm text-text-secondary">{t("gif.empty")}</p>
        ) : (
          <ul className="grid grid-cols-3 gap-1.5">
            {state.items.map((gif) => (
              <li key={gif.id}>
                <button
                  type="button"
                  onClick={() => onPick(gif)}
                  aria-label={gif.title ? t("gif.pickNamed", { title: gif.title }) : t("gif.pick")}
                  className="block aspect-square w-full overflow-hidden rounded-control bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- dış GIF (izinli sağlayıcı), Next optimizasyonu animasyonu bozar */}
                  <img src={gif.previewUrl} alt="" loading="lazy" referrerPolicy="no-referrer" className="size-full object-cover" />
                </button>
              </li>
            ))}
          </ul>
        )}
        {state.loading ? (
          <p role="status" className="py-3 text-center text-xs text-text-secondary">
            {t("states.loading")}
          </p>
        ) : null}
        {!state.loading && !state.error && state.nextOffset !== null && state.items.length > 0 ? (
          <div className="flex justify-center py-2">
            <button type="button" onClick={loadMore} className={buttonClass("ghost", "sm")}>
              {t("gif.more")}
            </button>
          </div>
        ) : null}
      </div>
      <p className="mt-2 text-end text-[0.6875rem] font-semibold uppercase tracking-wide text-text-secondary">Powered by GIPHY</p>
    </div>
  );
}
