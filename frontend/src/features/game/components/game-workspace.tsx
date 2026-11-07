"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameSession } from "../types/game";
import { GameAdminTools } from "./game-admin-tools";
import { GameDeveloperTools } from "./game-developer-tools";
import { GameModeration } from "./game-moderation";
import { GameStatus } from "./game-status";
import { EmptyState, Skeleton, Tabs } from "./game-ui";

/** Sekmeler yalnız arayüz kolaylığıdır; yetkiyi her istekte sunucu denetler. MODERATOR, ADMIN veya GAME_DEVELOPER yetkisi vermez. */
function Workspace({ session, initialGameId }: { session: GameSession; initialGameId: string }) {
  const t = useTranslations("Game");
  const sections = [session.roles.includes("ADMIN") ? "admin" : "", session.roles.includes("GAME_DEVELOPER") ? "developer" : "", session.roles.some((role) => ["ADMIN", "MODERATOR"].includes(role)) ? "moderation" : ""].filter(Boolean);
  const [selected, select] = useState(sections[0] ?? "");
  const active = sections.includes(selected) ? selected : sections[0];
  if (!active) return <EmptyState title={t("errors.403")}>{t("noWorkspaceAccess")}</EmptyState>;
  return <>
    <div className="my-6">{sections.length > 1
      ? <Tabs items={sections.map((value) => ({ id: value, label: t(`workspace.${value}`) }))} value={active} onChange={select} label={t("manageGames")} base="workspace" />
      : <p className="text-sm font-semibold text-brand-text">{t(`workspace.${active}`)}</p>}</div>
    <div role="tabpanel" id={`workspace-panel-${active}`} aria-labelledby={`workspace-tab-${active}`}>
      {active === "admin" && <GameAdminTools initialGameId={initialGameId} />}
      {active === "developer" && <GameDeveloperTools initialGameId={initialGameId} />}
      {active === "moderation" && <GameModeration initialGameId={initialGameId} />}
    </div>
  </>;
}

export function GameWorkspace() {
  const t = useTranslations("Game");
  const params = useSearchParams();
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true });
  return <div className="space-y-4">
    <Link href="/games" className="inline-flex min-h-11 items-center gap-2 text-brand-text underline-offset-4 hover:underline"><Icon name="arrowRight" width={16} height={16} className="rotate-180" />{t("backToGames")}</Link>
    <h1 className="font-display text-3xl font-semibold sm:text-4xl">{t("manageGames")}</h1>
    {session.loading && !session.data && session.data !== null && <Skeleton className="h-24" />}
    {!session.data && <GameStatus error={session.error} loading={false} onRetry={session.reload} />}
    {session.data === null && <EmptyState title={t("signInRequired")} />}
    {session.data && <Workspace key={session.data.accountId} session={session.data} initialGameId={params.get("gameId") ?? ""} />}
  </div>;
}
