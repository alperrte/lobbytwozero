"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { safeExternalUrl } from "../services/discovery-query";
import type { DeveloperContent, ManagedGame } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { GameCover } from "./game-cover";
import { GameFeedback } from "./game-feedback";
import { controlClass, GameStatus } from "./game-status";
import { Badge, EmptyState, Skeleton, Tabs } from "./game-ui";

function Moderation({ status, reason }: { status?: string; reason?: string | null }) {
  const t = useTranslations("Game");
  return <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-text-secondary">
    <Badge tone={status === "HIDDEN" ? "warning" : "success"}>{t(status === "HIDDEN" ? "hidden" : "visible")}</Badge>
    {status === "HIDDEN" && reason && <span>{t("moderationReason")}: {reason}</span>}
  </div>;
}

function DeveloperEditor({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const [section, setSection] = useState("description");
  const [editing, setEditing] = useState<string>();
  const result = useGameResource<DeveloperContent>({ kind: "developer", gameId }, { refreshOnFocus: false });
  const content = result.data;
  const note = content?.updateNotes.find((value) => value.id === editing);
  const items = [{ id: "description", label: t("tabs.description") }, { id: "media", label: t("tabs.media") }, { id: "notes", label: t("tabs.notes") }, { id: "feedback", label: t("receivedFeedback") }];
  return <section aria-label={t("developerContent")} className="space-y-5">
    <button type="button" className={controlClass} onClick={result.reload}>{t("reload")}</button>
    {result.loading && <Skeleton className="h-32" />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {content && <>
      <Tabs items={items} value={section} onChange={setSection} label={t("developerContent")} base={`dev-${gameId}`} />
      <div role="tabpanel" id={`dev-${gameId}-panel-${section}`} aria-labelledby={`dev-${gameId}-tab-${section}`}>
        {section === "description" && <>
          {content.description && <Moderation status={content.description.status} reason={content.description.moderationReason} />}
          {content.description?.status === "HIDDEN" && <p className="mt-2 text-sm text-text-secondary">{t("hiddenEditBlocked")}</p>}
          <GameActionForm key={`description/${content.description?.version ?? "new"}`} title={t("editDeveloperDescription")} disabled={content.description?.status === "HIDDEN"} fields={[{ name: "description", kind: "textarea", required: true, max: 8000 }]} initial={{ description: content.description?.text ?? "" }} build={(values) => ({ operation: "description", gameId, body: { description: values.description, ...(content.description ? { version: content.description.version } : {}) } })} onSaved={result.reload} />
        </>}
        {section === "media" && <>
          <GameActionForm title={t("addMedia")} description={t("mediaUrlOnly")} fields={[{ name: "type", required: true, options: ["IMAGE", "VIDEO", "DEMO"] }, { name: "url", kind: "url", required: true, max: 1024 }, { name: "altText", max: 200 }]} build={(body) => ({ operation: "addMedia", gameId, body })} onSaved={result.reload} resetOnSuccess />
          {!content.media.length ? <EmptyState title={t("noMedia")} /> : <ul className="grid gap-4 sm:grid-cols-2">{content.media.map((value) => <li key={value.id} className="overflow-hidden rounded-panel border border-border bg-surface">
            {value.type === "IMAGE" ? <GameCover url={value.url} title={value.altText ?? ""} /> : null}
            <div className="break-words p-4">
              <p className="flex flex-wrap items-center gap-2"><Badge>{t.has(`choices.${value.type}`) ? t(`choices.${value.type}`) : value.type}</Badge><span className="[overflow-wrap:anywhere]">{value.altText || value.url}</span></p>
              {value.type !== "IMAGE" && safeExternalUrl(value.url) && <a href={safeExternalUrl(value.url)} target="_blank" rel="noopener noreferrer" className="mt-2 inline-block min-h-11 py-2 text-brand-text underline">{t(value.type === "DEMO" ? "openDemo" : "openVideo")}</a>}
              <Moderation status={value.status} reason={value.moderationReason} />
              <GameActionForm title={t("deleteMedia")} fields={[]} destructive build={() => ({ operation: "deleteMedia", gameId, targetId: value.id })} onSaved={result.reload} />
            </div></li>)}</ul>}
        </>}
        {section === "notes" && <>
          {!content.updateNotes.length ? <EmptyState title={t("noNotes")} /> : <ul className="space-y-4">{content.updateNotes.map((value) => <li key={value.id} className="rounded-panel border border-border bg-surface p-4">
            <h3 className="font-display text-xl font-semibold [overflow-wrap:anywhere]">{value.title}</h3>
            <p className="mt-1 text-xs text-text-secondary">{t("createdOn", { date: new Date(value.createdAt).toLocaleDateString(locale) })} · {t("updatedOn", { date: new Date(value.updatedAt).toLocaleDateString(locale) })}</p>
            <p className="mt-3 whitespace-pre-wrap break-words text-text-secondary">{value.body}</p>
            <Moderation status={value.status} reason={value.moderationReason} />
            <button type="button" className={`${controlClass} mt-4`} onClick={() => setEditing(value.id)}>{t("edit")}</button>
            <GameActionForm title={t("deleteNote")} fields={[]} destructive build={() => ({ operation: "deleteNote", gameId, targetId: value.id })} onSaved={() => { setEditing(undefined); result.reload(); }} />
          </li>)}</ul>}
          {editing && <button type="button" className={`${controlClass} mt-4`} onClick={() => setEditing(undefined)}>{t("cancelEdit")}</button>}
          <GameActionForm key={note ? `${note.id}/${note.version}` : "new-note"} title={t(note ? "editNote" : "addNote")} disabled={note?.status === "HIDDEN"} fields={[{ name: "title", required: true, max: 120 }, { name: "body", kind: "textarea", required: true, max: 4000 }]} initial={note ? { title: note.title, body: note.body } : {}} build={(values) => ({ operation: note ? "updateNote" : "addNote", gameId, targetId: note?.id, body: { title: values.title, body: values.body, ...(note ? { version: note.version } : {}) } })} onSaved={() => { setEditing(undefined); result.reload(); }} resetOnSuccess={!note} />
        </>}
        {section === "feedback" && <GameFeedback gameId={gameId} developer />}
      </div>
    </>}
  </section>;
}

export function GameDeveloperTools({ initialGameId = "" }: { initialGameId?: string }) {
  const t = useTranslations("Game");
  const [chosen, choose] = useState(initialGameId);
  const result = useGameResource<ManagedGame[]>({ kind: "managed" }, { retainWhileRefreshing: true });
  const selected = result.data?.some((game) => game.gameId === chosen) ? chosen : result.data?.[0]?.gameId;
  return <div className="space-y-6">
    <h2 className="font-display text-2xl font-semibold">{t("workspace.developer")}</h2>
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {result.loading && !result.data && <Skeleton className="h-16" />}
    {result.data && (!result.data.length ? <EmptyState title={t("noManagedGames")}>{t("noManagedGamesHelp")}</EmptyState> : <label className="flex max-w-md flex-col gap-2 text-sm">{t("yourGames")}<select className={controlClass} value={selected} onChange={(event) => choose(event.target.value)}>{result.data.map((game) => <option key={game.gameId} value={game.gameId}>{game.title}</option>)}</select></label>)}
    {selected && <DeveloperEditor key={selected} gameId={selected} />}
  </div>;
}
