import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ProfileEditForm } from "@/features/user";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Profile");
  return { title: t("editTitle"), robots: { index: false, follow: false } };
}

export default function ProfileEditPage() {
  return <ProfileEditForm />;
}
