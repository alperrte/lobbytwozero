"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { reasonField } from "../schemas/game-forms";
import type { DiscoveryPage } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { coverOf, GameCover } from "./game-cover";
import { GamePicker, type PickedGame } from "./game-picker";
import { controlClass, GameStatus } from "./game-status";
import { Badge, EmptyState, panelClass, Skeleton } from "./game-ui";

export const maxPopularGames = 100;

/** Dizide iki öğenin yerini değiştirir; sınır dışı hareket listeyi değiştirmez. */
export function moveItem<T>(items: readonly T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (index < 0 || index >= items.length || target < 0 || target >= items.length) return [...items];
  const next = [...items];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

function Editor({ initial, onSaved, onEdited }: { initial: PickedGame[]; onSaved: () => void; onEdited: () => void }) {
  const t = useTranslations("Game");
  const [list, update] = useState(initial);
  const setList = (next: PickedGame[]) => { update(next); onEdited(); };
  const changed = list.length !== initial.length || list.some((game, index) => game.gameId !== initial[index]?.gameId);
  return <div className="space-y-6">
    <div role="note" className={`${panelClass} space-y-2 text-sm`}>
      <p>{t("popularEligibility")}</p>
      <p className="font-medium text-brand-text">{t("popularReplaceWarning")}</p>
    </div>
    <section aria-label={t("popularSelected")} className="space-y-3">
      <h3 className="flex flex-wrap items-center gap-3 font-display text-xl font-semibold">{t("popularSelected")}<Badge>{list.length}/{maxPopularGames}</Badge>{changed && <Badge tone="warning">{t("unsavedChanges")}</Badge>}</h3>
      {list.length === 0
        ? <EmptyState title={t("popularEmptyList")}>{t("popularEmptyHelp")}</EmptyState>
        : <ol className="space-y-2">{list.map((game, index) => <li key={game.gameId} className="flex items-center gap-3 rounded-panel border border-border bg-surface p-2">
          <span className="w-8 shrink-0 text-center text-sm font-semibold tabular-nums text-text-secondary">{index + 1}</span>
          <GameCover url={coverOf(game)} title={game.title} className="w-20 shrink-0 rounded-control" />
          <span className="min-w-0 flex-1 font-medium [overflow-wrap:anywhere]">{game.title}</span>
          <div className="flex shrink-0 flex-wrap justify-end gap-1">
            <button type="button" className={controlClass} disabled={index === 0} aria-label={t("moveUp", { title: game.title })} onClick={() => setList(moveItem(list, index, -1))}>↑</button>
            <button type="button" className={controlClass} disabled={index === list.length - 1} aria-label={t("moveDown", { title: game.title })} onClick={() => setList(moveItem(list, index, 1))}>↓</button>
            <button type="button" className={controlClass} aria-label={t("removeFromList", { title: game.title })} onClick={() => setList(list.filter((value) => value.gameId !== game.gameId))}>{t("remove")}</button>
          </div></li>)}</ol>}
    </section>
    {list.length < maxPopularGames
      ? <section aria-label={t("popularAdd")} className={panelClass}><GamePicker label={t("popularAdd")} excluded={list.map((game) => game.gameId)} onPick={(game) => setList([...list, game])} /></section>
      : <p role="status" className="text-sm text-text-secondary">{t("popularLimit", { max: maxPopularGames })}</p>}
    <GameActionForm title={t("popularSave")} fields={[reasonField]} destructive description={list.length === 0 ? t("popularClearWarning") : undefined}
      build={(values) => ({ operation: "popular", body: { gameIds: list.map((game) => game.gameId), reason: values.reason } })} onSaved={onSaved} />
  </div>;
}

/** Editör (popüler) listesi: mevcut sıra herkese açık uçtan okunur, sıralama ve ekleme/çıkarma yerelde yapılır, kayıt tek PUT'tur. */
export function GamePopularEditor() {
  const t = useTranslations("Game");
  const [revision, setRevision] = useState(0);
  // Kayıttan sonra liste yeniden yüklenip düzenleyici yeniden oluşur; başarı bildirimi bu yüzden burada tutulur.
  const [saved, setSaved] = useState(false);
  const result = useGameResource<DiscoveryPage>({ kind: "popular", page: 0, size: maxPopularGames }, { refreshOnFocus: false });
  return <section aria-label={t("popularSelection")} className="space-y-4">
    <h2 className="font-display text-2xl font-semibold">{t("popularSelection")}</h2>
    {result.loading && <Skeleton className="h-40" />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {result.data && <Editor key={`${revision}/${result.data.items.map((item) => item.game.gameId).join(",")}`} initial={result.data.items.map((item) => ({ gameId: item.game.gameId, title: item.game.title, coverUrl: item.game.coverUrl }))} onSaved={() => { setSaved(true); setRevision(revision + 1); result.reload(); }} onEdited={() => setSaved(false)} />}
    {saved && <p role="status" className="text-sm font-medium text-success">{t("saved")}</p>}
  </section>;
}
