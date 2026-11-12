import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PlayerProfile } from "@/features/user";

/** Başlık kişiye özel veri içermez; oyuncu profili arama motorlarına açılmaz. */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Players.profile");
  return { title: t("metaTitle"), robots: { index: false, follow: false } };
}

export default async function PlayerPage({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return <PlayerProfile userKey={decodeURIComponent(userId)} />;
}
