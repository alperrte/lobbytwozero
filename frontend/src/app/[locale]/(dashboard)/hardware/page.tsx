import { Suspense } from "react";
import { CatalogScreen } from "@/features/hardware/components/catalog-screen";
import { HardwareLoading } from "@/features/hardware/components/hardware-ui";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("hardware");
export default function HardwarePage() {
  return <div className="mx-auto w-full max-w-[100rem]"><Suspense fallback={<HardwareLoading />}><CatalogScreen /></Suspense></div>;
}
