"use client";

import { useEffect, useId, useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { DiscoveryPage, ManagedGame } from "../types/game";
import { GameCover } from "./game-cover";
import { controlClass, GameStatus } from "./game-status";

export type PickedGame = ManagedGame & { coverUrl?: string | null };

function Results({ query, onPick, excluded }: { query: string; onPick: (game: PickedGame) => void; excluded: readonly string[] }) {
  const t = useTranslations("Game");
  const result = useGameResource<DiscoveryPage>({ kind: "discovery", query: new URLSearchParams({ q: query, size: "8", sort: "TITLE", direction: "ASC" }).toString() }, { refreshOnFocus: false });
  const items = result.data?.items.filter((item) => !excluded.includes(item.game.gameId)) ?? [];
  return <div>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && (items.length === 0
      ? <p className="py-3 text-sm text-text-secondary">{t("pickerEmpty")}</p>
      : <ul className="divide-y divide-border rounded-panel border border-border">{items.map(({ game }) => <li key={game.gameId}>
        <button type="button" onClick={() => onPick({ gameId: game.gameId, title: game.title, coverUrl: game.coverUrl })} className="flex min-h-14 w-full items-center gap-3 p-2 text-left hover:bg-surface-elevated focus-visible:bg-surface-elevated">
          <GameCover url={game.coverUrl} title={game.title} className="w-20 shrink-0 rounded-control" />
          <span className="min-w-0 flex-1 font-medium [overflow-wrap:anywhere]">{game.title}</span>
          <span className="shrink-0 pe-2 text-sm text-brand-text">{t("pickerSelect")}</span>
        </button></li>)}</ul>)}
  </div>;
}

/**
 * Oyun arama/seçme. Kaynak herkese açık katalogdur (yalnız ACTIVE ana oyunlar); arşivlenmiş kayıtlar
 * burada çıkmaz. Ham kimlik girişi yalnız çağıran bileşenin ayrı "gelişmiş" alanındadır.
 */
export function GamePicker({ onPick, label, excluded = [] }: { onPick: (game: PickedGame) => void; label?: string; excluded?: readonly string[] }) {
  const t = useTranslations("Game");
  const id = useId();
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => {
    const timeout = setTimeout(() => setQuery(draft.trim()), 300);
    return () => clearTimeout(timeout);
  }, [draft]);
  return <div className="space-y-3">
    <label htmlFor={`${id}-q`} className="flex flex-col gap-2 text-sm">{label ?? t("pickerLabel")}
      <input id={`${id}-q`} type="search" maxLength={128} value={draft} onChange={(event) => setDraft(event.target.value)} className={controlClass} autoComplete="off" aria-describedby={`${id}-help`} /></label>
    <p id={`${id}-help`} className="text-xs text-text-secondary">{t("pickerHelp")}</p>
    {query && <Results key={query} query={query} onPick={(game) => { onPick(game); setDraft(""); setQuery(""); }} excluded={excluded} />}
  </div>;
}
