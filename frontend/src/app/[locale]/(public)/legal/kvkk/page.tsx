import { LegalPage } from "@/shared/components/layout/legal-page";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("kvkk");

export default function Page() {
  return <LegalPage id="kvkk" />;
}
