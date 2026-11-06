import type { ReactNode } from "react";
import { cn } from "@/shared/utils/cn";

type PageContainerProps = {
  children: ReactNode;
  className?: string;
};

/**
 * Public sayfalar (Ayarlar, SSS, Hakkımızda, İletişim) için ortak içerik kabı.
 *
 * AppShell'in `<main>` alanı sidebar'ın yanındaki tüm genişliği kaplar; bu kap içeriği o alanda
 * ortalar ve en çok 1320px ile sınırlar. Böylece geniş ekranda içerik sola yığılıp sağ taraf boş
 * kalmaz, dar ekranda ise tam genişliğe iner. `<main>` zaten 12px (mobil) / 20px (sm+) iç boşluk
 * verdiği için buradaki ek boşluk toplamda mobil 16px, tablet 24px, masaüstü 32px, geniş masaüstü 48px yapar.
 * AppShell, navbar, sidebar ve footer ölçüleri değişmez; sayfalar kendi genişliğini yazmaz.
 */
export function PageContainer({ children, className }: PageContainerProps) {
  return (
    <div
      data-page-container
      className={cn("mx-auto w-full max-w-[82.5rem] px-1 lg:px-3 xl:px-7", className)}
    >
      {children}
    </div>
  );
}
