"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { gameDefaults, gameEdit, gameFields, reasonField, uuidPattern } from "../schemas/game-forms";
import { isGameId } from "../services/game-api";
import type { AdminGame, DeveloperGrant } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { controlClass, GameStatus } from "./game-status";
import { GameAchievementEditor } from "./game-achievement-editor";

function DeveloperGrants({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const result = useGameResource<DeveloperGrant[]>({ kind: "grants", gameId }, { refreshOnFocus: false });
  return <section aria-label={t("developerGrants")}>
    <GameActionForm title={t("grantDeveloper")} fields={[{ name: "developerUserId", required: true, pattern: uuidPattern }, reasonField]} build={(body) => ({ operation: "grant", gameId, body })} onSaved={result.reload} resetOnSuccess />
    <GameStatus {...result} onRetry={result.reload} />
    {result.data?.map((grant) => <div key={grant.grantId} className="break-words"><p className="py-3 text-sm">{grant.developerUserId}</p><GameActionForm title={t("revokeDeveloper")} fields={[reasonField]} destructive build={(body) => ({ operation: "revoke", gameId, targetId: grant.developerUserId, body })} onSaved={result.reload} /></div>)}
  </section>;
}

function AdminEditor({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const result = useGameResource<AdminGame>({ kind: "admin", gameId }, { refreshOnFocus: false });
  const record = result.data;
  return <section aria-label={t("editGame")}>
    <button type="button" className={`${controlClass} my-5`} onClick={result.reload}>{t("reload")}</button>
    <GameStatus {...result} onRetry={result.reload} />
    {record && <>
      <p className="my-4 text-sm text-text-secondary">{t("gameStatus", { status: t.has(`choices.${record.status}`) ? t(`choices.${record.status}`) : t("unknown") })}</p>
      <p className="text-sm text-text-secondary">{t("listHelp")}</p>
      <GameActionForm key={record.version} title={t("editGame")} fields={gameFields} initial={gameDefaults(record.game)} build={(values) => ({ operation: "update", gameId, body: gameEdit(values, record) })} onSaved={result.reload} />
      {["ACTIVE", "ARCHIVED"].includes(record.status) && <GameActionForm title={t(record.status === "ACTIVE" ? "archiveGame" : "restoreGame")} fields={[reasonField]} destructive build={(body) => ({ operation: record.status === "ACTIVE" ? "archive" : "restore", gameId, body })} onSaved={result.reload} />}
      <section aria-label={t("manualOverrides")} className="mt-6"><h3 className="font-display text-xl font-semibold">{t("manualOverrides")}</h3>
        {!record.manualOverrides.length && <p className="py-4 text-text-secondary">{t("noOverrides")}</p>}
        {record.manualOverrides.map((override) => <GameActionForm key={override.field} title={`${t("releaseOverride")}: ${t(`overrideFields.${override.field}`)}`} fields={[reasonField]} destructive build={(body) => ({ operation: "release", gameId, field: override.field, body })} onSaved={result.reload} />)}
      </section>
      <details className="mt-6 rounded-panel border border-border p-4"><summary className="cursor-pointer py-2 font-semibold">{t("developerGrants")}</summary><DeveloperGrants gameId={gameId} /></details>
      {record.status === "ACTIVE" && <details className="mt-6 rounded-panel border border-border p-4"><summary className="cursor-pointer py-2 font-semibold">{t("manageAchievements")}</summary><GameAchievementEditor gameId={gameId} /></details>}
    </>}
  </section>;
}

export function GameAdminTools({ initialGameId = "" }: { initialGameId?: string }) {
  const t = useTranslations("Game");
  const [selected, select] = useState(isGameId(initialGameId) ? initialGameId : "");
  return <div>
    <details open={!selected} className="mt-6 rounded-panel border border-border p-4"><summary className="cursor-pointer py-2 font-semibold">{t("createGame")}</summary><GameActionForm title={t("createGame")} fields={gameFields} build={(values) => ({ operation: "create", body: gameEdit(values) })} onSaved={(value) => { if (value && typeof value === "object" && "gameId" in value && typeof value.gameId === "string") select(value.gameId); }} resetOnSuccess /></details>
    <form className="my-6 flex flex-wrap items-end gap-4" onSubmit={(event) => { event.preventDefault(); const value = String(new FormData(event.currentTarget).get("gameId")); if (isGameId(value)) select(value); }}>
      <label className="flex min-w-0 flex-1 flex-col gap-2 text-sm">{t("fields.gameId")}<input name="gameId" required pattern={uuidPattern} defaultValue={initialGameId} className={controlClass} /></label><button className={controlClass}>{t("loadGame")}</button>
    </form>
    {selected && <AdminEditor key={selected} gameId={selected} />}
    <details className="mt-6 rounded-panel border border-border p-4"><summary className="cursor-pointer py-2 font-semibold">{t("popularSelection")}</summary><GameActionForm title={t("popularSelection")} fields={[{ name: "gameIds", kind: "textarea", max: 4000 }, reasonField]} destructive build={(values) => ({ operation: "popular", body: { gameIds: values.gameIds.split(/[\s,]+/).filter(Boolean), reason: values.reason } })} /></details>
  </div>;
}
