import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, VerifyEmailForm } from "@/features/auth";

/** Sekme başlığı kök şablondan ("LTZ | %s") gelir; `(auth)` grubu zaten `noindex` yayınlar. */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("verify.metaTitle") };
}

/** E-posta doğrulama sayfası: yalnız çerçeve + form birleştirir; iş mantığı `features/auth` içindedir. */
export default async function VerifyEmailPage() {
  const t = await getTranslations("Auth");

  return (
    <AuthShell variant="register">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("verify.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("verify.description")}</p>
      <VerifyEmailForm />
    </AuthShell>
  );
}
