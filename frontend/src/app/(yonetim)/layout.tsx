import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Chakra_Petch, IBM_Plex_Sans } from "next/font/google";
import "../globals.css";

// Site ile aynı yazı tipleri; panel yalnız koyu temada çalışır (tema seçimi yok).
const displayFont = Chakra_Petch({ subsets: ["latin", "latin-ext"], weight: ["500", "600", "700"], variable: "--font-display-face", display: "swap" });
const bodyFont = IBM_Plex_Sans({ subsets: ["latin", "latin-ext"], weight: ["400", "500", "600"], variable: "--font-body-face", display: "swap" });

// Dil önekli site kökünden ayrı kök: yalnız Türkçe, dahili araç. Arama motorlarına kapalıdır.
export const metadata: Metadata = {
  title: "LTZ Yönetim",
  robots: { index: false, follow: false, nocache: true }
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, colorScheme: "dark", themeColor: "#0b0b0f" };

export default function AdminRootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="tr" data-theme="dark" className={`${displayFont.variable} ${bodyFont.variable}`}>
      <body className="bg-background font-sans text-text-primary antialiased">{children}</body>
    </html>
  );
}
