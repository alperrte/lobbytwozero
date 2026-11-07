"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { DeveloperContent, ManagedGame } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { GameFeedback } from "./game-feedback";
import { controlClass, GameStatus } from "./game-status";

function DeveloperEditor({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const result = useGameResource<DeveloperContent>({ kind: "developer", gameId }, { refreshOnFocus: false });
  const [editing, setEditing] = useState<string>();
  const content = result.data;
  const note = content?.updateNotes.find((value) => value.id === editing);
  return <section aria-label={t("developerContent")}>
    <button type="button" className={`${controlClass} my-5`} onClick={result.reload}>{t("reload")}</button>
    <GameStatus {...result} onRetry={result.reload} />
    {content && <>
      {content.description?.moderationReason && <p className="my-4 text-sm text-text-secondary">{t("moderationReason")}: {content.description.moderationReason}</p>}
      <GameActionForm key={`description/${content.description?.version ?? "new"}`} title={t("editDeveloperDescription")} disabled={content.description?.status === "HIDDEN"} fields={[{ name: "description", kind: "textarea", required: true, max: 8000 }]} initial={{ description: content.description?.text ?? "" }} build={(values) => ({ operation: "description", gameId, body: { description: values.description, ...(content.description ? { version: content.description.version } : {}) } })} onSaved={result.reload} />
      <GameActionForm title={t("addMedia")} fields={[{ name: "type", required: true, options: ["IMAGE", "VIDEO", "DEMO"] }, { name: "url", kind: "url", required: true, max: 1024 }, { name: "altText", max: 200 }]} build={(body) => ({ operation: "addMedia", gameId, body })} onSaved={result.reload} resetOnSuccess />
      <ul className="divide-y divide-border">{content.media.map((value) => <li key={value.id} className="break-words py-4"><p>{value.altText || value.url}</p><p className="mt-2 text-sm text-text-secondary">{t(value.status === "HIDDEN" ? "hidden" : "visible")}{value.moderationReason ? ` · ${value.moderationReason}` : ""}</p><GameActionForm title={t("deleteMedia")} fields={[]} destructive build={() => ({ operation: "deleteMedia", gameId, targetId: value.id })} onSaved={result.reload} /></li>)}</ul>
      <ul className="mt-5 divide-y divide-border">{content.updateNotes.map((value) => <li key={value.id} className="py-4"><h3 className="font-display text-xl font-semibold">{value.title}</h3><p className="mt-3 whitespace-pre-wrap break-words text-text-secondary">{value.body}</p><p className="mt-2 text-sm text-text-secondary">{t(value.status === "HIDDEN" ? "hidden" : "visible")}{value.moderationReason ? ` · ${value.moderationReason}` : ""}</p><button type="button" className={`${controlClass} mt-4`} onClick={() => setEditing(value.id)}>{t("edit")}</button><GameActionForm title={t("deleteNote")} fields={[]} destructive build={() => ({ operation: "deleteNote", gameId, targetId: value.id })} onSaved={() => { setEditing(undefined); result.reload(); }} /></li>)}</ul>
      {editing && <button type="button" className={`${controlClass} mt-4`} onClick={() => setEditing(undefined)}>{t("cancelEdit")}</button>}
      <GameActionForm key={note ? `${note.id}/${note.version}` : "new-note"} title={t(note ? "editNote" : "addNote")} disabled={note?.status === "HIDDEN"} fields={[{ name: "title", required: true, max: 120 }, { name: "body", kind: "textarea", required: true, max: 4000 }]} initial={note ? { title: note.title, body: note.body } : {}} build={(values) => ({ operation: note ? "updateNote" : "addNote", gameId, targetId: note?.id, body: { title: values.title, body: values.body, ...(note ? { version: note.version } : {}) } })} onSaved={() => { setEditing(undefined); result.reload(); }} resetOnSuccess={!note} />
      <GameFeedback gameId={gameId} developer />
    </>}
  </section>;
}

export function GameDeveloperTools({ initialGameId = "" }: { initialGameId?: string }) {
  const t = useTranslations("Game");
  const [chosen, choose] = useState(initialGameId);
  const result = useGameResource<ManagedGame[]>({ kind: "managed" }, { retainWhileRefreshing: true });
  const selected = result.data?.some((game) => game.gameId === chosen) ? chosen : result.data?.[0]?.gameId;
  return <div>
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && (!result.data.length ? <p className="py-8 text-text-secondary">{t("noManagedGames")}</p> : <label className="my-6 flex flex-col gap-2 text-sm">{t("fields.gameId")}<select className={controlClass} value={selected} onChange={(event) => choose(event.target.value)}>{result.data.map((game) => <option key={game.gameId} value={game.gameId}>{game.title}</option>)}</select></label>)}
    {selected && <DeveloperEditor key={selected} gameId={selected} />}
  </div>;
}
