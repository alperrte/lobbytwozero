import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { Notice } from "@/shared/components/ui/notice";
import type { PageId } from "@/shared/config/pages";

/**
 * Yasal sayfa iskeleti. Hukuki metin uydurulmaz: belge yayınlanana kadar dürüstçe
 * "henüz yayınlanmadı" bilgisi gösterilir. Bu sayfalar `noindex` yayınlanır.
 */
export async function LegalPage({ id }: { id: Extract<PageId, "kvkk" | "privacy" | "terms" | "cookies"> }) {
  const pages = await getTranslations("Pages");
  const legal = await getTranslations("Legal");

  return (
    <div className="w-full">
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {pages(`${id}.title`)}
      </h1>
      <p className="mt-3 max-w-prose text-lg text-text-secondary">
        {pages(`${id}.description`)}
      </p>

      <div className="mt-8">
        <Notice title={legal("statusTitle")}>
          <p>{legal("statusBody")}</p>
        </Notice>
      </div>

      <Link
        href="/"
        className="mt-6 inline-block rounded-control font-display text-sm font-medium text-brand-text underline-offset-4 hover:underline"
      >
        {legal("backHome")}
      </Link>
    </div>
  );
}
