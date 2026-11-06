"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
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
import type { ShellUser } from "@/shared/hooks/use-shell-user";
import { cn } from "@/shared/utils/cn";

type ProfileMenuProps = {
  /** Oturum açmış kullanıcı; `null` misafir demektir (auth bağlanana kadar hep null). */
  user: ShellUser | null;
};

const items: ReadonlyArray<{ id: string; icon: IconName }> = [
  { id: "info", icon: "info" },
  { id: "profile", icon: "user" },
  { id: "account", icon: "settings" }
];

function Avatar({ user }: { user: ShellUser | null }) {
  return (
    <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-full border border-border bg-surface-elevated text-text-secondary">
      {user?.avatarUrl ? (
        // Gerçek avatar URL'si auth ile gelir; harici alan adı yapılandırması o zaman eklenir.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={user.avatarUrl} alt="" className="size-full object-cover" />
      ) : (
        <Icon name="user" className="size-[18px]" />
      )}
    </span>
  );
}

/**
 * Profil avatarı + ad + menü. Misafir durumunda ad "Misafir"dir; henüz var olmayan profil/hesap
 * sayfaları tıklanamaz ve "Yakında" olarak işaretlidir (sahte rota yok). Çıkış yalnız oturum varken görünür.
 */
export function ProfileMenu({ user }: ProfileMenuProps) {
  const t = useTranslations("Navbar.profile");
  const { open, setOpen, rootRef, triggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);
  const name = user?.displayName ?? t("guestName");

  useEffect(() => {
    if (open) {
      focusFirstMenuItem(menuRef.current);
    }
  }, [open]);

  return (
    <div ref={rootRef} className="sm:relative">
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
        className={cn(iconButtonClass, "w-auto gap-2 ps-1 pe-2")}
      >
        <Avatar user={user} />
        <span className="max-w-28 truncate font-display text-sm font-medium text-text-primary max-md:sr-only">
          {name}
        </span>
        <Icon
          name="chevron"
          className={cn(
            "size-4 transition-transform max-sm:hidden",
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
          className={cn(popoverPanelClass, "sm:w-72")}
        >
          <div className="flex items-center gap-3 px-3 py-2.5">
            <Avatar user={user} />
            <div className="min-w-0">
              <p className="truncate font-display text-sm font-semibold">{name}</p>
              <p className="text-xs text-text-secondary">
                {user ? t("signedIn") : t("guestState")}
              </p>
            </div>
          </div>
          <div role="separator" className="my-1 h-px bg-border" />

          {items.map(({ id, icon }) => (
            <button
              key={id}
              type="button"
              role="menuitem"
              aria-disabled="true"
              onClick={(event) => event.preventDefault()}
              className={menuItemClass}
            >
              <Icon name={icon} className="size-[18px] shrink-0" />
              <span className="flex-1">{t(`items.${id}`)}</span>
              <span className="rounded-control border border-border px-1.5 py-0.5 text-xs font-medium leading-none text-text-secondary">
                {t("soon")}
              </span>
            </button>
          ))}

          {user ? (
            <>
              <div role="separator" className="my-1 h-px bg-border" />
              <button
                type="button"
                role="menuitem"
                aria-disabled="true"
                onClick={(event) => event.preventDefault()}
                className={menuItemClass}
              >
                <Icon name="logout" className="size-[18px] shrink-0" />
                <span className="flex-1">{t("items.logout")}</span>
                <span className="rounded-control border border-border px-1.5 py-0.5 text-xs font-medium leading-none text-text-secondary">
                  {t("soon")}
                </span>
              </button>
            </>
          ) : (
            <p className="px-3 pb-2 pt-1 text-xs text-text-secondary">
              {t("guestNote")}
            </p>
          )}
        </div>
      ) : null}
    </div>
  );
}
