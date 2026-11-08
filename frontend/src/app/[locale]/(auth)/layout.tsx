import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AuthBackdrop, AuthStage } from "@/features/auth";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı.
export const metadata: Metadata = {
  robots: { index: false, follow: false }
};

/**
 * `(auth)` grubunun kalıcı katmanı: tam ekran arka plan videosu ve üstündeki sahne (navbar, karşılama yazısı, kart
 * yüzeyi, footer). Sayfalar arası geçişte bu layout yeniden kurulmaz; video başa sarmaz, navbar ve footer yeniden
 * kurulmaz. Sayfalar yalnız kartın İÇERİĞİNİ (başlık + form) üretir.
 */
export default function PrivateGroupLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <AuthBackdrop />
      <AuthStage>{children}</AuthStage>
    </>
  );
}
