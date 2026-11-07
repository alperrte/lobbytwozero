"use client";

import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { DeveloperContent } from "../types/game";
import { GameGallery } from "./game-gallery";
import { GameStatus } from "./game-status";
import { Badge, Skeleton } from "./game-ui";

/** Geliştiricinin yayımladığı içerik; katalog (IGDB/Steam) metadata'sından ayrı ve etiketlidir. Gizlenmiş içerik API'den gelmez. */
export function GameDeveloperPublic({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<DeveloperContent>({ kind: "publicDeveloper", gameId });
  const data = result.data;
  const images = data?.media.filter((value) => value.type === "IMAGE").map((value) => ({ url: value.url, alt: value.altText || t("developerContent") })) ?? [];
  const links = data?.media.filter((value) => value.type !== "IMAGE").map((value) => ({ url: value.url, label: t(value.type === "DEMO" ? "openDemo" : "openVideo") })) ?? [];
  const empty = data && !data.description && !data.media.length && !data.updateNotes.length;
  return <section className="space-y-6 border-t border-border pt-8" aria-labelledby="game-developer-content">
    <div className="flex flex-wrap items-center gap-3"><h2 id="game-developer-content" className="font-display text-2xl font-semibold">{t("developerContent")}</h2><Badge tone="brand">{t("providedByDeveloper")}</Badge></div>
    {result.loading && <Skeleton className="h-24" />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {data && <>
      {empty && <p className="text-text-secondary">{t("emptyDeveloperContent")}</p>}
      {data.description && <div><h3 className="font-semibold">{t("developerDescription")}</h3><p className="mt-2 max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary">{data.description.text}</p>{data.description.updatedAt && <p className="mt-2 text-xs text-text-secondary">{t("updatedOn", { date: new Date(data.description.updatedAt).toLocaleDateString(locale) })}</p>}</div>}
      <GameGallery images={images} links={links} />
      {data.updateNotes.length > 0 && <div><h3 className="font-semibold">{t("updateNotes")}</h3><ol className="mt-3 space-y-4 border-s border-border ps-5">{data.updateNotes.map((note) => <li key={note.id} className="relative">
        <span aria-hidden="true" className="absolute -start-[1.6rem] top-2 size-2 rounded-full bg-primary" />
        <article className="rounded-panel border border-border bg-surface p-4">
          <h4 className="font-display text-lg font-semibold [overflow-wrap:anywhere]">{note.title}</h4>
          <p className="mt-1 text-xs text-text-secondary"><time dateTime={note.createdAt}>{new Date(note.createdAt).toLocaleDateString(locale)}</time>{note.updatedAt !== note.createdAt && <> · {t("updatedOn", { date: new Date(note.updatedAt).toLocaleDateString(locale) })}</>}</p>
          <p className="mt-3 max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary">{note.body}</p>
        </article></li>)}</ol></div>}
      <p className="text-sm text-text-secondary">{t("developerContentNotice")}</p>
    </>}
  </section>;
}
