"use client";

import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";
import type { SocialCounts } from "../types/social.types";

export type SocialSection = "friends" | "incoming" | "sent" | "blocked";

const tabs: ReadonlyArray<{ id: SocialSection; href: AppPathname; icon: IconName; count: keyof SocialCounts }> = [
  { id: "friends", href: "/friends", icon: "users", count: "friends" },
  { id: "incoming", href: "/friends/requests", icon: "userPlus", count: "incoming" },
  { id: "sent", href: "/friends/sent", icon: "send", count: "outgoing" },
  { id: "blocked", href: "/friends/blocked", icon: "ban", count: "blocked" }
];

/**
 * Dört bölüm arasında gezinme. Her sekme gerçek bir adrestir (`/tr/arkadaslar/istekler` gibi); geri/ileri ve derin bağlantı
 * çalışır. Etkin sekme `aria-current="page"` taşır. Sayılar yüklenene kadar rozet gösterilmez (sahte sıfır yok); 50'yi
 * aşan liste "50+" gösterilir (serviste sayım ucu yoktur).
 * Bekleyen gelen istek varsa rozeti kırmızıdır.
 * Yerleşim kapsayıcı genişliğine göredir: 2×2 ızgara, geniş alanda tek satır dört sütun. Yatay kaydırma yoktur, hiçbir sekme
 * görünmez kalmaz. Çok dar alanda ikon gizlenir; etiket yine sığmazsa kısaltılır (erişilebilir ad tam metindir).
 */
export function SocialTabs({ active, counts }: Readonly<{ active: SocialSection; counts?: SocialCounts }>) {
  const t = useTranslations("Social.tabs");
  return (
    <nav aria-label={t("label")} className="@container">
      <ul className="grid grid-cols-2 gap-2 @min-[48rem]:grid-cols-4">
        {tabs.map((tab) => {
          const current = tab.id === active;
          const count = counts?.[tab.count];
          const alert = tab.id === "incoming" && (count?.count ?? 0) > 0;
          return (
            <li key={tab.id} className="min-w-0">
              <Link
                href={tab.href}
                aria-current={current ? "page" : undefined}
                className={cn(
                  "flex h-12 min-w-0 items-center justify-center gap-2 rounded-control border px-2.5 text-sm font-semibold whitespace-nowrap transition-colors duration-150 motion-reduce:transition-none @min-[26rem]:px-3.5",
                  current
                    ? "border-primary bg-primary/12 text-text-primary shadow-[inset_0_-2px_0_var(--ltz-primary)]"
                    : "border-border bg-surface text-text-secondary hover:border-control-border hover:text-text-primary"
                )}
              >
                <Icon
                  name={tab.icon}
                  className={cn("size-[1.15rem] shrink-0 @max-[26rem]:hidden", current && "text-brand-text")}
                />
                <span className="min-w-0 truncate">{t(tab.id)}</span>
                {count !== undefined ? (
                  <span
                    className={cn(
                      "inline-flex h-6 min-w-6 shrink-0 items-center justify-center rounded-full px-1.5 text-xs font-bold tabular-nums",
                      alert ? "bg-primary text-primary-foreground" : "bg-surface-elevated text-text-primary"
                    )}
                  >
                    {count.count}
                    {count.more ? "+" : ""}
                  </span>
                ) : null}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
