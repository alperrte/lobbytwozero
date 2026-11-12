import type { Metadata } from "next";
import type { ReactNode } from "react";
import { getTranslations } from "next-intl/server";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Library");
  return { title: t("titles.all"), robots: { index: false, follow: false } };
}
export default function Layout({ children }: { children: ReactNode }) {
  return <AppShell footer={<Footer />}>{children}</AppShell>;
}
