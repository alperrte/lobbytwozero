import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";
import { PageContainer } from "@/shared/components/layout/page-container";
import { GameWorkspace } from "@/features/game/components/game-workspace";

export async function generateMetadata() {
  const t = await getTranslations("Game");
  return { title: t("manageGames"), robots: { index: false, follow: false } };
}

export default function GameManagementPage() {
  return <AppShell footer={<Footer />}><PageContainer><Suspense><GameWorkspace /></Suspense></PageContainer></AppShell>;
}
