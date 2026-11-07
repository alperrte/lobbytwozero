"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { safeExternalUrl } from "../services/discovery-query";
import type { GamePricesResponse } from "../types/game";
import { controlClass, GameStatus } from "./game-status";

export function GamePrices({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const [country, setCountry] = useState("TR");
  const [currency, setCurrency] = useState("TRY");
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
  return <section aria-labelledby="game-prices" className="mt-10 border-t border-border pt-8">
    <h2 id="game-prices" className="font-display text-2xl font-semibold">{t("prices")}</h2>
    <div className="my-5 flex flex-wrap gap-4">
      <label className="flex flex-col gap-2 text-sm">{t("fields.country")}<select value={country} onChange={(event) => setCountry(event.target.value)} className={controlClass}>{["TR", "US", "GB", "DE"].map((value) => <option key={value}>{value}</option>)}</select></label>
      <label className="flex flex-col gap-2 text-sm">{t("fields.currency")}<select value={currency} onChange={(event) => setCurrency(event.target.value)} className={controlClass}>{["USD", "EUR", "GBP", "TRY"].map((value) => <option key={value}>{value}</option>)}</select></label>
    </div>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && (offers.length ? <ul className="divide-y divide-border">{offers.map((offer) => <li key={offer.store} className="flex flex-wrap items-baseline justify-between gap-4 py-4">
      <div><span className="font-semibold">{offer.store}</span><p className="mt-1 text-xs text-text-secondary">{t("checkedAt", { date: new Date(offer.fetchedAt).toLocaleString(locale) })}</p></div>
      <span className="tabular-nums">{offer.currentPrice < offer.regularPrice && <del className="mr-3 text-text-secondary">{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.regularPrice)}</del>}{new Intl.NumberFormat(locale, { style: "currency", currency }).format(offer.currentPrice)}</span>
      {safeExternalUrl(offer.url) && <a href={safeExternalUrl(offer.url)} target="_blank" rel="sponsored nofollow noopener noreferrer" className="py-2 text-brand-text underline-offset-4 hover:underline">{t("openStore")}</a>}
    </li>)}</ul> : <p className="py-4 text-text-secondary">{t(`priceStates.${["STALE", "NO_OFFER", "UNAVAILABLE"].includes(state) ? state : "UNAVAILABLE"}`)}</p>)}
    <p className="mt-4 text-sm text-text-secondary">{t("priceDisclaimer")} <a href="https://isthereanydeal.com/" target="_blank" rel="noopener noreferrer" className="text-brand-text underline">IsThereAnyDeal</a></p>
  </section>;
}
