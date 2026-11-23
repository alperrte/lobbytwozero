import type { Metadata } from "next";
import type { ReactNode } from "react";
import { SessionProvider } from "@/features/auth";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı.
export const metadata: Metadata = {
  robots: { index: false, follow: false }
};

/**
 * Oturum katmanı ve ortak uygulama kabuğu bu grupta tek kez kurulur (`(public)` ile aynı). Bölüm layout'ları ve sayfalar
 * `AppShell` / `PageContainer` kurmaz; genişlik ve hiza kabuktan gelir (frontend/frontend-layout.md). Grup değişince
 * (ör. giriş sayfasından buraya) katman yeniden kurulup oturum tazelenir; grup içi gezinmede korunur.
 */
export default function PrivateGroupLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return (
    <SessionProvider>
      <AppShell footer={<Footer />}>{children}</AppShell>
    </SessionProvider>
  );
}
