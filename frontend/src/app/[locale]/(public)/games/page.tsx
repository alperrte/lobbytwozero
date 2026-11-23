import { Suspense } from "react";
import { GameCatalogue } from "@/features/game/components/game-catalogue";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("games");

export default function GamesPage() {
  return <Suspense><GameCatalogue /></Suspense>;
}
