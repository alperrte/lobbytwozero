import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AuthBackdrop } from "@/features/auth";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı.
export const metadata: Metadata = {
  robots: { index: false, follow: false }
};

/**
 * `(auth)` grubunun ortak katmanı. Sol paneldeki arka plan videosu burada durur: sayfalar arası geçişte bu layout yeniden
 * kurulmaz, bu yüzden video başa sarmadan akmaya devam eder.
 */
export default function PrivateGroupLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <AuthBackdrop />
      {children}
    </>
  );
}
