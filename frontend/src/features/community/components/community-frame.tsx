"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { CommunityIcon } from "./community-icons";
import { CommunityHeading, primaryButtonClass } from "./community-ui";

const TABS = [
  { id: "feed", href: "/community" },
  { id: "forums", href: "/community/forums" },
  { id: "mine", href: "/community/mine" }
] as const;

function activeTab(pathname: string): (typeof TABS)[number]["id"] | null {
  if (pathname.startsWith("/community/forums")) return "forums";
  if (pathname.startsWith("/community/mine")) return "mine";
  if (pathname === "/community" || pathname.startsWith("/community/posts") || pathname.startsWith("/community/new")) return "feed";
  return null;
}

/**
 * Community sayfalarının ortak üst alanı: başlık, ana eylem ve sayfa içi sekmeler. Ortak kabuğun (navbar/sidebar)
 * yerine geçmez; yalnız Community'nin kendi bölümleri arasında gezinir.
 */
export function CommunityFrame({
  title,
  description,
  action,
  hideNav = false,
  children
}: {
  title: string;
  description: string;
  action?: ReactNode;
  hideNav?: boolean;
  children: ReactNode;
}) {
  const t = useTranslations("Community");
  const pathname = usePathname();
  const current = activeTab(pathname);

  return (
    <div className="pb-10">
      <CommunityHeading title={title} description={description}>
        {action}
      </CommunityHeading>
      {hideNav ? null : (
        <nav aria-label={t("nav.label")} className="mb-6 border-b border-border">
          <ul className="-mb-px flex flex-wrap gap-1">
            {TABS.map((tab) => (
              <li key={tab.id}>
                <Link
                  href={tab.href}
                  aria-current={current === tab.id ? "page" : undefined}
                  className={cn(
                    "inline-flex min-h-11 items-center border-b-2 px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus",
                    current === tab.id
                      ? "border-primary text-text-primary"
                      : "border-transparent text-text-secondary hover:text-text-primary"
                  )}
                >
                  {t(`nav.${tab.id}`)}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
      {children}
    </div>
  );
}

/** "Gönderi oluştur" ana eylemi (başlık yanında). */
export function NewPostLink({ forumId }: { forumId?: string }) {
  const t = useTranslations("Community");
  return (
    <Link
      href={forumId ? { pathname: "/community/new", query: { forum: forumId } } : "/community/new"}
      className={primaryButtonClass}
    >
      <CommunityIcon name="pencil" className="size-4" />
      {forumId ? t("forum.newTopic") : t("nav.newPost")}
    </Link>
  );
}
