import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Chakra_Petch, IBM_Plex_Sans } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages, getTranslations } from "next-intl/server";
import { preferencesInitScript } from "@/shared/lib/preferences/init-script";
import { getSiteUrl, isIndexable, SITE_NAME, THEME_COLORS } from "@/shared/config/site";
import "./globals.css";

const displayFont = Chakra_Petch({
  subsets: ["latin", "latin-ext"],
  weight: ["500", "600", "700"],
  variable: "--font-display-face",
  display: "swap"
});

const bodyFont = IBM_Plex_Sans({
  subsets: ["latin", "latin-ext"],
  weight: ["400", "500", "600"],
  variable: "--font-body-face",
  display: "swap"
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Metadata");
  const siteUrl = getSiteUrl();
  const description = t("description");

  return {
    ...(siteUrl ? { metadataBase: siteUrl } : {}),
    title: { default: SITE_NAME, template: `%s | ${SITE_NAME}` },
    description,
    applicationName: SITE_NAME,
    openGraph: {
      type: "website",
      siteName: SITE_NAME,
      title: SITE_NAME,
      description
    },
    // Üretim ve doğrulanmış site kökü yoksa hiçbir sayfa indekslenmez.
    robots: isIndexable(siteUrl)
      ? { index: true, follow: true }
      : { index: false, follow: false }
  };
}

export const viewport: Viewport = {
  colorScheme: "dark light",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: THEME_COLORS.dark },
    { media: "(prefers-color-scheme: light)", color: THEME_COLORS.light }
  ]
};

export default async function RootLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    // data-theme / data-theme-pref / data-sidebar, ilk boyamadan önce init betiği tarafından
    // yazılır; sunucu çıktısından farklı olacağı için suppressHydrationWarning gerekir.
    <html
      lang={locale}
      className={`${displayFont.variable} ${bodyFont.variable}`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: preferencesInitScript }} />
      </head>
      <body className="font-sans">
        <NextIntlClientProvider messages={messages}>
          {children}
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
