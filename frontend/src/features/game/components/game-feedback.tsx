"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { useGameMutation } from "../hooks/use-game-mutation";
import type { FeedbackPage } from "../types/game";
import { controlClass, GameStatus } from "./game-status";
import { GameActionForm } from "./game-action-form";

export function GameFeedback({ gameId, developer = false, earlyAccess = false }: { gameId: string; developer?: boolean; earlyAccess?: boolean }) {
  const t = useTranslations("Game");
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const result = useGameResource<FeedbackPage>({ kind: "feedback", gameId, developer, page, status, type });
  const mutation = useGameMutation();
  return <section aria-label={t(developer ? "receivedFeedback" : "myFeedback")} className="mt-10 border-t border-border pt-8">
    <h2 className="font-display text-2xl font-semibold">{t(developer ? "receivedFeedback" : "myFeedback")}</h2>
    {!developer && <GameActionForm title={t("sendFeedback")} fields={[{ name: "type", required: true, options: ["GENERAL", "BUG_REPORT", ...(earlyAccess ? ["EARLY_ACCESS"] : [])] }, { name: "title", required: true, max: 120 }, { name: "body", kind: "textarea", required: true, max: 4000 }]} build={(values) => ({ operation: "feedback", body: { gameId, ...values } })} resetOnSuccess onSaved={() => { setPage(0); result.reload(); }} />}
    <div className="my-5 flex flex-wrap gap-4">
      <label className="flex flex-col gap-2 text-sm">{t("fields.status")}<select className={controlClass} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">{t("all")}</option>{["OPEN", "IN_PROGRESS", "RESOLVED"].map((value) => <option key={value} value={value}>{t(`choices.${value}`)}</option>)}</select></label>
      <label className="flex flex-col gap-2 text-sm">{t("fields.type")}<select className={controlClass} value={type} onChange={(event) => { setType(event.target.value); setPage(0); }}><option value="">{t("all")}</option>{["GENERAL", "BUG_REPORT", "EARLY_ACCESS"].map((value) => <option key={value} value={value}>{t(`choices.${value}`)}</option>)}</select></label>
      <button type="button" className={`${controlClass} self-end`} onClick={result.reload}>{t("reload")}</button>
    </div>
    <GameStatus {...result} onRetry={result.reload} />
    <GameStatus error={mutation.error} loading={mutation.pending} onRetry={result.reload} />
    {result.data && <>
      <p className="text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p>
      {!result.data.items.length && <p className="py-6 text-text-secondary">{t("emptyModule")}</p>}
      <ul className="divide-y divide-border">{result.data.items.map((item) => <li key={item.id} className="py-5">
        <div className="flex flex-wrap items-baseline justify-between gap-3"><h3 className="font-display text-xl font-semibold">{item.title}</h3><span className="text-sm text-text-secondary">{t(`choices.${item.status}`)}</span></div>
        <p className="mt-3 max-w-prose whitespace-pre-wrap break-words text-text-secondary">{item.body}</p>
        {developer && item.status !== "RESOLVED" && <button type="button" className={`${controlClass} mt-4`} disabled={mutation.pending} onClick={async () => {
          const saved = await mutation.run({ operation: "feedbackStatus", gameId, targetId: item.id, body: { status: item.status === "OPEN" ? "IN_PROGRESS" : "RESOLVED", version: item.version } });
          if (saved.ok) result.reload();
        }}>{t(item.status === "OPEN" ? "startFeedback" : "resolveFeedback")}</button>}
      </li>)}</ul>
      <nav aria-label={t("pagination")} className="mt-5 flex flex-wrap items-center gap-3"><button type="button" className={controlClass} disabled={page === 0} onClick={() => setPage(page - 1)}>{t("previous")}</button><span>{t("page", { page: page + 1 })}</span><button type="button" className={controlClass} disabled={(page + 1) * 20 >= result.data.totalElements || page >= 10000} onClick={() => setPage(page + 1)}>{t("next")}</button></nav>
    </>}
  </section>;
}
