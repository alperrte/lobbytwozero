import { getTranslations } from "next-intl/server";
import { ProductDetailScreen } from "@/features/hardware/components/product-detail-screen";
import { PageContainer } from "@/shared/components/layout/page-container";

export async function generateMetadata() {
  const t = await getTranslations("Hardware.detail");
  return { title: t("title"), robots: { index: false, follow: false } };
}
export default async function ProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const { productId } = await params;
  return <PageContainer><ProductDetailScreen productId={productId} /></PageContainer>;
}
