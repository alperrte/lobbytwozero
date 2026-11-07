import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, RegisterForm } from "@/features/auth";
import { Link } from "@/shared/lib/i18n/navigation";

/** Sekme başlığı kök şablondan ("LTZ | %s") gelir; `(auth)` grubu zaten `noindex` yayınlar. */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("register.metaTitle") };
}

/** Kayıt sayfası: yalnız çerçeve + form birleştirir; iş mantığı `features/auth` içindedir. */
export default async function RegisterPage() {
  const t = await getTranslations("Auth");

  return (
    <AuthShell variant="register">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("register.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("register.description")}</p>
      <RegisterForm />
      <p className="mt-6 text-center text-sm text-text-secondary">
        {t("register.haveAccount")}{" "}
        <Link href="/login" className="font-medium text-brand-text underline underline-offset-2">
          {t("register.toLogin")}
        </Link>
      </p>
    </AuthShell>
  );
}
