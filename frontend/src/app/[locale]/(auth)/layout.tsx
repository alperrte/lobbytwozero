import type { Metadata } from "next";
import type { ReactNode } from "react";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı.
export const metadata: Metadata = {
  robots: { index: false, follow: false }
};

export default function PrivateGroupLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return children;
}
