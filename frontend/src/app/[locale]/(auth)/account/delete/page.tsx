import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, DeleteAccountForm } from "@/features/auth";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("deleteAccount.metaTitle") };
}

/**
 * Hesap silme onay sayfası. Auth'ın e-posta bağlantısı `…/account/delete#token=…` adresine gider (token URL parçasındadır);
 * dil önekli adrese yönlendirme sırasında parça tarayıcıda korunur.
 */
export default async function DeleteAccountPage() {
  const t = await getTranslations("Auth");

  return (
    <AuthShell variant="login">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("deleteAccount.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("deleteAccount.description")}</p>
      <DeleteAccountForm />
    </AuthShell>
  );
}
