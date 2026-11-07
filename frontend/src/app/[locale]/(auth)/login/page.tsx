import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, LoginForm } from "@/features/auth";

/** Sekme başlığı kök şablondan ("LTZ | %s") gelir; `(auth)` grubu zaten `noindex` yayınlar. */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("login.metaTitle") };
}

type LoginPageProps = {
  searchParams: Promise<{ next?: string | string[]; verified?: string | string[] }>;
};

/** Giriş sayfası: yalnız çerçeve + form birleştirir; iş mantığı `features/auth` içindedir. */
export default async function LoginPage({ searchParams }: LoginPageProps) {
  const t = await getTranslations("Auth");
  const { next, verified } = await searchParams;

  return (
    <AuthShell variant="login">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("login.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("login.description")}</p>
      <LoginForm next={typeof next === "string" ? next : null} verified={verified === "1"} />
    </AuthShell>
  );
}
