import { getTranslations } from "next-intl/server";
import { MyListingsView } from "@/features/lfp";

export async function generateMetadata() {
  const t = await getTranslations("Lfp.meta");
  return { title: t("mine"), robots: { index: false, follow: false } };
}

export default function MyListingsPage() {
  return <MyListingsView />;
}
