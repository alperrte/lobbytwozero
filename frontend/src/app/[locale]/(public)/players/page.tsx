import type { Metadata } from "next";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { PlayerSearch } from "@/features/user";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Players.search");
  return { title: t("title"), robots: { index: false, follow: false } };
}

export default function PlayersPage() {
  // `useSearchParams` kullanan istemci bileşeni Suspense sınırı ister.
  return (
    <Suspense fallback={null}>
      <PlayerSearch />
    </Suspense>
  );
}
