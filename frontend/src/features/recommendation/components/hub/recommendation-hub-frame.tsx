"use client";

import { useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageHeader } from "@/shared/components/layout/page-header";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";
import { LtzGuideDrawer } from "../assistant/ltz-guide-drawer";
import { ConsentGate } from "./consent-gate";

export const hubSections = ["overview", "games", "hardware", "friends", "players", "events", "history"] as const;
export type HubSection = (typeof hubSections)[number];

const sectionMeta: Record<HubSection, { href: AppPathname; icon: IconName }> = {
  overview: { href: "/for-you", icon: "sparkles" },
  games: { href: "/for-you/games", icon: "gamepad" },
  hardware: { href: "/for-you/hardware", icon: "cpu" },
  friends: { href: "/for-you/friends", icon: "userPlus" },
  players: { href: "/for-you/players", icon: "users" },
  events: { href: "/for-you/events", icon: "calendar" },
  history: { href: "/for-you/history", icon: "clock" }
};

/**
 * "Sana Özel" bölümlerinin ortak çerçevesi (bileşen; layout değildir — sayfa kabı AppShell/PageContainer'dadır).
 * Başlık, bölümler arası alt menü, LTZ Rehber düğmesi ve rıza kapısı. İçerik yalnız rıza açıkken çizilir.
 */
export function RecommendationHubFrame({ section, children }: { section: HubSection; children: ReactNode }) {
  const t = useTranslations("Recommendation");
  useRequireSession();
  const [guideOpen, setGuideOpen] = useState(false);
  return <>
    <PageHeader title={t(`sections.${section}.title`)} description={t(`sections.${section}.description`)} />
    <ConsentGate header={(granted) => <div className="mb-8 flex flex-col gap-3 border-b border-border pb-3 lg:flex-row lg:items-center lg:justify-between">
      <nav aria-label={t("nav.label")} className="-mx-1 min-w-0 overflow-x-auto px-1 pb-1">
        <ul className="flex w-max gap-1">
          {hubSections.map((item) => {
            const current = item === section;
            return <li key={item}>
              <Link href={sectionMeta[item].href} aria-current={current ? "page" : undefined} className={cn(
                "relative inline-flex h-10 items-center gap-2 rounded-control px-3 text-sm font-semibold whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus motion-reduce:transition-none",
                current ? "bg-surface-elevated text-text-primary after:absolute after:inset-x-3 after:-bottom-1 after:h-0.5 after:bg-primary" : "text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
              )}>
                <Icon name={sectionMeta[item].icon} className="size-4 shrink-0" />
                {t(`nav.${item}`)}
              </Link>
            </li>;
          })}
        </ul>
      </nav>
      {granted ? <button type="button" className={buttonClass("secondary", "md", "self-start lg:self-auto")} aria-haspopup="dialog" onClick={() => setGuideOpen(true)}>
        <Icon name="message" className="size-4" />
        {t("guide.open")}
      </button> : null}
    </div>}>
      {children}
      {guideOpen ? <LtzGuideDrawer onClose={() => setGuideOpen(false)} /> : null}
    </ConsentGate>
  </>;
}
