"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameMutation } from "../hooks/use-game-mutation";
import { useGameResource } from "../hooks/use-game-resource";
import type { CardsResponse, Feedback, FeedbackPage } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { controlClass, GameStatus } from "./game-status";
import { Badge, EmptyState, Pagination, Skeleton } from "./game-ui";

const statuses = ["OPEN", "IN_PROGRESS", "RESOLVED"] as const;
const types = ["GENERAL", "BUG_REPORT", "EARLY_ACCESS"] as const;

/** Durum akışı OPEN → IN_PROGRESS → RESOLVED; oyuncu durumu değiştiremez, yalnız izler. */
function StatusSteps({ status }: { status: Feedback["status"] }) {
  const t = useTranslations("Game");
  const current = statuses.indexOf(status);
  return <ol aria-label={t("fields.status")} className="flex flex-wrap items-center gap-2 text-xs">
    {statuses.map((value, index) => <li key={value} aria-current={index === current ? "step" : undefined} className={`rounded-full border px-3 py-1 ${index === current ? "border-primary bg-primary/10 font-semibold text-brand-text" : index < current ? "border-success/50 text-success" : "border-border text-text-secondary"}`}>{t(`choices.${value}`)}</li>)}
  </ol>;
}

function FeedbackDetail({ item, gameId, developer }: { item: Feedback; gameId?: string; developer: boolean }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<Feedback>({ kind: "feedbackItem", id: item.id, gameId, developer }, { refreshOnFocus: false });
  const full = result.data;
  return <div className="mt-4 space-y-4 border-t border-border pt-4">
    <GameStatus {...result} onRetry={result.reload} />
    {full && <>
      <p className="max-w-prose whitespace-pre-wrap break-words leading-relaxed">{full.body}</p>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-text-secondary">{t("createdAt")}</dt><dd>{new Date(full.createdAt).toLocaleString(locale)}</dd></div>
        <div><dt className="text-text-secondary">{t("updatedAt")}</dt><dd>{new Date(full.updatedAt).toLocaleString(locale)}</dd></div>
      </dl>
    </>}
  </div>;
}

function FeedbackRow({ item, gameId, developer, title, onChanged, mutation }: { item: Feedback; gameId?: string; developer: boolean; title?: string; onChanged: () => void; mutation: ReturnType<typeof useGameMutation> }) {
  const t = useTranslations("Game");
  const [open, setOpen] = useState(false);
  const tone = item.status === "RESOLVED" ? "success" : item.status === "IN_PROGRESS" ? "brand" : "neutral";
  return <li className="rounded-panel border border-border bg-surface p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2"><Badge>{t(`choices.${item.type}`)}</Badge><Badge tone={tone}>{t(`choices.${item.status}`)}</Badge></div>
        <h3 className="mt-2 font-display text-lg font-semibold [overflow-wrap:anywhere]">{item.title}</h3>
        {title && <p className="text-sm text-text-secondary">{title}</p>}
      </div>
      <StatusSteps status={item.status} />
    </div>
    <div className="mt-3 flex flex-wrap gap-3">
      <button type="button" aria-expanded={open} className={controlClass} onClick={() => setOpen(!open)}>{t(open ? "hideDetail" : "showDetail")}</button>
      {developer && item.status !== "RESOLVED" && <button type="button" className={controlClass} disabled={mutation.pending} onClick={async () => {
        const saved = await mutation.run({ operation: "feedbackStatus", gameId, targetId: item.id, body: { status: item.status === "OPEN" ? "IN_PROGRESS" : "RESOLVED", version: item.version } });
        if (saved.ok) onChanged();
      }}>{t(item.status === "OPEN" ? "startFeedback" : "resolveFeedback")}</button>}
    </div>
    {open && <FeedbackDetail item={item} gameId={gameId} developer={developer} />}
  </li>;
}

/** Sayfadaki oyun başlıkları tek toplu kart isteğiyle alınır; başarısızsa satırlar başlıksız kalır. */
function Titles({ page, children }: { page: FeedbackPage; children: (titles: Map<string, string>) => React.ReactNode }) {
  const ids = [...new Set(page.items.map((item) => item.gameId))].slice(0, 50);
  const result = useGameResource<CardsResponse>({ kind: "cards", gameIds: ids }, { refreshOnFocus: false });
  return <>{children(new Map(result.data?.items.map((card) => [card.gameId, card.title])))}</>;
}

/** Oyuncunun (gameId verilirse tek oyun için) veya geliştiricinin gelen kutusu. */
export function FeedbackList({ gameId, developer = false }: { gameId?: string; developer?: boolean }) {
  const t = useTranslations("Game");
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const mutation = useGameMutation();
  const result = useGameResource<FeedbackPage>(developer && gameId
    ? { kind: "feedback", gameId, developer: true, page, status, type }
    : { kind: "myFeedback", page, gameId, status, type }, { refreshOnFocus: false });
  return <div>
    <div className="my-5 flex flex-wrap gap-4">
      <label className="flex flex-col gap-2 text-sm">{t("fields.status")}<select className={controlClass} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">{t("all")}</option>{statuses.map((value) => <option key={value} value={value}>{t(`choices.${value}`)}</option>)}</select></label>
      <label className="flex flex-col gap-2 text-sm">{t("fields.type")}<select className={controlClass} value={type} onChange={(event) => { setType(event.target.value); setPage(0); }}><option value="">{t("all")}</option>{types.map((value) => <option key={value} value={value}>{t(`choices.${value}`)}</option>)}</select></label>
      <button type="button" className={`${controlClass} self-end`} onClick={result.reload}>{t("reload")}</button>
    </div>
    {result.loading && <div aria-hidden="true" className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    <GameStatus error={mutation.error} loading={false} onRetry={result.reload} />
    {result.data && (!result.data.items.length
      ? <EmptyState title={t(developer ? "emptyFeedbackDeveloper" : "emptyFeedback")} />
      : <>
        <p className="mb-3 text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p>
        <Titles page={result.data}>{(titles) => <ul className="space-y-3">{result.data!.items.map((item) => <FeedbackRow key={item.id} item={item} gameId={gameId} developer={developer} title={!gameId ? titles.get(item.gameId) : undefined} onChanged={result.reload} mutation={mutation} />)}</ul>}</Titles>
        <Pagination page={page} size={20} totalElements={result.data.totalElements} onChange={setPage} />
      </>)}
  </div>;
}

export function FeedbackForm({ gameId, earlyAccess = false, onSaved }: { gameId: string; earlyAccess?: boolean; onSaved: () => void }) {
  const t = useTranslations("Game");
  return <div>
    <p className="mb-2 text-sm text-text-secondary">{t("feedbackPrivacy")}</p>
    <GameActionForm title={t("sendFeedback")} fields={[{ name: "type", required: true, options: ["GENERAL", "BUG_REPORT", ...(earlyAccess ? ["EARLY_ACCESS"] : [])] }, { name: "title", required: true, max: 120 }, { name: "body", kind: "textarea", required: true, max: 4000 }]} build={(values) => ({ operation: "feedback", body: { gameId, ...values } })} resetOnSuccess onSaved={onSaved} />
  </div>;
}

/** Tek oyun için geri bildirim bölümü (oyun detayı) ve geliştirici gelen kutusu. */
export function GameFeedback({ gameId, developer = false, earlyAccess = false }: { gameId: string; developer?: boolean; earlyAccess?: boolean }) {
  const t = useTranslations("Game");
  const [signal, setSignal] = useState(0);
  return <section aria-label={t(developer ? "receivedFeedback" : "myFeedback")} className="border-t border-border pt-8">
    <h2 className="font-display text-2xl font-semibold">{t(developer ? "receivedFeedback" : "myFeedback")}</h2>
    {!developer && <FeedbackForm gameId={gameId} earlyAccess={earlyAccess} onSaved={() => setSignal(signal + 1)} />}
    <FeedbackList key={signal} gameId={gameId} developer={developer} />
  </section>;
}
