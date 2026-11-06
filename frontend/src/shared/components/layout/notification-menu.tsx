"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import {
  iconButtonClass,
  popoverPanelClass
} from "@/shared/components/ui/styles";
import { usePopover } from "@/shared/hooks/use-popover";
import type { ShellNotification } from "@/shared/hooks/use-shell-notifications";
import { cn } from "@/shared/utils/cn";

type NotificationMenuProps = {
  /** Gerçek bildirimler. notification-service bağlanana kadar boş gelir; sahte kayıt gösterilmez. */
  notifications: readonly ShellNotification[];
};

/**
 * Bildirim zili ve paneli. Boş durum dürüsttür: sayaç ve liste yalnız gerçek veri varsa çizilir.
 */
export function NotificationMenu({ notifications }: NotificationMenuProps) {
  const t = useTranslations("Navbar.notifications");
  const panelId = useId();
  const { open, setOpen, rootRef, triggerRef } = usePopover();
  const unread = notifications.filter((item) => !item.read).length;

  return (
    <div ref={rootRef} className="sm:relative">
      <button
        ref={triggerRef}
        type="button"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={unread > 0 ? t("labelWithCount", { count: unread }) : t("label")}
        onClick={() => setOpen(!open)}
        className={cn(iconButtonClass, "relative")}
      >
        <Icon name="bell" />
        {unread > 0 ? (
          <span
            aria-hidden="true"
            className="absolute end-1.5 top-1.5 min-w-4 rounded-full bg-primary px-1 text-center font-display text-[0.625rem] font-semibold leading-4 text-primary-foreground"
          >
            {unread > 9 ? "9+" : unread}
          </span>
        ) : null}
      </button>

      {open ? (
        <section
          id={panelId}
          aria-label={t("title")}
          className={cn(popoverPanelClass, "p-0 sm:w-80")}
        >
          <h2 className="border-b border-border px-4 py-3 font-display text-sm font-semibold">
            {t("title")}
          </h2>
          {notifications.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-8 text-center">
              <span className="flex size-10 items-center justify-center rounded-full bg-surface-elevated text-text-secondary">
                <Icon name="bell" />
              </span>
              <p className="font-display text-sm font-semibold">{t("emptyTitle")}</p>
              <p className="text-sm text-text-secondary">{t("emptyBody")}</p>
            </div>
          ) : (
            <ul className="max-h-80 divide-y divide-border overflow-y-auto">
              {notifications.map((item) => (
                <li key={item.id} className="px-4 py-3">
                  <p className="text-sm font-medium">{item.title}</p>
                  {item.body ? (
                    <p className="text-sm text-text-secondary">{item.body}</p>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </div>
  );
}
