import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, ResetPasswordForm } from "@/features/auth";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("reset.metaTitle") };
}

/**
 * Şifre sıfırlama sayfası. Auth'ın e-posta bağlantısı `…/auth/reset-password#token=…` adresine gider (token URL
 * parçasındadır); dil önekli adrese yönlendirme sırasında parça tarayıcıda korunur.
 */
export default async function ResetPasswordPage() {
  const t = await getTranslations("Auth");

  return (
    <AuthShell variant="login">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("reset.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("reset.description")}</p>
      <ResetPasswordForm />
    </AuthShell>
  );
}
