import { ProfilesScreen } from "@/features/hardware/components/profiles-screen";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("computers");
export default function ComputersPage() {
  return <ProfilesScreen />;
}
