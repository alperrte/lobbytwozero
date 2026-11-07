import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ProfileView } from "@/features/user";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Profile");
  return { title: t("title"), robots: { index: false, follow: false } };
}

/** Kendi profilin (giriş gerekir; oturum kontrolü istemcide, yetki kararı User servisinde). */
export default function ProfilePage() {
  return <ProfileView />;
}
