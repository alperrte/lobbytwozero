"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { useGames } from "../hooks/use-directory";
import { searchGames } from "../services/community-api";
import { isAbort } from "../services/community-errors";
import type { GameCard } from "../types/community.types";
import { actionClass, buttonClass, inputClass } from "./community-ui";

type SearchResult = { q: string; items: GameCard[]; failed: boolean };

/**
 * Oyun seçici: Game Service yerel katalog araması (`/games/search`, anonim). Seçim yalnız kimliği taşır; geçerliliği
 * Community sunucusu game-service'te doğrular (yoksa/yayında değilse 422). Katalog boşsa dürüst boş durum gösterilir.
 */
export function GamePicker({
  id,
  label,
  value,
  onChange,
  disabled = false,
  describedBy
}: {
  id: string;
  label: string;
  value: string;
  onChange: (gameId: string) => void;
  disabled?: boolean;
  describedBy?: string;
}) {
  const t = useTranslations("Community.game");
  const lookup = useGames([value || null]);
  const selected = value ? lookup(value) : null;
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<SearchResult>();
  const trimmed = query.trim();

  useEffect(() => {
    if (!trimmed) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchGames(trimmed, controller.signal).then(
        (items) => setResult({ q: trimmed, items, failed: false }),
        (error: unknown) => {
          if (!isAbort(error)) setResult({ q: trimmed, items: [], failed: true });
        }
      );
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed]);

  const shown = result && result.q === trimmed ? result : undefined;

  if (value) {
    return (
      <div>
        <span id={`${id}-label`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {label}
        </span>
        <div className="flex min-h-11 items-center justify-between gap-3 rounded-control border border-control-border bg-background px-3 py-1.5">
          <span className="truncate text-sm font-medium text-text-primary">{selected?.title ?? t("selected")}</span>
          <button type="button" className={cn(actionClass, "shrink-0")} onClick={() => onChange("")} disabled={disabled}>
            {t("clear")}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-text-primary">
        {label}
      </label>
      <div className="relative">
        <Icon name="search" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary" />
        <input
          id={id}
          type="search"
          autoComplete="off"
          value={query}
          disabled={disabled}
          aria-describedby={describedBy}
          placeholder={t("placeholder")}
          onChange={(event) => setQuery(event.target.value)}
          className={cn(inputClass, "ps-9")}
        />
      </div>
      {trimmed ? (
        <div className="mt-2 rounded-control border border-border bg-surface" role="group" aria-label={t("results")}>
          {!shown ? (
            <p role="status" className="px-3 py-2.5 text-sm text-text-secondary">
              {t("searching")}
            </p>
          ) : shown.failed ? (
            <p role="alert" className="px-3 py-2.5 text-sm text-text-secondary">
              {t("failed")}
            </p>
          ) : shown.items.length === 0 ? (
            <p role="status" className="px-3 py-2.5 text-sm text-text-secondary">
              {t("none")}
            </p>
          ) : (
            <ul>
              {shown.items.map((game) => (
                <li key={game.gameId} className="border-b border-border last:border-b-0">
                  <button
                    type="button"
                    className={cn(buttonClass, "w-full justify-start rounded-none border-0 bg-transparent font-medium")}
                    onClick={() => {
                      onChange(game.gameId);
                      setQuery("");
                    }}
                  >
                    {game.title}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}
