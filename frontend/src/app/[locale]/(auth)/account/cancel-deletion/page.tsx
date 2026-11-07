import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AuthShell, CancelDeletionForm } from "@/features/auth";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth");
  return { title: t("cancelDeletion.metaTitle") };
}

/** Silme talebinden vazgeçme sayfası. Giriş ekranı, silinme sürecindeki hesap için buraya bağlantı verir. */
export default async function CancelDeletionPage() {
  const t = await getTranslations("Auth");

  return (
    <AuthShell variant="login">
      <h1 className="font-display text-3xl font-bold text-text-primary">{t("cancelDeletion.title")}</h1>
      <p className="mt-2 mb-6 text-base text-text-secondary">{t("cancelDeletion.description")}</p>
      <CancelDeletionForm />
    </AuthShell>
  );
}
