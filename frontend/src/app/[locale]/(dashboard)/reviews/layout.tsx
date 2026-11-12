import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı. İnceleme metni, sahiplik ve puan paylaşım önizlemesine girmez.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function ReviewsLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <AppShell footer={<Footer />}>{children}</AppShell>;
}
