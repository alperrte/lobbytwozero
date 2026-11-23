import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { ListingsView } from "@/features/lfp";

export async function generateMetadata() {
  const t = await getTranslations("Lfp.meta");
  return { title: t("active"), robots: { index: false, follow: false } };
}

/** Süzgeçler adres çubuğundan (`useSearchParams`) okunur; Suspense sınırı gerekir. */
export default function FindTeamPage() {
  return (
    <Suspense>
      <ListingsView />
    </Suspense>
  );
}
