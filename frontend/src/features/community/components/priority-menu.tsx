"use client";

import { useEffect, useId, useRef } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { menuItemClass } from "@/shared/components/ui/styles";
import { focusFirstMenuItem, onMenuKeyDown, usePopover } from "@/shared/hooks/use-popover";
import { cn } from "@/shared/utils/cn";
import type { PostSort } from "../types/community.types";
import { buttonClass } from "./community-ui";

const OPTIONS = ["OLDEST", "NEWEST"] as const;
type Priority = (typeof OPTIONS)[number];

/**
 * Gönderilerim "Öncelik" menüsü (tarihe göre gerçek sıralama: `sort=OLDEST|NEWEST`). Kapalıyken tetikleyici DAİMA "Öncelik"
 * yazar; seçili seçenek başlığın yerine geçmez, yalnız açılan menüde işaretlenir. Projenin ortak açılır panel davranışı
 * (`usePopover`: dışarı tıklama, Escape, odak dışarı çıkınca kapanma) ve menü stilleri/klavye gezinmesi kullanılır; seçenekler
 * `menuitemradio`dur, seçim menüyü kapatır ve odağı tetikleyiciye döndürür. Tetikleyici yan kontrollerle (`buttonClass`) aynı yüksekliktedir.
 */
export function PriorityMenu({ value, onChange }: { value: PostSort; onChange: (sort: Priority) => void }) {
  const t = useTranslations("Community.mine");
  const id = useId();
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) focusFirstMenuItem(menuRef.current);
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        className={cn(buttonClass, "gap-2")}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? `${id}-menu` : undefined}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
      >
        <span>{t("priority")}</span>
        <Icon name="chevron" className={cn("size-4 shrink-0 text-text-secondary transition-transform duration-150", open && "rotate-180")} />
      </button>
      {open ? (
        <div
          ref={menuRef}
          id={`${id}-menu`}
          role="menu"
          aria-label={t("priority")}
          onKeyDown={onMenuKeyDown}
          className="absolute start-0 top-full z-40 mt-1 w-52 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-surface p-1.5 shadow-popover animate-pop-in"
        >
          {OPTIONS.map((option) => {
            const selected = value === option;
            return (
              <button
                key={option}
                type="button"
                role="menuitemradio"
                aria-checked={selected}
                className={cn(menuItemClass, selected && "font-semibold text-brand-text")}
                onClick={() => {
                  close(true);
                  if (!selected) onChange(option);
                }}
              >
                <span className="min-w-0 flex-1 truncate">{t(`priorityOptions.${option}`)}</span>
                {selected ? <Icon name="check" className="size-4 shrink-0 text-primary" /> : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
