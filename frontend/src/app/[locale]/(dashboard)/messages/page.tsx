import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { MessagingScreen } from "@/features/messaging";

export async function generateMetadata() {
  const t = await getTranslations("Messaging.meta");
  return { title: t("title"), robots: { index: false, follow: false } };
}

/** Açık sohbet adres çubuğundan (`?c=`) okunur; `useSearchParams` için Suspense sınırı gerekir. */
export default function MessagesPage() {
  return (
    <Suspense>
      <MessagingScreen />
    </Suspense>
  );
}
