"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { menuItemClass } from "@/shared/components/ui/styles";
import { focusFirstMenuItem, onMenuKeyDown, usePopover } from "@/shared/hooks/use-popover";
import { cn } from "@/shared/utils/cn";
import type { FriendView } from "../types/social.types";
import { GameBackdrop, GameLine } from "./game-cover";
import { PresenceLabel } from "./presence-label";
import { usePerson } from "./use-person";

export type FriendIntent = "remove" | "block";

type FriendCardProps = {
  view: FriendView;
  disabled?: boolean;
  onIntent: (intent: FriendIntent, view: FriendView) => void;
};

/**
 * Arkadaş kartı: avatar, ad, kullanıcı adı, durum, oynadığı oyun ve işlemler.
 * "Mesaj Gönder" ve "Profili Gör", ilgili ekranlar yayınlanana kadar pasiftir ve nedenini söyler (kırık bağlantı yok).
 */
export function FriendCard({ view, disabled = false, onIntent }: FriendCardProps) {
  const t = useTranslations("Social");
  const { profile, presence } = view;
  const person = usePerson()(profile);
  const soonId = useId();
  const nameId = useId();

  return (
    <article
      aria-labelledby={nameId}
      className="group @container relative flex min-w-0 flex-1 flex-col gap-4 rounded-panel border border-border bg-surface p-4 transition-colors duration-150 hover:border-primary/45 motion-reduce:transition-none sm:p-5"
    >
      {presence.game ? (
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden rounded-panel">
          <GameBackdrop visual={presence.game.visual} />
        </div>
      ) : null}
      <div className="relative flex min-w-0 items-center gap-4">
        <Avatar seed={profile.avatarSeed} imageUrl={person.avatarUrl} size="lg" ring />
        <div className="min-w-0">
          <h3 id={nameId} className="truncate font-display text-xl font-semibold text-text-primary">
            {person.name}
          </h3>
          {person.handle ? <p className="truncate text-sm text-text-secondary">{person.handle}</p> : null}
          <PresenceLabel presence={presence} className="mt-1" />
        </div>
      </div>
      <div className="relative mt-auto flex items-center justify-between gap-3 border-t border-border/70 pt-4">
        <div className="min-w-0 flex-1">
          <GameLine game={presence.game} lastPlayed={presence.status === "offline"} />
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            aria-disabled="true"
            aria-describedby={soonId}
            title={t("card.messageSoon")}
            className={buttonClass("secondary", "sm", "@max-[25rem]:w-9 @max-[25rem]:px-0")}
          >
            <Icon name="message" className="size-4" />
            <span className="@max-[25rem]:sr-only">{t("card.message")}</span>
          </button>
          <span id={soonId} className="sr-only">
            {t("card.messageSoon")}
          </span>
          <FriendActionsMenu
            name={person.name}
            disabled={disabled}
            onSelect={(intent) => onIntent(intent, view)}
          />
        </div>
      </div>
    </article>
  );
}

function FriendActionsMenu({
  name,
  disabled,
  onSelect
}: {
  name: string;
  disabled: boolean;
  onSelect: (intent: FriendIntent) => void;
}) {
  const t = useTranslations("Social.card");
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const menuId = useId();

  function choose(intent: FriendIntent) {
    close();
    onSelect(intent);
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-label={t("moreActions", { name })}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        disabled={disabled}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
        className={cn(buttonClass("secondary", "sm"), "w-9 px-0 aria-expanded:border-primary/60")}
      >
        <Icon name="more" className="size-5" />
      </button>
      {open ? (
        <ul
          id={menuId}
          role="menu"
          aria-label={t("moreActions", { name })}
          ref={(element) => focusFirstMenuItem(element)}
          onKeyDown={onMenuKeyDown}
          className="absolute end-0 top-full z-30 mt-2 w-56 rounded-panel border border-border bg-surface p-1.5 shadow-popover animate-pop-in"
        >
          <li role="none">
            <button type="button" role="menuitem" aria-disabled="true" className={menuItemClass}>
              <Icon name="user" className="size-4" />
              <span className="flex-1">{t("viewProfile")}</span>
              <span className="rounded-full bg-surface-elevated px-2 py-0.5 text-[0.6875rem] font-semibold text-text-secondary">
                {t("soon")}
              </span>
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              onClick={() => choose("remove")}
              className={cn(menuItemClass, "text-brand-text hover:text-brand-text")}
            >
              <Icon name="userMinus" className="size-4" />
              {t("removeFriend")}
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              onClick={() => choose("block")}
              className={cn(menuItemClass, "text-brand-text hover:text-brand-text")}
            >
              <Icon name="ban" className="size-4" />
              {t("block")}
            </button>
          </li>
        </ul>
      ) : null}
    </div>
  );
}
