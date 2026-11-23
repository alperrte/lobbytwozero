"use client";

import type { ReactNode } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { SocialHero } from "@/features/social";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";
import type { Listing, ListingStatus, Platform } from "../types/lfp.types";

/** Takım Bul sayfaları yalnız oturum açmış kullanıcıya çizilir (misafir ilan göremez; ürün kararı 2026-11-13). */
export function LfpSessionGate({ children }: Readonly<{ children: (meId: string) => ReactNode }>) {
  const session = useRequireSession();
  const t = useTranslations("Lfp.states");
  if (session.status === "authenticated" && session.me) return children(session.me.accountId);
  return (
    <div role="status" aria-live="polite" className="space-y-5">
      <span className="sr-only">{t("loading")}</span>
      <div aria-hidden="true" className="h-40 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
      <div aria-hidden="true" className="h-96 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
    </div>
  );
}

type Section = "active" | "mine";

const tabs: ReadonlyArray<{ id: Section; href: AppPathname; icon: IconName }> = [
  { id: "active", href: "/find-team", icon: "usersRound" },
  { id: "mine", href: "/find-team/mine", icon: "fileText" }
];

/** Takım Bul iskeleti: sosyal sayfalarla aynı başlık kartı, "İlan Oluştur" eylemi ve iki bölüm sekmesi. */
export function LfpShell({
  section,
  title,
  subtitle,
  aside,
  children
}: Readonly<{ section: Section; title: string; subtitle: string; aside?: ReactNode; children: ReactNode }>) {
  const t = useTranslations("Lfp");
  return (
    <div className="space-y-5">
      <SocialHero
        icon="usersRound"
        title={title}
        subtitle={subtitle}
        action={
          <Link href="/find-team/new" className={buttonClass("primary", "md", "h-11 px-5")}>
            <Icon name="userPlus" className="size-4" />
            {t("actions.create")}
          </Link>
        }
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label={t("tabs.label")}>
          <ul className="flex gap-2">
            {tabs.map((tab) => {
              const current = tab.id === section;
              return (
                <li key={tab.id}>
                  <Link
                    href={tab.href}
                    aria-current={current ? "page" : undefined}
                    className={cn(
                      "flex h-11 items-center gap-2 rounded-control border px-4 text-sm font-semibold transition-colors duration-150 motion-reduce:transition-none",
                      current
                        ? "border-primary bg-primary/12 text-text-primary shadow-[inset_0_-2px_0_var(--ltz-primary)]"
                        : "border-border bg-surface text-text-secondary hover:border-control-border hover:text-text-primary"
                    )}
                  >
                    <Icon name={tab.icon} className={cn("size-[1.15rem]", current && "text-brand-text")} />
                    {t(`tabs.${tab.id}`)}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        {aside}
      </div>
      {children}
    </div>
  );
}

export function usePlatformLabel() {
  const t = useTranslations("Lfp.platform");
  return (platform: Platform) => t(platform);
}

/** Durum rozeti: renk + nokta + metin (yalnız renge dayanmaz). */
export function StatusBadge({ status, className }: Readonly<{ status: ListingStatus; className?: string }>) {
  const t = useTranslations("Lfp.status");
  const tone = status === "OPEN" ? "bg-success" : status === "EXPIRED" ? "bg-warning" : "bg-text-secondary";
  const text = status === "OPEN" ? "text-success" : status === "EXPIRED" ? "text-warning" : "text-text-secondary";
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-sm font-semibold", text, className)}>
      <span aria-hidden="true" className={cn("size-2 rounded-full", tone)} />
      {t(status)}
    </span>
  );
}

/** Tarih/saat biçimleri (kullanıcının yerel saat diliminde; backend UTC saklar — web-rules §7.3). */
export function useListingTime() {
  const format = useFormatter();
  const t = useTranslations("Lfp.card");
  const short = (iso: string) => format.dateTime(new Date(iso), { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
  return {
    short,
    playTime: (listing: Pick<Listing, "playTimeStart" | "playTimeEnd">) => {
      if (!listing.playTimeStart) return t("anyTime");
      const start = short(listing.playTimeStart);
      return listing.playTimeEnd
        ? `${start} – ${format.dateTime(new Date(listing.playTimeEnd), { hour: "2-digit", minute: "2-digit" })}`
        : start;
    }
  };
}

/** Kart altındaki tek satırlık bilgi (ikon + metin). */
export function Meta({ icon, children, className }: Readonly<{ icon: IconName; children: ReactNode; className?: string }>) {
  return (
    <span className={cn("inline-flex min-w-0 items-center gap-1.5 text-xs text-text-secondary", className)}>
      <Icon name={icon} className="size-4 shrink-0" />
      <span className="truncate">{children}</span>
    </span>
  );
}

/** Kart üstündeki küçük etiket (platform, seviye, rol). */
export function Chip({ icon, children, muted = false }: Readonly<{ icon?: IconName; children: ReactNode; muted?: boolean }>) {
  return (
    <span
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 rounded-control border border-border bg-surface-elevated px-2 py-1 text-xs font-medium",
        muted ? "text-text-secondary" : "text-text-primary"
      )}
    >
      {icon ? <Icon name={icon} className="size-3.5 shrink-0 text-text-secondary" /> : null}
      <span className="truncate">{children}</span>
    </span>
  );
}
