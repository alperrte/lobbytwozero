import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ForgotPasswordForm } from "@/features/auth";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("forgot.metaTitle") };
}

/** Şifremi unuttum sayfası: yalnız kart içeriğini (başlık + form) üretir; çerçeve `(auth)/layout.tsx` sahnesindedir; iş mantığı `features/auth` içindedir. */
export default async function ForgotPasswordPage() {
  const t = await getTranslations("Auth");

  return (
    <>
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("forgot.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("forgot.description")}</p>
      <ForgotPasswordForm />
    </>
  );
}
