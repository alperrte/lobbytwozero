import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { PageContainer } from "@/shared/components/layout/page-container";
import { GameDetail } from "@/features/game/components/game-detail";
import { isGameId } from "@/features/game/services/game-api";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Game");
  // Kişiye göre değişebilen topluluk içeriği metadata'ya veya paylaşım önizlemesine taşınmaz.
  return { title: t("detail"), robots: { index: false, follow: true } };
}

export default async function GamePage({ params }: { params: Promise<{ gameId: string }> }) {
  const { gameId } = await params;
  if (!isGameId(gameId)) notFound();
  return <PageContainer><GameDetail gameId={gameId} /></PageContainer>;
}
