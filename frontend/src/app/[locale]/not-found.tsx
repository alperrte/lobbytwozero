import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Link } from "@/shared/lib/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("NotFound");
  return { title: t("title"), robots: { index: false, follow: false } };
}

export default async function NotFound() {
  const t = await getTranslations("NotFound");

  return (
    <main className="mx-auto flex min-h-dvh max-w-xl flex-col items-start justify-center gap-4 px-6">
      <p className="font-display text-sm font-semibold text-brand-text">404</p>
      <h1 className="font-display text-3xl font-bold text-text-primary">
        {t("title")}
      </h1>
      <p className="text-text-secondary">{t("description")}</p>
      <Link
        href="/"
        className="inline-flex h-11 items-center rounded-control bg-primary px-5 font-display text-sm font-semibold text-primary-foreground hover:bg-secondary"
      >
        {t("backHome")}
      </Link>
    </main>
  );
}
