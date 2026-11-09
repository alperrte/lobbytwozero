"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { safeExternalUrl } from "../services/discovery-query";
import type { GamePricesResponse } from "../types/game";
import { marketForCurrency, marketLabel, marketOptions, priceMarkets } from "../services/price-markets";
import { controlClass, GameStatus } from "./game-status";
import { Badge, GameSelect } from "./game-ui";

export function GamePrices({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  // Kullanıcı ülke (pazar) seçer; para birimi ITAD sözleşmesi gereği otomatik gelir (ABD→USD, Almanya→EUR, Birleşik Krallık→GBP). TRY sunulmaz.
  const [currency, setCurrency] = useState<string>(priceMarkets[0].currency);
  const { country } = marketForCurrency(currency);
  const [clock, setClock] = useState<{ source: GamePricesResponse["items"][number] | undefined; now: number }>();
  const result = useGameResource<GamePricesResponse>({ kind: "prices", gameIds: [gameId], country, currency });
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
  const offers = item?.status === "AVAILABLE" ? item.offers.filter((offer) => Date.parse(offer.expiresAt) > now && Date.parse(offer.fetchedAt) <= now) : [];
  const state = item?.status === "AVAILABLE" && !offers.length ? "STALE" : item?.status ?? "UNAVAILABLE";
  return <section aria-labelledby="game-prices" className="border-t border-border pt-8">
    <h2 id="game-prices" className="font-display text-2xl font-semibold">{t("prices")}</h2>
    <div className="my-5 flex flex-wrap items-end gap-4">
      <label className="flex flex-col gap-2 text-sm">{t("fields.country")}<GameSelect value={currency} onChange={(event) => setCurrency(event.target.value)} className={controlClass}>{marketOptions(locale).map((market) => <option key={market.currency} value={market.currency}>{marketLabel(market, locale)}</option>)}</GameSelect></label>
    </div>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && (offers.length ? <ul className="space-y-3">{offers.map((offer) => <li key={offer.store} className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-border bg-surface p-4">
      <div><span className="font-semibold">{offer.store}</span><p className="mt-1 text-xs text-text-secondary">{t("checkedAt", { date: new Date(offer.fetchedAt).toLocaleString(locale) })}</p></div>
      <span className="tabular-nums">{offer.currentPrice < offer.regularPrice && <><Badge tone="success" className="mr-3">{t("discountPercent", { percent: offer.discountPercent ?? Math.round((1 - offer.currentPrice / offer.regularPrice) * 100) })}</Badge><del className="mr-3 text-text-secondary">{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.regularPrice)}</del></>}{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.currentPrice)}</span>
      {safeExternalUrl(offer.url) && <a href={safeExternalUrl(offer.url)} target="_blank" rel="sponsored nofollow noopener noreferrer" className="py-2 text-brand-text underline-offset-4 hover:underline">{t("openStore")}</a>}
    </li>)}</ul> : <p className="py-4 text-text-secondary">{t(`priceStates.${["STALE", "NO_OFFER", "UNAVAILABLE"].includes(state) ? state : "UNAVAILABLE"}`)}</p>)}
  </section>;
}
