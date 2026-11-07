"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import { reasonField, uuidPattern } from "../schemas/game-forms";
import type { GameSession } from "../types/game";
import { GameAdminTools } from "./game-admin-tools";
import { GameDeveloperTools } from "./game-developer-tools";
import { GameActionForm } from "./game-action-form";
import { controlClass, GameStatus } from "./game-status";

function Workspace({ session, initialGameId }: { session: GameSession; initialGameId: string }) {
  const t = useTranslations("Game");
  const sections = [session.roles.includes("ADMIN") ? "admin" : "", session.roles.includes("GAME_DEVELOPER") ? "developer" : "", session.roles.some((role) => ["ADMIN", "MODERATOR"].includes(role)) ? "moderation" : ""].filter(Boolean);
  const [selected, select] = useState(sections[0] ?? "");
  const active = sections.includes(selected) ? selected : sections[0];
  if (!active) return <p className="py-8">{t("errors.403")}</p>;
  return <>
    <div role="group" aria-label={t("manageGames")} className="my-6 flex flex-wrap gap-3">{sections.map((value) => <button type="button" key={value} aria-pressed={active === value} className={`${controlClass} aria-pressed:border-brand-text aria-pressed:text-brand-text`} onClick={() => select(value)}>{t(`workspace.${value}`)}</button>)}</div>
    {active === "admin" && <GameAdminTools initialGameId={initialGameId} />}
    {active === "developer" && <GameDeveloperTools initialGameId={initialGameId} />}
    {active === "moderation" && <GameActionForm title={t("moderateContent")} fields={[{ name: "gameId", required: true, pattern: uuidPattern }, { name: "target", required: true, options: ["DESCRIPTION", "MEDIA", "UPDATE_NOTE"] }, { name: "targetId", pattern: uuidPattern }, { name: "action", required: true, options: ["HIDE", "RESTORE"] }, reasonField]} initial={{ gameId: initialGameId }} destructive build={(values) => ({ operation: "moderate", gameId: values.gameId, body: { target: values.target, targetId: values.target === "DESCRIPTION" ? null : values.targetId, action: values.action, reason: values.reason } })} />}
  </>;
}

export function GameWorkspace() {
  const t = useTranslations("Game");
  const params = useSearchParams();
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true });
  return <>
    <Link href="/games" className="inline-block py-3 text-brand-text underline">{t("backToGames")}</Link>
    <h1 className="mt-4 font-display text-3xl font-semibold sm:text-4xl">{t("manageGames")}</h1>
    {!session.data && <GameStatus {...session} onRetry={session.reload} />}
    {session.data === null && <p className="py-8">{t("signInRequired")}</p>}
    {session.data && <Workspace key={session.data.accountId} session={session.data} initialGameId={params.get("gameId") ?? ""} />}
  </>;
}
