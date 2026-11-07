"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { safeExternalUrl } from "../services/discovery-query";
import type { DeveloperContent } from "../types/game";
import { GameStatus } from "./game-status";

export function GameDeveloperPublic({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const result = useGameResource<DeveloperContent>({ kind: "publicDeveloper", gameId });
  return <section className="mt-10 border-t border-border pt-8" aria-labelledby="game-developer-content">
    <h2 id="game-developer-content" className="font-display text-2xl font-semibold">{t("developerContent")}</h2>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && <>
      {!result.data.description && !result.data.media.length && !result.data.updateNotes.length && <p className="mt-4 text-text-secondary">{t("emptyModule")}</p>}
      {result.data.description && <p className="mt-5 max-w-prose whitespace-pre-wrap break-words leading-relaxed">{result.data.description.text}</p>}
      <ul className="mt-6 grid gap-4 sm:grid-cols-2">{result.data.media.filter((value) => safeExternalUrl(value.url)).map((value) => <li key={value.id}>{value.type === "IMAGE" ? <Image unoptimized width={800} height={450} src={safeExternalUrl(value.url)!} alt={value.altText || t("developerContent")} referrerPolicy="no-referrer" className="h-auto max-h-96 w-full rounded-panel object-contain" /> : <a href={safeExternalUrl(value.url)} target="_blank" rel="noopener noreferrer" className="inline-block py-3 text-brand-text underline">{t(value.type === "DEMO" ? "openDemo" : "openVideo")}</a>}</li>)}</ul>
      <div className="mt-6 divide-y divide-border">{result.data.updateNotes.map((note) => <article key={note.id} className="py-5"><h3 className="font-display text-xl font-semibold">{note.title}</h3><p className="mt-3 max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary">{note.body}</p></article>)}</div>
      <p className="mt-4 text-sm text-text-secondary">{t("developerContentNotice")}</p>
    </>}
  </section>;
}
