"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import type { IconName } from "@/shared/components/ui/icons";
import { useSocialCounts } from "../hooks/use-social-data";
import { SocialAnnouncerProvider } from "./social-announcer";
import { SocialHero } from "./social-hero";
import { SocialTabs, type SocialSection } from "./social-tabs";

type SocialPageProps = {
  section: SocialSection;
  icon: IconName;
  title: string;
  subtitle: string;
  figures?: 1 | 2;
  heroAction?: ReactNode;
  /** Örnek veri uyarısı gösterilsin mi (`SOCIAL_PREVIEW_DATA`). */
  preview: boolean;
  /** Sağ sütun (masaüstü) / ana içeriğin altı (mobil). */
  aside: ReactNode;
  asideLabel: string;
  children: ReactNode;
};

/**
 * Dört sosyal sayfanın ortak iskeleti: başlık kartı (tam genişlik), altında iki sütun.
 * Sol: sekmeler + sayfa içeriği; sağ: yardımcı paneller. `lg` altında sağ sütun içeriğin altına iner (yatay taşma yok).
 */
export function SocialPage({
  section,
  icon,
  title,
  subtitle,
  figures,
  heroAction,
  preview,
  aside,
  asideLabel,
  children
}: SocialPageProps) {
  const t = useTranslations("Social.preview");
  const counts = useSocialCounts();
  return (
    <SocialAnnouncerProvider>
      <div className="space-y-5">
        <SocialHero
          icon={icon}
          title={title}
          subtitle={subtitle}
          figures={figures}
          action={heroAction}
          preview={preview ? { badge: t("badge"), description: t("description") } : undefined}
        />
        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] xl:grid-cols-[minmax(0,1fr)_23rem]">
          <div className="min-w-0 space-y-4">
            <SocialTabs active={section} counts={counts.data} />
            {children}
          </div>
          <aside aria-label={asideLabel} className="min-w-0 space-y-5">
            {aside}
          </aside>
        </div>
      </div>
    </SocialAnnouncerProvider>
  );
}
