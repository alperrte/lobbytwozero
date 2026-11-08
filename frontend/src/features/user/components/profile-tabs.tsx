"use client";

import { useRef, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";

export const profileTabs = ["overview", "activity", "games", "hardware", "friends"] as const;
export type ProfileTabId = (typeof profileTabs)[number];

export function tabId(prefix: string, tab: ProfileTabId): string {
  return `${prefix}-tab-${tab}`;
}

export function panelId(prefix: string, tab: ProfileTabId): string {
  return `${prefix}-panel-${tab}`;
}

/**
 * Erişilebilir sekme çubuğu (WAI-ARIA tabs): yalnız etkin sekme Tab ile odaklanır, ok tuşları/Home/End sekme değiştirir.
 * Sekme durumu yereldir; adrese yazılmaz. Dar ekranda yatay kaydırılır.
 */
export function ProfileTabs({
  active,
  onChange,
  idPrefix
}: {
  active: ProfileTabId;
  onChange: (tab: ProfileTabId) => void;
  idPrefix: string;
}) {
  const t = useTranslations("Profile.tabs");
  const refs = useRef<Partial<Record<ProfileTabId, HTMLButtonElement | null>>>({});

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const index = profileTabs.indexOf(active);
    const last = profileTabs.length - 1;
    let next: number | null = null;
    if (event.key === "ArrowRight") next = index === last ? 0 : index + 1;
    else if (event.key === "ArrowLeft") next = index === 0 ? last : index - 1;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = last;
    if (next === null) return;
    event.preventDefault();
    const tab = profileTabs[next] as ProfileTabId;
    onChange(tab);
    refs.current[tab]?.focus();
  }

  return (
    <div className="overflow-x-auto border-b border-border">
      <div role="tablist" aria-label={t("label")} onKeyDown={onKeyDown} className="flex min-w-max gap-1">
        {profileTabs.map((tab) => {
          const selected = tab === active;
          return (
            <button
              key={tab}
              ref={(node) => {
                refs.current[tab] = node;
              }}
              type="button"
              role="tab"
              id={tabId(idPrefix, tab)}
              aria-selected={selected}
              aria-controls={panelId(idPrefix, tab)}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab)}
              className={cn(
                "-mb-px min-h-11 border-b-2 px-4 text-sm font-semibold transition-colors duration-150",
                selected
                  ? "border-primary text-text-primary"
                  : "border-transparent text-text-secondary hover:text-text-primary"
              )}
            >
              {t(tab)}
            </button>
          );
        })}
      </div>
    </div>
  );
}
