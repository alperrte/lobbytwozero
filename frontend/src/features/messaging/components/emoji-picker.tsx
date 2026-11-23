"use client";

import { useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";

/**
 * Emoji seçici (MSG-18). Liste istemcidedir; sunucu emoji listesi tutmaz, metni olduğu gibi saklar. Seçim metne imleç
 * konumunda eklenir. Ok tuşlarıyla ızgarada gezilir; emoji karakteri ekran okuyucuda kendi adıyla okunur.
 */
export const EMOJI_GROUPS = {
  smileys: ["😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "🥳", "😇", "🙂", "😉", "😋", "😜", "🤔", "🤨", "😐", "😏", "😴", "😮", "😢", "😭", "😡", "🤯", "😱", "🥶", "🥵", "🤝"],
  gestures: ["👍", "👎", "👏", "🙌", "🙏", "💪", "👊", "✌️", "🤞", "👌", "🤙", "👋", "🫡", "🫶", "👀", "🧠"],
  gaming: ["🎮", "🕹️", "👾", "🏆", "🥇", "🎯", "⚔️", "🛡️", "🏹", "💣", "🔥", "⚡", "💥", "🚀", "🎧", "🖥️", "⌨️", "🖱️", "🎲", "🧩"],
  symbols: ["❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💯", "✅", "❌", "⭐", "✨", "🎉", "💀", "👻", "🤖", "🍕", "☕", "🌙", "🇹🇷"]
} as const;

export type EmojiGroup = keyof typeof EMOJI_GROUPS;

/** Tepki için hızlı seçim (MSG-23). */
export const QUICK_REACTIONS = ["❤️", "👍", "😂", "😮", "😢", "🙏"] as const;

const COLUMNS = 8;

export function EmojiPicker({ onPick, className }: { onPick: (emoji: string) => void; className?: string }) {
  const t = useTranslations("Messaging.emoji");
  const [group, setGroup] = useState<EmojiGroup>("smileys");
  const emojis = EMOJI_GROUPS[group];

  function onGridKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>("button"));
    const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
    if (index < 0) return;
    const moves: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: COLUMNS, ArrowUp: -COLUMNS };
    const move = moves[event.key];
    if (move === undefined) return;
    event.preventDefault();
    const next = Math.min(buttons.length - 1, Math.max(0, index + move));
    buttons[next]?.focus();
  }

  return (
    <div className={cn("w-[19rem] max-w-[calc(100vw-2rem)] rounded-float border border-border bg-surface p-2 shadow-popover", className)}>
      <div role="tablist" aria-label={t("groups")} className="mb-2 flex gap-1 overflow-x-auto">
        {(Object.keys(EMOJI_GROUPS) as EmojiGroup[]).map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={group === key}
            onClick={() => setGroup(key)}
            className={cn(
              "shrink-0 rounded-control px-2.5 py-1 text-xs font-semibold text-text-secondary hover:bg-surface-elevated hover:text-text-primary focus-visible:outline-2 focus-visible:outline-focus",
              group === key && "bg-surface-elevated text-text-primary"
            )}
          >
            {t(key)}
          </button>
        ))}
      </div>
      <div role="group" aria-label={t(group)} onKeyDown={onGridKeyDown} className="grid grid-cols-8 gap-0.5">
        {emojis.map((emoji) => (
          <button
            key={emoji}
            type="button"
            onClick={() => onPick(emoji)}
            className="inline-flex size-9 items-center justify-center rounded-control text-xl hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus"
          >
            {emoji}
          </button>
        ))}
      </div>
    </div>
  );
}
