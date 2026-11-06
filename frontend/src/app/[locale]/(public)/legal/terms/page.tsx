import { LegalPage } from "@/shared/components/layout/legal-page";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("terms");

export default function Page() {
  return <LegalPage id="terms" />;
}
