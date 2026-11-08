import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { LoginForm } from "@/features/auth";

/** Sekme başlığı kök şablondan ("LTZ | %s") gelir; `(auth)` grubu zaten `noindex` yayınlar. */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("login.metaTitle") };
}

type LoginPageProps = {
  searchParams: Promise<{ next?: string | string[]; verified?: string | string[]; reset?: string | string[] }>;
};

/** Giriş sayfası: yalnız kart içeriğini (başlık + form) üretir; çerçeve `(auth)/layout.tsx` sahnesindedir; iş mantığı `features/auth` içindedir. */
export default async function LoginPage({ searchParams }: LoginPageProps) {
  const t = await getTranslations("Auth");
  const { next, verified, reset } = await searchParams;

  return (
    <>
      <h1 className="mb-6 font-display text-3xl font-bold text-text-primary">{t("login.title")}</h1>
      <LoginForm next={typeof next === "string" ? next : null} verified={verified === "1"} passwordReset={reset === "1"} />
    </>
  );
}
