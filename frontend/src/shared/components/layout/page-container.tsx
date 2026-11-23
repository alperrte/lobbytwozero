import type { ReactNode } from "react";
import { cn } from "@/shared/utils/cn";

type PageContainerProps = {
  children: ReactNode;
  className?: string;
};

/**
 * Tüm uygulama sayfalarının TEK ortak içerik kabı (Donanımlar kataloğu ölçüsü).
 *
 * Yalnız `AppShell` `<main>` içinde bir kez kullanır; sayfalar ve feature bileşenleri bunu import etmez, kendi kök
 * genişliğini (`mx-auto max-w-*`) yazmaz. İçerik sidebar'ın yanındaki alanı doldurur, en çok 100rem (1600px) olur ve
 * daha geniş ekranda ortalanır. Yan boşluğu yalnız `<main>` verir (mobil 12px, sm+ 20px); burada ek `px` yoktur.
 * Ayrıntı ve kurallar: `frontend/frontend-layout.md`.
 */
export function PageContainer({ children, className }: PageContainerProps) {
  return (
    <div data-page-container className={cn("mx-auto w-full max-w-[100rem]", className)}>
      {children}
    </div>
  );
}
