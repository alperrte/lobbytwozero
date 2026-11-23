import type { Metadata } from "next";
import type { ReactNode } from "react";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Library");
  return { title: t("titles.all"), robots: { index: false, follow: false } };
}

/** Kabuk `(dashboard)` grubundan gelir; bu bölüm yalnız metadata ekler. */
export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
