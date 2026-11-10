"use client";

import { useContext, useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { useShellUser } from "@/shared/hooks/use-shell-user";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { PlayerLookupContext, type PlayerLookupHit } from "@/shared/providers/player-lookup-context";
import { searchProviders } from "@/shared/lib/search/pages-provider";
import { collectEntries, searchEntries } from "@/shared/lib/search/search";
import { cn } from "@/shared/utils/cn";

const MAX_RESULTS = 6;
const DESKTOP_SEARCH_QUERY = "(min-width: 768px)";
const PLAYER_DEBOUNCE_MS = 250;

interface PlayerResult {
  term: string;
  hits: PlayerLookupHit[];
  failed: boolean;
}

/** Listede gösterilen tek satır; sayfa ve oyuncu sonuçları aynı biçime çevrilir. */
interface Row {
  id: string;
  title: string;
  description?: string;
  open: () => void;
}

/**
 * Navbar'a gömülü arama: modal yok. Yazmaya başlayınca (ör. "a") yalnız eşleşen sayfalar
 * alanın altında küçük bir liste olarak açılır; boş sorguda hiçbir şey listelenmez.
 * Ctrl/Cmd+K kutuya odaklanır. Mobilde büyüteç düğmesi alanı navbar'ın üstüne açar.
 * ARIA: combobox + listbox, aktif seçenek `aria-activedescendant` ile bildirilir.
 * "@" ile başlayan metin oyuncu aramasına döner ("@ali"): kısa bir bekleyişten sonra sunucudan gerçek sonuçlar
 * gelir, Enter seçili oyuncuya ya da seçim yoksa tüm sonuç sayfasına (/players?q=) götürür. Oturumsuzsa giriş istenir.
 * Sahte oyun/oyuncu sonucu yoktur.
 */
export function NavbarSearch() {
  const t = useTranslations("Navbar.search");
  const pagesT = useTranslations("Pages");
  const router = useRouter();
  const listId = useId();
  const groupId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [focused, setFocused] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [playerResult, setPlayerResult] = useState<PlayerResult | null>(null);
  const lookup = useContext(PlayerLookupContext);
  const signedIn = useShellUser() !== null;

  const entries = useMemo(
    () => collectEntries(searchProviders, (key) => pagesT(key as never)),
    [pagesT]
  );
  const hasQuery = query.trim().length > 0;
  const playerMode = query.trimStart().startsWith("@");
  const playerTerm = playerMode ? (lookup?.normalize(query.trimStart().slice(1)) ?? null) : null;
  const playerReady = playerTerm !== null && lookup !== null && signedIn;
  const pageResults = useMemo(
    () => (hasQuery && !playerMode ? searchEntries(entries, query).slice(0, MAX_RESULTS) : []),
    [entries, hasQuery, playerMode, query]
  );
  const currentPlayers = playerReady && playerResult?.term === playerTerm ? playerResult : null;
  const playersLoading = playerReady && currentPlayers === null;

  const rows: Row[] = playerMode
    ? (currentPlayers?.hits ?? []).map((hit) => ({
        id: `player-${hit.userId}`,
        title: hit.name || hit.username || "",
        description: hit.username && hit.name ? `@${hit.username}` : undefined,
        open: () => router.push({ pathname: "/players/[userId]", params: { userId: hit.userId } })
      }))
    : pageResults.map((entry) => ({
        id: entry.id,
        title: entry.title,
        description: entry.description,
        open: () => router.push(entry.href)
      }));
  const panelOpen = focused && hasQuery;
  const activeRow = rows[activeIndex];

  // "@ad": kısa bekleyişten sonra oyuncuları sunucudan getirir; yazı değişince önceki istek iptal edilir.
  useEffect(() => {
    if (!playerReady || lookup === null || playerTerm === null) {
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      lookup
        .search(playerTerm, controller.signal)
        .then((hits) => setPlayerResult({ term: playerTerm, hits: hits.slice(0, MAX_RESULTS), failed: false }))
        .catch(() => {
          if (!controller.signal.aborted) {
            setPlayerResult({ term: playerTerm, hits: [], failed: true });
          }
        });
    }, PLAYER_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [lookup, playerReady, playerTerm]);

  // Mobilde alan açılınca odak kutuya geçer.
  useEffect(() => {
    if (expanded) {
      inputRef.current?.focus();
    }
  }, [expanded]);

  // Ctrl/Cmd+K: arama kutusuna odaklanır (modal açmaz).
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      // Tarayıcı otomatik doldurma `key` alanı olmayan keydown olayı üretebilir.
      if (typeof event.key !== "string" || event.key.toLowerCase() !== "k" || !(event.ctrlKey || event.metaKey)) {
        return;
      }
      if (event.altKey || event.shiftKey) {
        return;
      }
      event.preventDefault();
      if (window.matchMedia(DESKTOP_SEARCH_QUERY).matches) {
        inputRef.current?.focus();
        inputRef.current?.select();
      } else {
        setExpanded(true);
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  function reset() {
    setQuery("");
    setActiveIndex(0);
    setExpanded(false);
  }

  function go(row: Row) {
    reset();
    inputRef.current?.blur();
    row.open();
  }

  function goToPlayerResults(term: string) {
    reset();
    inputRef.current?.blur();
    router.push({ pathname: "/players", query: { q: term } });
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      if (hasQuery) {
        event.preventDefault();
        // Navbar'ın üstündeki genel Escape işleyicilerine (menü/popover) sızmasın.
        event.stopPropagation();
        reset();
      } else if (expanded) {
        reset();
      }
      return;
    }
    if (event.key === "Enter" && playerMode) {
      event.preventDefault();
      if (activeRow) {
        go(activeRow);
      } else if (playerReady && playerTerm !== null) {
        goToPlayerResults(playerTerm);
      }
      return;
    }
    if (rows.length === 0) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((activeIndex + 1) % rows.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((activeIndex - 1 + rows.length) % rows.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      go(activeRow ?? rows[0]);
    }
  }

  /** Oyuncu modunda sonuç listesi boşken gösterilecek mesajın anahtarı. */
  function playerStatusKey() {
    if (!signedIn) return "playersLogin" as const;
    if (playerTerm === null) return "playersHint" as const;
    if (playersLoading) return "playersLoading" as const;
    return currentPlayers?.failed ? ("playersError" as const) : ("playersEmpty" as const);
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setExpanded(true)}
        aria-label={t("open")}
        aria-keyshortcuts="Control+K Meta+K"
        className={cn(iconButtonClass, "md:hidden")}
      >
        <Icon name="search" />
      </button>

      <div
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) {
            setFocused(false);
            setExpanded(false);
          }
        }}
        className={cn(
          "min-w-0 items-center gap-2 md:flex md:flex-1",
          expanded
            ? "max-md:absolute max-md:inset-0 max-md:z-10 max-md:flex max-md:rounded-navbar max-md:bg-surface max-md:px-2"
            : "max-md:hidden"
        )}
      >
        <div className="relative flex h-10 min-w-0 flex-1 items-center">
          <Icon
            name="search"
            aria-hidden="true"
            className="pointer-events-none absolute start-3.5 size-4 text-text-secondary"
          />
          <input
            ref={inputRef}
            type="text"
            role="combobox"
            aria-expanded={panelOpen}
            aria-controls={panelOpen && rows.length > 0 ? listId : undefined}
            aria-activedescendant={
              panelOpen && activeRow ? `${listId}-${activeRow.id}` : undefined
            }
            aria-autocomplete="list"
            aria-keyshortcuts="Control+K Meta+K"
            aria-label={t("inputLabel")}
            placeholder={t("placeholder")}
            autoComplete="off"
            spellCheck={false}
            value={query}
            onFocus={() => setFocused(true)}
            onChange={(event) => {
              setQuery(event.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={onKeyDown}
            className="h-10 w-full min-w-0 rounded-xl border border-transparent bg-surface-elevated ps-10 pe-3 text-sm text-text-primary transition-colors duration-150 ease-out placeholder:text-text-secondary hover:border-border hover:bg-surface-elevated/70 focus-visible:border-primary focus-visible:bg-surface-elevated focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/25 md:pe-16"
          />
          {hasQuery ? null : (
            <kbd
              aria-hidden="true"
              className="pointer-events-none absolute end-3 hidden items-center rounded-md border border-border bg-surface px-1.5 py-0.5 font-sans text-[0.6875rem] font-medium leading-none text-text-secondary md:inline-flex"
            >
              Ctrl K
            </kbd>
          )}

          {panelOpen ? (
            <div className="absolute inset-x-0 top-full z-40 mt-2 overflow-hidden rounded-xl border border-border bg-surface shadow-popover animate-pop-in max-md:-inset-x-2">
              {rows.length > 0 ? (
                <>
                  <p
                    id={groupId}
                    className="px-4 pb-1 pt-3 font-display text-xs font-semibold text-text-secondary"
                  >
                    {playerMode ? t("groupPlayers") : t("groupPages")}
                  </p>
                  <ul
                    id={listId}
                    role="listbox"
                    aria-labelledby={groupId}
                    className="max-h-80 space-y-0.5 overflow-y-auto overscroll-contain p-2 pt-1"
                  >
                    {rows.map((entry, index) => (
                      <li
                        key={entry.id}
                        id={`${listId}-${entry.id}`}
                        role="option"
                        aria-selected={index === activeIndex}
                        // Tıklama sırasında kutunun odağı kaybedilmesin (liste kapanmasın).
                        onMouseDown={(event) => event.preventDefault()}
                        onMouseMove={() => setActiveIndex(index)}
                        onClick={() => go(entry)}
                        className={cn(
                          "flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2",
                          index === activeIndex
                            ? "bg-surface-elevated text-text-primary"
                            : "text-text-secondary"
                        )}
                      >
                        <Icon
                          name="arrowRight"
                          className={cn(
                            "size-4 shrink-0",
                            index === activeIndex ? "text-brand-text" : "opacity-0"
                          )}
                        />
                        <span className="min-w-0">
                          <span className="block truncate font-display text-sm font-medium text-text-primary">
                            {entry.title}
                          </span>
                          {entry.description ? (
                            <span className="block truncate text-xs text-text-secondary">
                              {entry.description}
                            </span>
                          ) : null}
                        </span>
                      </li>
                    ))}
                  </ul>
                </>
              ) : playerMode ? (
                <div className="px-4 py-6 text-center">
                  <p className="font-display text-sm font-semibold">{t(playerStatusKey())}</p>
                </div>
              ) : (
                <div className="px-4 py-6 text-center">
                  <p className="font-display text-sm font-semibold">{t("emptyTitle")}</p>
                  <p className="mt-1 text-sm text-text-secondary">{t("emptyBody")}</p>
                </div>
              )}
            </div>
          ) : null}
        </div>

        <button
          type="button"
          onClick={reset}
          aria-label={t("close")}
          className={cn(iconButtonClass, "md:hidden")}
        >
          <Icon name="close" />
        </button>

        <p className="sr-only" role="status" aria-live="polite">
          {panelOpen && !playersLoading ? t("resultCount", { count: rows.length }) : ""}
        </p>
      </div>
    </>
  );
}
