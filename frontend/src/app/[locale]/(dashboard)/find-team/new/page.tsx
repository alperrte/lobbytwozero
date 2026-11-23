import { getTranslations } from "next-intl/server";
import { CreateListingView } from "@/features/lfp";

export async function generateMetadata() {
  const t = await getTranslations("Lfp.meta");
  return { title: t("create"), robots: { index: false, follow: false } };
}

export default function CreateListingPage() {
  return <CreateListingView />;
}
