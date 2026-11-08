import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { PostFormScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("new.title"), description: t("new.description"), robots: { index: false, follow: false } };
}

export default function NewPostPage() {
  return (
    <Suspense>
      <PostFormScreen />
    </Suspense>
  );
}
