import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Chakra_Petch, IBM_Plex_Sans } from "next/font/google";
import { notFound } from "next/navigation";
import Script from "next/script";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { PlayerLookupProvider } from "@/features/user/components/player-lookup-provider";
import { routing } from "@/shared/lib/i18n/routing";
import { preferencesInitScript } from "@/shared/lib/preferences/init-script";
import {
  getSiteUrl,
  isIndexable,
  SITE_NAME,
  SITE_SHORT_NAME,
  THEME_COLORS
} from "@/shared/config/site";
import "../globals.css";

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

// Dil URL önekindedir (/tr, /en); hreflang ve canonical sayfa bazında buildPageMetadata ile yazılır.
const OG_LOCALES: Record<string, string> = { tr: "tr_TR", en: "en_US" };

type LocaleParams = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: LocaleParams): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Metadata" });
  const siteUrl = getSiteUrl();
  const description = t("description");

  return {
    ...(siteUrl ? { metadataBase: siteUrl } : {}),
    // Varsayılan sekme başlığı "LobbyTwoZero"; sayfalar "LTZ | Anasayfa" biçimini kullanır.
    title: { default: SITE_NAME, template: `${SITE_SHORT_NAME} | %s` },
    description,
    applicationName: SITE_NAME,
    openGraph: {
      type: "website",
      siteName: SITE_NAME,
      title: SITE_NAME,
      description,
      locale: OG_LOCALES[locale] ?? OG_LOCALES.tr,
      alternateLocale: Object.entries(OG_LOCALES)
        .filter(([code]) => code !== locale)
        .map(([, value]) => value)
    },
    // Görsel hazır olana kadar görselsiz özet kartı; sahte önizleme görseli üretilmez.
    twitter: { card: "summary", title: SITE_NAME, description },
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
  children,
  params
}: Readonly<{ children: ReactNode } & LocaleParams>) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }
  setRequestLocale(locale);
  const messages = await getMessages();

  return (
    // data-theme / data-theme-pref / data-sidebar, ilk boyamadan önce init betiği tarafından
    // yazılır; sunucu çıktısından farklı olacağı için suppressHydrationWarning gerekir.
    <html
      lang={locale}
      className={`${displayFont.variable} ${bodyFont.variable}`}
      suppressHydrationWarning
    >
      <body className="font-sans">
        {/* next/script: React 19'un "script etiketi render edildi" konsol hatasını önler; boyamadan önce çalışır. */}
        <Script id="preferences-init" strategy="beforeInteractive" dangerouslySetInnerHTML={{ __html: preferencesInitScript }} />
        <NextIntlClientProvider messages={messages}>
          <PlayerLookupProvider>{children}</PlayerLookupProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
