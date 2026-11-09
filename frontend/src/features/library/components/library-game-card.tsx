"use client";

import { useLocale, useTranslations } from "next-intl";
import { GameCover } from "@/features/game/components/game-cover";
import { Link } from "@/shared/lib/i18n/navigation";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import type { LibraryItem, LibraryMutation } from "../types/library";

export function LibraryGameCard({ item, readOnly = false, pending = false, onAction, onRemove }: {
  item: LibraryItem; readOnly?: boolean; pending?: boolean; onAction?: (action: LibraryMutation) => void; onRemove?: () => void;
}) {
  const t = useTranslations("Library");
  const locale = useLocale();
  const { entry, game } = item;
  const title = game.title || t("unknownTitle");
  const date = (value: string) => Number.isNaN(Date.parse(value)) ? t("unknownDate") : new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(new Date(value));
  return <article className="flex min-w-0 flex-col overflow-hidden rounded-panel border border-border bg-surface">
    <Link href={{ pathname: "/games/[gameId]", params: { gameId: entry.gameId } }} aria-label={t("openGame", { title })} className="block focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus">
      <div role="img" aria-label={t("cover", { title })}><GameCover url={game.coverUrl} title={title} /></div>
      <h2 className="px-4 pt-4 font-display text-lg font-semibold [overflow-wrap:anywhere]">{title}</h2>
    </Link>
    <div className="flex flex-1 flex-col gap-3 p-4 pt-2">
      <p className="text-xs text-text-secondary">{t("addedAt", { date: date(entry.addedAt) })}</p>
      <div className="flex flex-wrap gap-2 text-xs">
        {entry.favorite && <span className="inline-flex items-center gap-1 text-brand-text"><Icon name="star" className="size-4" />{t("favorite")}</span>}
        {entry.completed && <span className="inline-flex items-center gap-1 text-success"><Icon name="check" className="size-4" />{t("completed")}</span>}
      </div>
      {(game.status !== "AVAILABLE" || game.metadataSource !== "LIVE") && <div className="space-y-1 text-xs leading-5 text-text-secondary">
        {game.status !== "AVAILABLE" && <p>{t(`metadata.${game.status}`)}</p>}
        {game.metadataSource !== "LIVE" && <p>{t(`metadata.${game.metadataSource}`)}</p>}
        {game.metadataSource === "LAST_KNOWN" && game.metadataRefreshedAt && <p>{t("observedAt", { date: date(game.metadataRefreshedAt) })}</p>}
      </div>}
      {!readOnly && <div className="mt-auto space-y-2 border-t border-border pt-3" aria-busy={pending}>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonClass("secondary", "sm", "min-h-10 flex-1")} aria-pressed={entry.favorite} disabled={pending || (!entry.favorite && game.status !== "AVAILABLE")} onClick={() => onAction?.({ kind: "favorite", gameId: entry.gameId, enabled: !entry.favorite })}>{t(entry.favorite ? "unfavorite" : "markFavorite")}</button>
          <button type="button" className={buttonClass("secondary", "sm", "min-h-10 flex-1")} aria-pressed={entry.completed} disabled={pending || (!entry.completed && game.status !== "AVAILABLE")} onClick={() => onAction?.({ kind: "completed", gameId: entry.gameId, enabled: !entry.completed })}>{t(entry.completed ? "uncomplete" : "markCompleted")}</button>
        </div>
        <button type="button" onClick={onRemove} disabled={pending} className={buttonClass("ghost", "sm", "min-h-10 w-full")}>{t("remove")}</button>
      </div>}
    </div>
  </article>;
}
