import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { EditListingView } from "@/features/lfp";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function generateMetadata() {
  const t = await getTranslations("Lfp.meta");
  return { title: t("edit"), robots: { index: false, follow: false } };
}

export default async function EditListingPage({ params }: Readonly<{ params: Promise<{ listingId: string }> }>) {
  const { listingId } = await params;
  if (!UUID.test(listingId)) notFound();
  return <EditListingView listingId={listingId} />;
}
