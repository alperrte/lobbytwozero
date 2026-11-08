import { Suspense } from "react";
import { PageContainer } from "@/shared/components/layout/page-container";
import { GameCatalogue } from "@/features/game/components/game-catalogue";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("games");

export default function GamesPage() {
  return <PageContainer><Suspense><GameCatalogue /></Suspense></PageContainer>;
}
