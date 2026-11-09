"use client";

import { useEffect, useId, useRef } from "react";
import { useTranslations } from "next-intl";
import { menuItemClass } from "@/shared/components/ui/styles";
import { focusFirstMenuItem, onMenuKeyDown, usePopover } from "@/shared/hooks/use-popover";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { CommunityIcon, type CommunityIconName } from "./community-icons";

type PostHref = { pathname: "/community/posts/[postId]"; params: { postId: string }; query?: { edit: string } };

export type PostAction =
  | { id: "edit"; label: string; href: PostHref }
  | { id: "edit"; label: string; onSelect: () => void }
  | { id: "delete"; label: string; icon: CommunityIconName; onSelect: () => void }
  | { id: "report"; label: string; onSelect: () => void };

/** 44 px dokunma alanı; stil ortak `iconButtonClass` ile aynıdır (çakışan boyut sınıfları olmasın diye burada tek parça verilir). */
const TRIGGER_CLASS =
  "-me-2 -mt-1.5 inline-flex size-11 shrink-0 items-center justify-center rounded-xl text-text-secondary transition-colors duration-150 hover:bg-surface-elevated/70 hover:text-text-primary aria-expanded:bg-surface-elevated aria-expanded:text-text-primary aria-expanded:ring-1 aria-expanded:ring-inset aria-expanded:ring-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";

const ICONS: Record<PostAction["id"], CommunityIconName> = { edit: "pencil", delete: "trash", report: "flag" };

/**
 * Gönderi kartının sağ üstündeki "Gönderi işlemleri" (üç nokta) menüsü. Projenin ortak açılır panel davranışı (`usePopover`:
 * dışarı tıklama, Escape, odak dışarı çıkınca kapanma) ve menü stilleri/klavye gezinmesi (`menuItemClass`, `onMenuKeyDown`)
 * kullanılır. Liste yalnız kullanıcının yetkisi olan işlemleri içerir (karar çağıranda verilir; sunucu yine 403/404 ile korur);
 * hiç işlem yoksa menü çizilmez. Panel tetikleyicinin altına, sağ kenara hizalı açılır ve kartın kenarında kesilmez.
 * Bir işlem seçilince menü kapanır ve odak tetikleyiciye döner (diyalog açılıp kapanınca odak kaybolmaz).
 */
export function PostActionsMenu({ actions }: { actions: PostAction[] }) {
  const t = useTranslations("Community.post");
  const id = useId();
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) focusFirstMenuItem(menuRef.current);
  }, [open]);

  if (actions.length === 0) return null;

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? `${id}-menu` : undefined}
        aria-label={t("actions")}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
        className={TRIGGER_CLASS}
      >
        <CommunityIcon name="more" className="size-5" />
      </button>
      {open ? (
        <div
          ref={menuRef}
          id={`${id}-menu`}
          role="menu"
          aria-label={t("actions")}
          onKeyDown={onMenuKeyDown}
          className="absolute end-0 top-full z-40 mt-1 w-56 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-surface p-1.5 shadow-popover animate-pop-in"
        >
          {actions.map((action) => {
            const icon = action.id === "delete" ? action.icon : ICONS[action.id];
            const content = (
              <>
                <CommunityIcon name={icon} className="size-[18px] shrink-0" />
                <span className="min-w-0 flex-1 truncate">{action.label}</span>
              </>
            );
            const className = cn(menuItemClass, action.id === "delete" && "text-brand-text");
            if ("href" in action) {
              return (
                <Link key={action.id} role="menuitem" href={action.href} className={className} onClick={() => close(false)}>
                  {content}
                </Link>
              );
            }
            return (
              <button
                key={action.id}
                type="button"
                role="menuitem"
                className={className}
                onClick={() => {
                  close(true);
                  action.onSelect();
                }}
              >
                {content}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
