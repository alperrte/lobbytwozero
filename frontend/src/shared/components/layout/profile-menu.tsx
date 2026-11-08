"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import {
  dangerMenuItemClass,
  iconButtonClass,
  menuItemClass,
  popoverPanelClass
} from "@/shared/components/ui/styles";
import {
  focusFirstMenuItem,
  onMenuKeyDown,
  usePopover
} from "@/shared/hooks/use-popover";
import { useShellSession, type ShellUser } from "@/shared/hooks/use-shell-user";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";

type ProfileMenuProps = {
  /** Oturum açmış kullanıcı; `null` misafir demektir. */
  user: ShellUser | null;
};

const items: ReadonlyArray<{ id: string; icon: IconName }> = [
  { id: "info", icon: "info" },
  { id: "profile", icon: "user" },
  { id: "account", icon: "settings" }
];

/** Ad ve soyaddan en çok iki baş harf ("Ada Lovelace" → "AL"). */
function getInitials(displayName: string): string {
  const parts = displayName.trim().split(/\s+/).filter(Boolean);
  const letters = [parts[0], parts.length > 1 ? parts[parts.length - 1] : undefined]
    .map((part) => (part ? Array.from(part)[0] : ""))
    .join("");
  return letters.toLocaleUpperCase();
}

/**
 * Yuvarlak avatar. Öncelik sırası: kullanıcının yüklediği fotoğraf → ad baş harfleri (giriş
 * yapmış, fotoğrafsız kullanıcı) → kullanıcı simgesi (misafir). Çerçeve nötrdür.
 */
function Avatar({
  user,
  className
}: {
  user: ShellUser | null;
  className?: string;
}) {
  const initials = user && !user.hideInitials ? getInitials(user.displayName) : "";

  return (
    <span
      className={cn(
        "flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface-elevated text-text-secondary ring-1 ring-inset ring-border",
        className
      )}
    >
      {user?.avatarUrl ? (
        // Gerçek avatar URL'si auth ile gelir; harici alan adı yapılandırması o zaman eklenir.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={user.avatarUrl}
          alt=""
          width={44}
          height={44}
          referrerPolicy="no-referrer"
          className="size-full object-cover"
        />
      ) : initials ? (
        <span
          aria-hidden="true"
          className="font-display text-sm font-semibold text-text-primary"
        >
          {initials}
        </span>
      ) : (
        <Icon name="user" className="size-5" />
      )}
    </span>
  );
}

/**
 * Profil avatarı + ad + menü. Misafir durumunda ad "Misafir"dir; henüz var olmayan profil/hesap
 * sayfaları tıklanamaz ve "Yakında" olarak işaretlidir (sahte rota yok). Oturum katmanı bağlıysa (`useShellSession().logout`):
 * misafire "Giriş yap" bağlantısı, giriş yapmış kullanıcıya çalışan "Çıkış yap" eylemi gösterilir. Bağlı değilse çıkış satırı eski,
 * eylemsiz ("Yakında") halini korur.
 */
export function ProfileMenu({ user }: ProfileMenuProps) {
  const t = useTranslations("Navbar.profile");
  const { open, setOpen, rootRef, triggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);
  const name = user?.displayName ?? t("guestName");
  const { logout, loading } = useShellSession();
  const sessionAware = logout !== undefined;
  const [signingOut, setSigningOut] = useState(false);

  async function onLogout() {
    if (!logout || signingOut) {
      return;
    }
    setSigningOut(true);
    try {
      await logout();
    } finally {
      setSigningOut(false);
      setOpen(false);
    }
  }

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
        className={cn(
          iconButtonClass,
          "h-10 w-10 p-0.5 sm:h-12 sm:w-auto sm:gap-2.5 sm:ps-1.5 sm:pe-3"
        )}
      >
        <Avatar user={user} className="size-9" />
        <span className="max-w-36 truncate font-display text-sm font-semibold text-text-primary max-sm:sr-only">
          {name}
        </span>
        <Icon
          name="chevron"
          className={cn(
            "size-3.5 transition-transform duration-150 max-sm:hidden",
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
            <Avatar user={user} className="size-11 text-base" />
            <div className="min-w-0">
              <p className="truncate font-display text-sm font-semibold">{name}</p>
              <p className="text-xs text-text-secondary">
                {user ? t("signedIn") : t("guestState")}
              </p>
            </div>
          </div>
          <div role="separator" className="my-1 h-px bg-border" />

          <Link href="/profile/computers" role="menuitem" className={menuItemClass} onClick={() => setOpen(false)}>
            <Icon name="desktop" className="size-[18px] shrink-0" />
            <span className="flex-1">{t("items.computers")}</span>
          </Link>

          {items.map(({ id, icon }) =>
            id === "profile" && user ? (
              <Link
                key={id}
                href="/profile"
                role="menuitem"
                onClick={() => setOpen(false)}
                className={menuItemClass}
              >
                <Icon name={icon} className="size-[18px] shrink-0" />
                <span className="flex-1">{t(`items.${id}`)}</span>
              </Link>
            ) : (
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
            )
          )}

          <div role="separator" className="my-1 h-px bg-border" />
          {sessionAware && user ? (
            <button
              type="button"
              role="menuitem"
              aria-disabled={signingOut}
              onClick={onLogout}
              className={dangerMenuItemClass}
            >
              <Icon name="logout" className="size-[18px] shrink-0" />
              <span className="flex-1">{t("items.logout")}</span>
            </button>
          ) : sessionAware ? (
            loading ? null : (
              <Link
                href="/login"
                role="menuitem"
                onClick={() => setOpen(false)}
                className={menuItemClass}
              >
                <Icon name="user" className="size-[18px] shrink-0" />
                <span className="flex-1">{t("signIn")}</span>
              </Link>
            )
          ) : (
            <>
              <button
                type="button"
                role="menuitem"
                aria-disabled="true"
                onClick={(event) => event.preventDefault()}
                className={dangerMenuItemClass}
              >
                <Icon name="logout" className="size-[18px] shrink-0" />
                <span className="flex-1">{t("items.logout")}</span>
                <span className="rounded-control border border-primary/40 px-1.5 py-0.5 text-xs font-medium leading-none">
                  {t("soon")}
                </span>
              </button>
              {user ? null : (
                <p className="px-3 pb-2 pt-2 text-xs text-text-secondary">
                  {t("guestNote")}
                </p>
              )}
            </>
          )}
        </div>
      ) : null}
    </div>
  );
}
