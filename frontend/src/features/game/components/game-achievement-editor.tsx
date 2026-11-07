"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { reasonField } from "../schemas/game-forms";
import type { Achievement } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { controlClass, GameStatus } from "./game-status";

type ManagedAchievement = Achievement & { code: string; visible: boolean; version: number };
export function GameAchievementEditor({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const [page, setPage] = useState(0);
  const [editing, edit] = useState<ManagedAchievement>();
  const result = useGameResource<{ items: ManagedAchievement[]; totalElements: number }>({ kind: "adminAchievements", gameId, page }, { refreshOnFocus: false });
  return <section className="mt-8" aria-label={t("manageAchievements")}>
    <h3 className="font-display text-xl font-semibold">{t("manageAchievements")}</h3>
    <GameStatus {...result} onRetry={result.reload} />
    <ul className="mt-4 divide-y divide-border">{result.data?.items.map((value) => <li key={value.id} className="flex flex-wrap items-center justify-between gap-3 py-3"><span>{value.title} · {t(value.visible ? "visible" : "hidden")}</span><button type="button" className={controlClass} onClick={() => edit(value)}>{t("edit")}</button></li>)}</ul>
    {editing && <button type="button" className={`${controlClass} mt-4`} onClick={() => edit(undefined)}>{t("cancelEdit")}</button>}
    <GameActionForm key={editing ? `${editing.id}/${editing.version}` : "new"} title={t(editing ? "editAchievement" : "addAchievement")} fields={[...(!editing ? [{ name: "code", required: true, max: 64, pattern: "[a-z0-9][a-z0-9-]{0,63}" }] : []), { name: "title", required: true, max: 120 }, { name: "description", kind: "textarea", max: 2000 }, { name: "visible", required: true, options: ["true", "false"] }, reasonField]} initial={editing ? { title: editing.title, description: editing.description ?? "", visible: String(editing.visible) } : { visible: "true" }} build={(values) => ({ operation: "achievement", gameId, targetId: editing?.code ?? values.code, body: { title: values.title, description: values.description || null, visible: values.visible === "true", reason: values.reason, ...(editing ? { version: editing.version } : {}) } })} onSaved={() => { edit(undefined); result.reload(); }} resetOnSuccess={!editing} />
    {result.data && <nav aria-label={t("pagination")} className="flex flex-wrap items-center gap-3"><button type="button" className={controlClass} disabled={page === 0} onClick={() => setPage(page - 1)}>{t("previous")}</button><span>{t("page", { page: page + 1 })}</span><button type="button" className={controlClass} disabled={(page + 1) * 20 >= result.data.totalElements} onClick={() => setPage(page + 1)}>{t("next")}</button></nav>}
  </section>;
}
