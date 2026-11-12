"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameSession } from "../types/game";
import { GameStatus } from "./game-status";

export function GameSessionActions({ gameId }: { gameId?: string }) {
  const t = useTranslations("Game");
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true });
  if (session.error) return gameId ? <GameStatus {...session} onRetry={session.reload} /> : null;
  if (session.loading && !session.data) return null;
  if (!session.data) return null;
  const canManage = session.data.roles.some((role) => ["ADMIN", "MODERATOR", "GAME_DEVELOPER"].includes(role));
  return <div key={session.data.accountId}>
    {canManage && <Link href={{ pathname: "/games/manage", query: gameId ? { gameId } : undefined }} className="mt-6 inline-block py-3 text-brand-text underline-offset-4 hover:underline">{t("manageGames")}</Link>}
  </div>;
}
