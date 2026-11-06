"use client";

import { useEffect, useRef, useTransition } from "react";
import { useRouter } from "next/navigation";
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
import { setLocale } from "@/shared/lib/i18n/actions";
import { locales, type Locale } from "@/shared/lib/i18n/config";
import { cn } from "@/shared/utils/cn";

/** Dil adları her zaman kendi dilinde yazılır (Türkçe / English). */
const nativeNames: Record<Locale, string> = { tr: "Türkçe", en: "English" };

/**
 * TR/EN açılır menüsü. Kapalıyken bayrak + kod + ok; açıkken "Türkçe" ve "English" seçenekleri.
 * Dil next-intl çerezinde saklanır, rota değişmez (router.refresh).
 */
export function LanguageMenu() {
  const t = useTranslations("Locale");
  const router = useRouter();
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
    startTransition(async () => {
      await setLocale(locale);
      router.refresh();
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
