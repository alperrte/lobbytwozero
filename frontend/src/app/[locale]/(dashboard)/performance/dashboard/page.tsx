import { PerformanceDashboard } from "@/features/performance/components/performance-dashboard";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("performanceDashboard");
export default function PerformanceDashboardPage() {
  return <PerformanceDashboard />;
}
