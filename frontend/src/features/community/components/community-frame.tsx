"use client";

import { useEffect, useId, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { CommunityIcon } from "./community-icons";
import { CommunityHeading } from "./community-ui";

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
 * yerine geçmez; yalnız Community'nin kendi bölümleri arasında gezinir. Başlık, sekmeler, araç çubukları ve gönderi kartları
 * AYNI içerik containerında durur (mobilde %96, sm ve üstünde %93, ortalı): sol ve sağ kenarlar hepsinde aynı hizadadır.
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
    <div className="mx-auto w-[96%] pb-10 sm:w-[93%]">
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

/**
 * "Gönderi oluştur" ana eylemi: başlık satırının sağında 40 × 40 px, yalnız "+" ikonlu kompakt buton. Üzerine gelince/odaklanınca
 * "Gönderi oluştur" ipucu görünür (Escape kapatır); erişilebilir ad aynıdır. Tıklanınca mevcut oluşturma sayfası açılır.
 */
export function NewPostLink({ forumId }: { forumId?: string }) {
  const t = useTranslations("Community");
  const tipId = useId();
  const [tip, setTip] = useState(false);
  const label = forumId ? t("forum.newTopic") : t("nav.newPost");

  useEffect(() => {
    if (!tip) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setTip(false);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [tip]);

  return (
    <span className="relative ms-auto mt-4 inline-flex shrink-0">
      <Link
        href={forumId ? { pathname: "/community/new", query: { forum: forumId } } : "/community/new"}
        aria-label={label}
        aria-describedby={tip ? tipId : undefined}
        onMouseEnter={() => setTip(true)}
        onMouseLeave={() => setTip(false)}
        onFocus={() => setTip(true)}
        onBlur={() => setTip(false)}
        className="inline-flex size-10 items-center justify-center rounded-xl border border-primary bg-primary text-primary-foreground transition-colors hover:border-secondary hover:bg-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        <CommunityIcon name="plus" className="size-5" />
      </Link>
      {tip ? (
        <span
          id={tipId}
          role="tooltip"
          className="pointer-events-none absolute end-0 top-full z-40 mt-2 w-max max-w-[calc(100vw-2rem)] rounded-control border border-border bg-surface-elevated px-3 py-1.5 text-sm text-text-primary shadow-popover animate-pop-in"
        >
          {label}
        </span>
      ) : null}
    </span>
  );
}
