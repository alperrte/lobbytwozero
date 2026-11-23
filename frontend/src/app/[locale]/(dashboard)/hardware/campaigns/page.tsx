import { Suspense } from "react";
import { CampaignsScreen } from "@/features/hardware/components/campaigns-screen";
import { HardwareLoading } from "@/features/hardware/components/hardware-ui";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("campaigns");
export default function CampaignsPage() {
  return <Suspense fallback={<HardwareLoading />}><CampaignsScreen /></Suspense>;
}
