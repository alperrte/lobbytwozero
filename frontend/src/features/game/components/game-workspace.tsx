"use client";

import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameSession } from "../types/game";
import { GameAdminTools } from "./game-admin-tools";
import { GameDeveloperTools } from "./game-developer-tools";
import { GameModeration } from "./game-moderation";
import { GameSectionNav, staffSections } from "./game-section-nav";
import { GameStatus } from "./game-status";
import { cn } from "@/shared/utils/cn";
import { EmptyState, gameScopeClass, Skeleton } from "./game-ui";

/**
 * Yönetim bölümü `?section=developer|admin|moderation` ile seçilir (bağlantılanabilir). Bölüm bağlantıları ortak Game gezintisindedir.
 * Bu yalnız arayüz kolaylığıdır; yetkiyi her istekte sunucu denetler. MODERATOR, ADMIN veya GAME_DEVELOPER yetkisi vermez.
 */
function Workspace({ session, section, initialGameId }: { session: GameSession; section: string | null; initialGameId: string }) {
  const t = useTranslations("Game");
  const sections = staffSections(session.roles);
  const active = sections.find((value) => value === section) ?? sections[0];
  if (!active) return <EmptyState title={t("errors.403")}>{t("noWorkspaceAccess")}</EmptyState>;
  return <div className="space-y-6">
    {active === "admin" && <GameAdminTools initialGameId={initialGameId} />}
    {active === "developer" && <><h2 className="sr-only">{t("developerContent")}</h2><GameDeveloperTools initialGameId={initialGameId} /></>}
    {active === "moderation" && <><h2 className="sr-only">{t("moderateContent")}</h2><GameModeration initialGameId={initialGameId} /></>}
  </div>;
}

export function GameWorkspace() {
  const t = useTranslations("Game");
  const params = useSearchParams();
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true });
  const section = params.get("section");
  const sections = session.data ? staffSections(session.data.roles) : [];
  const active = sections.find((value) => value === section) ?? sections[0] ?? null;
  return <div className={cn("space-y-6", gameScopeClass)}>
    {session.data !== undefined && <GameSectionNav current={active} session={session.data} />}
    <h1 className="font-display text-3xl font-semibold sm:text-4xl">{active ? t(`workspace.${active}`) : t("manageGames")}</h1>
    {session.loading && !session.data && session.data !== null && <Skeleton className="h-24" />}
    {!session.data && <GameStatus error={session.error} loading={false} onRetry={session.reload} />}
    {session.data === null && <EmptyState title={t("signInRequired")} />}
    {session.data && <Workspace key={session.data.accountId} session={session.data} section={section} initialGameId={params.get("gameId") ?? ""} />}
  </div>;
}
