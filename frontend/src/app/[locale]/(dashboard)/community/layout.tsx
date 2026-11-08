import type { ReactNode } from "react";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PublishOverlay, PublishToast } from "@/features/community";

/**
 * Community bölümü ortak kabuğu: tek AppShell (navbar + sidebar + footer); oturum katmanı `(dashboard)` grubundadır.
 * Paylaşım yükleme katmanı ve sağ alt başarı bildirimi burada tek örnek olarak durur: oluşturma sayfasından akışa geçişte kaybolmaz.
 */
export default function CommunityLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppShell footer={<Footer />}>
        <PageContainer>{children}</PageContainer>
      </AppShell>
      <PublishOverlay />
      <PublishToast />
    </>
  );
}
