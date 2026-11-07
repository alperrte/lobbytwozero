"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { gameDefaults, gameEdit, gameFields, reasonField, uuidPattern } from "../schemas/game-forms";
import { isGameId } from "../services/game-api";
import type { AdminGame, DeveloperGrant } from "../types/game";
import { GameAchievementEditor } from "./game-achievement-editor";
import { GameActionForm } from "./game-action-form";
import { GameCatalogStatus } from "./game-catalog-status";
import { GameIdentityNotice } from "./game-identity-notice";
import { GamePicker } from "./game-picker";
import { GamePopularEditor } from "./game-popular-editor";
import { controlClass, GameStatus } from "./game-status";
import { Badge, panelClass, Skeleton, Tabs } from "./game-ui";

function DeveloperGrants({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<DeveloperGrant[]>({ kind: "grants", gameId }, { refreshOnFocus: false });
  return <section aria-label={t("developerGrants")} className="space-y-4">
    <p className="max-w-prose text-sm text-text-secondary">{t("grantScopeHelp")}</p>
    <GameActionForm title={t("grantDeveloper")} fields={[{ name: "developerUserId", required: true, pattern: uuidPattern }, reasonField]} build={(body) => ({ operation: "grant", gameId, body })} onSaved={result.reload} resetOnSuccess />
    <h3 className="font-display text-xl font-semibold">{t("activeGrants")}</h3>
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {result.loading && <Skeleton className="h-16" />}
    {result.data && !result.data.length && <p className="text-text-secondary">{t("noGrants")}</p>}
    <ul className="space-y-3">{result.data?.map((grant) => <li key={grant.grantId} className="break-words rounded-panel border border-border bg-surface px-4">
      <p className="pt-3 text-sm"><span className="text-text-secondary">{t("grantedUser")}:</span> {grant.developerUserId}</p>
      <p className="text-xs text-text-secondary">{t("grantedAt", { date: new Date(grant.grantedAt).toLocaleString(locale) })}</p>
      <GameActionForm title={t("revokeDeveloper")} fields={[reasonField]} destructive build={(body) => ({ operation: "revoke", gameId, targetId: grant.developerUserId, body })} onSaved={result.reload} />
    </li>)}</ul>
  </section>;
}

function GameRecord({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const [section, setSection] = useState("edit");
  const result = useGameResource<AdminGame>({ kind: "admin", gameId }, { refreshOnFocus: false });
  const record = result.data;
  const items = [{ id: "edit", label: t("editGame") }, { id: "lifecycle", label: t("lifecycle") }, { id: "developers", label: t("developerGrants") }, ...(record?.status === "ACTIVE" ? [{ id: "achievements", label: t("manageAchievements") }] : [])];
  const active = items.some((item) => item.id === section) ? section : "edit";
  return <section aria-label={t("selectedGame")} className="space-y-5">
    {result.loading && <Skeleton className="h-24" />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {record && <>
      <div className={`${panelClass} flex flex-wrap items-center justify-between gap-3`}>
        <div className="min-w-0"><h2 className="font-display text-2xl font-semibold [overflow-wrap:anywhere]">{record.game.game.title}</h2>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-text-secondary"><Badge tone={record.status === "ACTIVE" ? "success" : "warning"}>{t.has(`choices.${record.status}`) ? t(`choices.${record.status}`) : t("unknown")}</Badge>{t("recordVersion", { version: record.version })}</p></div>
        <button type="button" className={controlClass} onClick={result.reload}>{t("reload")}</button>
      </div>
      <Tabs items={items} value={active} onChange={setSection} label={t("selectedGame")} base={`admin-${gameId}`} />
      <div role="tabpanel" id={`admin-${gameId}-panel-${active}`} aria-labelledby={`admin-${gameId}-tab-${active}`}>
        {active === "edit" && <>
          <p className="text-sm text-text-secondary">{t("listHelp")}</p>
          <GameActionForm key={record.version} title={t("editGame")} fields={gameFields} initial={gameDefaults(record.game)} build={(values) => ({ operation: "update", gameId, body: gameEdit(values, record) })} onSaved={result.reload} />
          <section aria-label={t("manualOverrides")} className="mt-6"><h3 className="font-display text-xl font-semibold">{t("manualOverrides")}</h3>
            <p className="mt-1 max-w-prose text-sm text-text-secondary">{t("manualOverridesHelp")}</p>
            {!record.manualOverrides.length && <p className="py-4 text-text-secondary">{t("noOverrides")}</p>}
            {record.manualOverrides.map((override) => <GameActionForm key={override.field} title={`${t("releaseOverride")}: ${t.has(`overrideFields.${override.field}`) ? t(`overrideFields.${override.field}`) : override.field}`} description={t("overrideLockedAt", { date: new Date(override.markedAt).toLocaleString() })} fields={[reasonField]} destructive build={(body) => ({ operation: "release", gameId, field: override.field, body })} onSaved={result.reload} />)}
          </section>
        </>}
        {active === "lifecycle" && (["ACTIVE", "ARCHIVED"].includes(record.status)
          ? <GameActionForm title={t(record.status === "ACTIVE" ? "archiveGame" : "restoreGame")} description={t(record.status === "ACTIVE" ? "archiveHelp" : "restoreHelp")} fields={[reasonField]} destructive build={(body) => ({ operation: record.status === "ACTIVE" ? "archive" : "restore", gameId, body })} onSaved={result.reload} />
          : <p className="py-4 text-text-secondary">{t("lifecycleUnavailable")}</p>)}
        {active === "developers" && <DeveloperGrants gameId={gameId} />}
        {active === "achievements" && <GameAchievementEditor gameId={gameId} />}
      </div>
    </>}
  </section>;
}

function GamesSection({ initialGameId }: { initialGameId: string }) {
  const t = useTranslations("Game");
  const [selected, select] = useState(isGameId(initialGameId) ? initialGameId : "");
  return <div className="space-y-6">
    <details open={!selected} className={panelClass}><summary className="cursor-pointer py-2 font-semibold">{t("createGame")}</summary>
      <GameActionForm title={t("createGame")} fields={gameFields} build={(values) => ({ operation: "create", body: gameEdit(values) })} onSaved={(value) => { if (value && typeof value === "object" && "gameId" in value && typeof value.gameId === "string") select(value.gameId); }} resetOnSuccess /></details>
    <section aria-label={t("findGame")} className={panelClass}>
      <GamePicker label={t("findGame")} onPick={(game) => select(game.gameId)} />
      <details className="mt-4"><summary className="cursor-pointer py-2 text-sm text-text-secondary">{t("loadById")}</summary>
        <form className="mt-2 flex flex-wrap items-end gap-4" onSubmit={(event) => { event.preventDefault(); const value = String(new FormData(event.currentTarget).get("gameId")).trim(); if (isGameId(value)) select(value); }}>
          <label className="flex min-w-0 flex-1 flex-col gap-2 text-sm">{t("fields.gameId")}<input name="gameId" required pattern={uuidPattern} defaultValue={initialGameId} className={controlClass} /></label><button className={controlClass}>{t("loadGame")}</button>
        </form>
        <p className="mt-2 text-xs text-text-secondary">{t("loadByIdHelp")}</p>
      </details>
    </section>
    {selected && <GameRecord key={selected} gameId={selected} />}
  </div>;
}

export function GameAdminTools({ initialGameId = "" }: { initialGameId?: string }) {
  const t = useTranslations("Game");
  const [section, setSection] = useState("games");
  const items = [{ id: "games", label: t("adminGames") }, { id: "popular", label: t("popularSelection") }, { id: "catalog", label: t("catalogStatus") }, { id: "identity", label: t("identityTitle") }];
  return <div className="space-y-6">
    <Tabs items={items} value={section} onChange={setSection} label={t("workspace.admin")} base="admin" />
    <div role="tabpanel" id={`admin-panel-${section}`} aria-labelledby={`admin-tab-${section}`}>
      {section === "games" && <GamesSection initialGameId={initialGameId} />}
      {section === "popular" && <GamePopularEditor />}
      {section === "catalog" && <GameCatalogStatus defaultOpen />}
      {section === "identity" && <GameIdentityNotice />}
    </div>
  </div>;
}
