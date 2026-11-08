import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthCallback } from "@/features/auth";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("callback.metaTitle") };
}

/**
 * Google/Steam dönüş sayfası. Auth'ın yönlendirmesi `…/auth/callback#status=…` adresine gelir (sonuç URL parçasındadır);
 * dil önekli adrese yönlendirme sırasında parça tarayıcıda korunur.
 */
export default async function AuthCallbackPage() {
  const t = await getTranslations("Auth");

  return (
    <>
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("callback.title")}</h1>
      <div className="mt-6">
        <AuthCallback />
      </div>
    </>
  );
}
