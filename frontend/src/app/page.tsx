import { getTranslations } from "next-intl/server";
import { LanguageSwitcher } from "@/shared/components/ui/language-switcher";

export default async function Home() {
  const t = await getTranslations("Home");

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-white p-8 text-slate-900">
      <h1 className="text-4xl font-bold">{t("title")}</h1>
      <p className="text-center">{t("description")}</p>
      <LanguageSwitcher />
    </main>
  );
}
