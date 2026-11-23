import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { GameWorkspace } from "@/features/game/components/game-workspace";

export async function generateMetadata() {
  const t = await getTranslations("Game");
  return { title: t("manageGames"), robots: { index: false, follow: false } };
}

export default function GameManagementPage() {
  return <Suspense><GameWorkspace /></Suspense>;
}
