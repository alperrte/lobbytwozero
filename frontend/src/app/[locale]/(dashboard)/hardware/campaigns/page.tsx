import { Suspense } from "react";
import { CampaignsScreen } from "@/features/hardware/components/campaigns-screen";
import { HardwareLoading } from "@/features/hardware/components/hardware-ui";
import { PageContainer } from "@/shared/components/layout/page-container";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("campaigns");
export default function CampaignsPage() {
  return <PageContainer><Suspense fallback={<HardwareLoading />}><CampaignsScreen /></Suspense></PageContainer>;
}
