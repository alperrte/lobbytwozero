import { getTranslations } from "next-intl/server";

export default async function Loading() {
  const t = await getTranslations("Loading");

  return (
    <main
      role="status"
      className="flex min-h-dvh items-center justify-center px-6 text-text-secondary"
    >
      {t("label")}
    </main>
  );
}
