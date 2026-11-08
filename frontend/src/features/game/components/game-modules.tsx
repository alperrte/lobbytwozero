"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { moduleTypes, type CommunityDetail, type ModulePage, type ModuleType } from "../types/game";
import { controlClass, GameStatus } from "./game-status";

function ContentDetail({ gameId, contentId, onBack }: { gameId: string; contentId: string; onBack: () => void }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<CommunityDetail>({ kind: "content", gameId, contentId });
  return <div className="space-y-5">
    <button type="button" className={controlClass} onClick={onBack}>{t("backToContent")}</button>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && <article className="max-w-prose space-y-5 break-words">
      <h3 className="font-display text-2xl font-semibold">{result.data.title}</h3>
      <p className="text-sm text-text-secondary"><time dateTime={result.data.publishedAt}>{new Date(result.data.publishedAt).toLocaleDateString(locale)}</time></p>
      <p className="whitespace-pre-wrap leading-relaxed">{result.data.body}</p>
      <p className="text-sm text-text-secondary">{t("interactions", { likes: result.data.likeCount, comments: result.data.commentCount })}</p>
    </article>}
  </div>;
}

function ModuleContent({ gameId, type }: { gameId: string; type: ModuleType }) {
  const t = useTranslations("Game");
  const [page, setPage] = useState(0);
  const [contentId, setContentId] = useState<string>();
  const result = useGameResource<ModulePage>({ kind: "module", gameId, type, page });
  if (contentId) return <ContentDetail gameId={gameId} contentId={contentId} onBack={() => { setContentId(undefined); result.reload(); }} />;
  return <div>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && <>
      <p className="mb-4 text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p>
      {result.data.items.length === 0 ? <p className="py-8 text-text-secondary">{t("emptyModule")}</p> :
        <ul className="divide-y divide-border">
          {result.data.kind === "community" ? result.data.items.map((item) => <li key={item.id} className="py-5">
            <h3><button type="button" onClick={() => setContentId(item.id)} className="min-h-11 text-left font-display text-xl font-semibold text-brand-text underline-offset-4 hover:underline">{item.title}</button></h3>
            <p className="mt-2 max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary">{item.excerpt}</p>
          </li>) : result.data.items.map((item) => <li key={item.id} className="py-5">
            <h3 className="font-display text-lg font-semibold">{item.title}</h3>
            {item.description && <p className="mt-2 max-w-prose whitespace-pre-wrap break-words text-text-secondary">{item.description}</p>}
          </li>)}
        </ul>}
      <nav aria-label={t("pagination")} className="mt-6 flex flex-wrap items-center gap-3">
        <button type="button" className={controlClass} disabled={page === 0} onClick={() => setPage(page - 1)}>{t("previous")}</button>
        <span>{t("page", { page: page + 1 })}</span>
        <button type="button" className={controlClass} disabled={(page + 1) * result.data.size >= result.data.totalElements || page >= 10000} onClick={() => setPage(page + 1)}>{t("next")}</button>
      </nav>
    </>}
  </div>;
}

export function GameModules({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const [type, setType] = useState<ModuleType>("ACHIEVEMENTS");
  return <section aria-labelledby="game-modules" className="border-t border-border pt-8">
    <h2 id="game-modules" className="font-display text-2xl font-semibold">{t("modules")}</h2>
    <div role="group" aria-label={t("modules")} className="my-6 flex flex-wrap gap-2">
      {moduleTypes.map((value) => <button key={value} type="button" aria-pressed={type === value} className={`${controlClass} aria-pressed:border-brand-text aria-pressed:bg-surface-elevated aria-pressed:text-brand-text`} onClick={() => setType(value)}>{t(`types.${value}`)}</button>)}
    </div>
    <ModuleContent key={`${gameId}/${type}`} gameId={gameId} type={type} />
  </section>;
}
