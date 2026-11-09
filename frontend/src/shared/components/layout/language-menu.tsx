"use client";

import { useEffect, useRef, useTransition } from "react";
import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Flag } from "@/shared/components/ui/flags";
import { Icon } from "@/shared/components/ui/icons";
import {
  iconButtonClass,
  menuItemClass,
  popoverPanelClass
} from "@/shared/components/ui/styles";
import {
  focusFirstMenuItem,
  onMenuKeyDown,
  usePopover
} from "@/shared/hooks/use-popover";
import { locales, type Locale } from "@/shared/lib/i18n/config";
import { getPathname, usePathname } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";

/** Dinamik rotalar → dil değişiminde korunacak URL parametresi adı. */
const DYNAMIC_PARAMS: Readonly<Record<string, string>> = {
  "/hardware/[productId]": "productId",
  "/games/[gameId]": "gameId",
  "/library/users/[userId]": "userId",
  "/community/posts/[postId]": "postId",
  "/community/forums/[forumId]": "forumId",
  "/community/users/[userId]": "userId"
};

/** Dil adları her zaman kendi dilinde yazılır (Türkçe / English). */
const nativeNames: Record<Locale, string> = { tr: "Türkçe", en: "English" };

/**
 * TR/EN açılır menüsü. Kapalıyken bayrak + kod + ok; açıkken "Türkçe" ve "English" seçenekleri.
 * Dil URL önekindedir: seçim, açık sayfanın eşdeğerine geçer (ör. /tr/hakkimizda → /en/about).
 */
export function LanguageMenu() {
  const t = useTranslations("Locale");
  const pathname = usePathname();
  const params = useParams();
  const active = useLocale() as Locale;
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);
  const [pending, startTransition] = useTransition();

  useEffect(() => {
    if (open) {
      focusFirstMenuItem(menuRef.current);
    }
  }, [open]);

  function choose(locale: Locale) {
    close(true);
    if (locale === active) {
      return;
    }
    startTransition(() => {
      const search = new URLSearchParams(window.location.search);
      // Locale changes replace the root html layout. A document navigation runs
      // the existing pre-paint preference script and clears private client state.
      if (pathname in DYNAMIC_PARAMS) {
        const name = DYNAMIC_PARAMS[pathname];
        const value = params[name];
        if (typeof value !== "string" || !value.trim()) return;
        const href = { pathname, params: { [name]: value }, query: Object.fromEntries(search) } as unknown as Parameters<typeof getPathname>[0]["href"];
        window.location.assign(getPathname({ href, locale }) + window.location.hash);
      } else {
        // Statik rota birleşimi TS'in ayırıcı birleşim sınırını (25) aşar; yol zaten `AppPathname`'dir.
        const href = { pathname: pathname as AppPathname, query: Object.fromEntries(search) } as unknown as Parameters<typeof getPathname>[0]["href"];
        window.location.assign(getPathname({ href, locale }) + window.location.hash);
      }
    });
  }

  return (
    <div ref={rootRef} className="sm:relative">
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${t("label")}: ${nativeNames[active]}`}
        disabled={pending}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
        className={cn(
          iconButtonClass,
          "w-auto gap-1 px-2.5 font-display text-xs font-semibold tracking-wide text-text-primary disabled:cursor-wait sm:w-auto"
        )}
      >
        <span aria-hidden="true">{active.toUpperCase()}</span>
        <Icon
          name="chevron"
          className={cn(
            "size-3.5 text-text-secondary transition-transform duration-150",
            open && "rotate-180"
          )}
        />
      </button>

      {open ? (
        <div
          ref={menuRef}
          role="menu"
          aria-label={t("label")}
          onKeyDown={onMenuKeyDown}
          className={cn(popoverPanelClass, "sm:w-48")}
        >
          {locales.map((locale) => {
            const checked = locale === active;
            return (
              <button
                key={locale}
                type="button"
                role="menuitemradio"
                aria-checked={checked}
                lang={locale}
                onClick={() => choose(locale)}
                className={menuItemClass}
              >
                <Flag locale={locale} />
                <span className="flex-1">{nativeNames[locale]}</span>
                {checked ? (
                  <Icon name="check" className="size-4 text-brand-text" />
                ) : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
