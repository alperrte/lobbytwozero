import { getTranslations } from "next-intl/server";

/**
 * Yalnız `(public)` grubunda: kabuğun (navbar, sidebar, footer) içinde yükleme durumu gösterir.
 * Kök seviyesine konmaz; aksi halde tanımsız adresler akış başladıktan sonra 404 verdiği için
 * HTTP durumu 200 kalır (soft 404).
 */
export default async function Loading() {
  const t = await getTranslations("Loading");

  return (
    <div
      role="status"
      className="flex min-h-[50dvh] items-center justify-center px-6 text-text-secondary"
    >
      {t("label")}
    </div>
  );
}
