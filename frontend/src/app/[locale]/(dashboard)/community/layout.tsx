import type { ReactNode } from "react";
import { PublishOverlay, PublishToast } from "@/features/community";

/**
 * Community bölümü: kabuk (navbar + sidebar + ortak sayfa kabı + footer) `(dashboard)` grubundan gelir.
 * Paylaşım yükleme katmanı ve sağ alt başarı bildirimi burada tek örnek olarak durur: oluşturma sayfasından akışa geçişte kaybolmaz.
 */
export default function CommunityLayout({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <PublishOverlay />
      <PublishToast />
    </>
  );
}
