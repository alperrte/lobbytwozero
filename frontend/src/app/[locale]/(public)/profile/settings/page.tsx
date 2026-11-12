import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AccountSettings } from "@/features/user";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("AccountSettings");
  return { title: t("title"), robots: { index: false, follow: false } };
}

export default function AccountSettingsPage() {
  return <AccountSettings />;
}
