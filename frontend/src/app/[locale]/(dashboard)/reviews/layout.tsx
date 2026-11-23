import type { Metadata } from "next";
import type { ReactNode } from "react";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı. İnceleme metni, sahiplik ve puan paylaşım önizlemesine girmez.
export const metadata: Metadata = { robots: { index: false, follow: false } };

/** Kabuk `(dashboard)` grubundan gelir; bu bölüm yalnız metadata ekler. */
export default function ReviewsLayout({ children }: Readonly<{ children: ReactNode }>) {
  return children;
}
