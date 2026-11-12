"use client";

import { useEffect, useRef, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { safeExternalUrl } from "../services/discovery-query";
import type { GamePricesResponse } from "../types/game";
import { relativeAge } from "../services/price-age";
import { defaultCurrencyForLocale, marketForCurrency, marketLabel, marketOptions } from "../services/price-markets";
import { controlClass, GameStatus } from "./game-status";
import { Badge, GameSelect } from "./game-ui";

/** Bayat/boş fiyat durumunda yeniden okuma gecikmeleri (ms): ilk 12 sn, ikincisi ilkten 18 sn sonra (toplam 30 sn). Sürekli sorgulama YOKTUR: en çok iki kez. */
const REREAD_DELAYS_MS = [12000, 18000] as const;

type PriceOffer = GamePricesResponse["items"][number]["offers"][number];

/**
 * Süresi dolmuş (bayat) fiyatlar: son bilinen teklifler güncel olduğu iddia edilmeden gösterilir. İndirim rozeti ve üstü çizili eski fiyat YOKTUR
 * (indirim bitmiş olabilir); her satır "güncel olmayabilir" rozeti taşır ve en üstte son kontrol yaşı yazılır.
 */
function StalePrices({ offers, currency, locale, ageText }: { offers: PriceOffer[]; currency: string; locale: string; ageText: string }) {
  const t = useTranslations("Game");
  return <>
    <p role="note" data-testid="stale-price-note" className="mb-3 text-sm text-text-secondary">{t("priceStaleNotice", { age: ageText })}</p>
    <ul className="space-y-3">{offers.map((offer) => <li key={offer.store} className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-border bg-surface p-4 opacity-75">
      <div><span className="font-semibold">{offer.store}</span><p className="mt-1 text-xs text-text-secondary">{t("checkedAt", { date: new Date(offer.fetchedAt).toLocaleString(locale) })}</p></div>
      <span className="tabular-nums"><Badge tone="warning" className="mr-3">{t("priceStaleBadge")}</Badge>{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.currentPrice)}</span>
      {safeExternalUrl(offer.url) && <a href={safeExternalUrl(offer.url)} target="_blank" rel="sponsored nofollow noopener noreferrer" className="py-2 text-brand-text underline-offset-4 hover:underline">{t("openStore")}</a>}
    </li>)}</ul>
  </>;
}

/**
 * `free` / `comingSoon`: Steam'in mağaza bilgisi (null/yok = bilinmiyor). Teklif yokken neden fiyat olmadığını açıklar: ücretsiz oyunun fiyatı yoktur
 * (0 değildir, hata da değildir), çıkmamış oyunun fiyatı henüz yoktur. Teklif varsa (ör. ön sipariş) her zaman teklif gösterilir.
 */
export function GamePrices({ gameId, free, comingSoon }: { gameId: string; free?: boolean | null; comingSoon?: boolean | null }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  // Kullanıcı ülke (pazar) seçer; para birimi ITAD sözleşmesi gereği otomatik gelir (ABD→USD, Almanya→EUR, Türkiye→TRY ...). Varsayılan pazar dile göre.
  const [currency, setCurrency] = useState<string>(() => defaultCurrencyForLocale(locale));
  const { country } = marketForCurrency(currency);
  const [clock, setClock] = useState<{ source: GamePricesResponse["items"][number] | undefined; now: number }>();
  const result = useGameResource<GamePricesResponse>({ kind: "prices", gameIds: [gameId], country, currency }, { retainWhileRefreshing: true });
  const item = result.data?.market.country === country && result.data.market.currency === currency ? result.data.items.find((value) => value.gameId === gameId) : undefined;
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const tick = () => {
      const now = Date.now();
      setClock({ source: item, now });
      const dates = item?.offers.map((offer) => Date.parse(offer.expiresAt)).filter((date) => Number.isFinite(date) && date > now) ?? [];
      if (dates.length) timer = setTimeout(tick, Math.min(2147483647, Math.max(1, Math.min(...dates) - now)));
    };
    timer = setTimeout(tick, 0);
    return () => clearTimeout(timer);
  }, [item]);
  const now = clock?.source === item ? clock?.now ?? 0 : 0;
  // Her oyunda ITAD'in döndürdüğü tüm mağazalar gösterilir; en ucuzdan pahalıya sıralı.
  const offers = item?.status === "AVAILABLE" ? item.offers.filter((offer) => Date.parse(offer.expiresAt) > now && Date.parse(offer.fetchedAt) <= now).sort((a, b) => a.currentPrice - b.currentPrice || a.store.localeCompare(b.store)) : [];
  const state = item?.status === "AVAILABLE" && !offers.length ? "STALE" : item?.status ?? "UNAVAILABLE";
  const emptyState = free === true ? "FREE" : comingSoon === true ? "COMING_SOON" : ["STALE", "NO_OFFER", "UNAVAILABLE"].includes(state) ? state : "UNAVAILABLE";
  // Süresi dolmuş teklifler: sunucu STALE'de eski teklifleri de döndürür. Ücretsiz/çıkmamış oyunda eski fiyat gösterilmez. Saat okunmadan (ilk çizim) karar verilmez.
  const clockReady = clock?.source === item;
  const staleOffers = clockReady && item && !offers.length && free !== true && comingSoon !== true && state === "STALE"
    ? [...item.offers].sort((a, b) => a.currentPrice - b.currentPrice || a.store.localeCompare(b.store))
    : [];
  const newestFetch = staleOffers.length ? Math.max(...staleOffers.map((offer) => Date.parse(offer.fetchedAt)).filter(Number.isFinite)) : Number.NaN;
  // Bayat veya doğrulanmış fiyatı olmayan durumda, ipucuyla yenilenen veriyi görmek için EN ÇOK İKİ kez yeniden okunur (sürekli sorgulama yok).
  const marketKey = `${gameId}|${country}|${currency}`;
  const [rereads, setRereads] = useState<{ key: string; count: number }>({ key: marketKey, count: 0 });
  const attempt = rereads.key === marketKey ? rereads.count : 0;
  const wantsReread = Boolean(result.data) && clockReady && free !== true && comingSoon !== true && (state === "STALE" || state === "UNAVAILABLE");
  const reloadRef = useRef(result.reload);
  useEffect(() => { reloadRef.current = result.reload; });
  useEffect(() => {
    if (!wantsReread || attempt >= REREAD_DELAYS_MS.length) return;
    const timer = setTimeout(() => {
      setRereads({ key: marketKey, count: attempt + 1 });
      if (document.visibilityState === "visible") reloadRef.current();
    }, REREAD_DELAYS_MS[attempt]);
    return () => clearTimeout(timer);
  }, [wantsReread, attempt, marketKey]);
  return <section aria-labelledby="game-prices" className="border-t border-border pt-8">
    <h2 id="game-prices" className="font-display text-2xl font-semibold">{t("prices")}</h2>
    <div className="my-5 flex flex-wrap items-end gap-4">
      <label className="flex flex-col gap-2 text-sm">{t("fields.country")}<GameSelect value={currency} onChange={(event) => setCurrency(event.target.value)} className={controlClass}>{marketOptions(locale).map((market) => <option key={market.currency} value={market.currency}>{marketLabel(market, locale)}</option>)}</GameSelect></label>
    </div>
    {currency === "TRY" && <p data-testid="try-note" className="mb-4 text-sm text-text-secondary">{t("priceTurkeyNote")}</p>}
    <GameStatus {...result} loading={result.loading && !result.data} onRetry={result.reload} />
    {result.data && (offers.length ? <ul className="space-y-3">{offers.map((offer) => <li key={offer.store} className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-border bg-surface p-4">
      <div><span className="font-semibold">{offer.store}</span><p className="mt-1 text-xs text-text-secondary">{t("checkedAt", { date: new Date(offer.fetchedAt).toLocaleString(locale) })}</p></div>
      <span className="tabular-nums">{offer.currentPrice < offer.regularPrice && <><Badge tone="success" className="mr-3">{t("discountPercent", { percent: offer.discountPercent ?? Math.round((1 - offer.currentPrice / offer.regularPrice) * 100) })}</Badge><del className="mr-3 text-text-secondary">{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.regularPrice)}</del></>}{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.currentPrice)}</span>
      {safeExternalUrl(offer.url) && <a href={safeExternalUrl(offer.url)} target="_blank" rel="sponsored nofollow noopener noreferrer" className="py-2 text-brand-text underline-offset-4 hover:underline">{t("openStore")}</a>}
    </li>)}</ul> : staleOffers.length ? <StalePrices offers={staleOffers} currency={currency} locale={locale} ageText={relativeAge(newestFetch, clock?.now ?? 0, locale)} /> : <p className="py-4 text-text-secondary">{t(`priceStates.${emptyState}`)}</p>)}
  </section>;
}
