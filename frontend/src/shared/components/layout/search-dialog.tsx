"use client";

import { useId, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import {
  collectEntries,
  searchEntries,
  type SearchEntry
} from "@/shared/lib/search/search";
import { searchProviders } from "@/shared/lib/search/pages-provider";
import { cn } from "@/shared/utils/cn";

type SearchDialogProps = {
  open: boolean;
  onClose: () => void;
};

/**
 * Global arama (komut paleti). Ctrl/Cmd+K veya navbar'daki arama alanıyla açılır.
 * Şimdilik yalnız var olan sayfalar aranır; sahte oyun/oyuncu sonucu yoktur.
 * ARIA: combobox + listbox, aktif seçenek `aria-activedescendant` ile bildirilir.
 */
export function SearchDialog({ open, onClose }: SearchDialogProps) {
  const titleId = useId();

  return (
    <ModalDialog
      open={open}
      onClose={onClose}
      labelledBy={titleId}
      className="mx-auto mt-[10vh] max-h-[80dvh] w-[min(40rem,calc(100vw-1.5rem))] rounded-panel border border-border bg-surface shadow-popover open:animate-pop-in backdrop:backdrop-blur-[2px]"
    >
      <SearchPanel titleId={titleId} onClose={onClose} />
    </ModalDialog>
  );
}

function SearchPanel({
  titleId,
  onClose
}: {
  titleId: string;
  onClose: () => void;
}) {
  const t = useTranslations("Navbar.search");
  const pagesT = useTranslations("Pages");
  const router = useRouter();
  const listId = useId();
  const groupId = useId();
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);

  const entries = useMemo(
    () => collectEntries(searchProviders, (key) => pagesT(key as never)),
    [pagesT]
  );
  const results = useMemo(() => searchEntries(entries, query), [entries, query]);
  const activeId = results[activeIndex] ? optionId(listId, results[activeIndex]) : undefined;

  function go(entry: SearchEntry) {
    onClose();
    router.push(entry.href);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (results.length === 0) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((activeIndex + 1) % results.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((activeIndex - 1 + results.length) % results.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      go(results[activeIndex] ?? results[0]);
    }
  }

  return (
    <div className="flex max-h-[80dvh] flex-col">
      <h2 id={titleId} className="sr-only">
        {t("title")}
      </h2>

      <div className="flex items-center gap-3 border-b border-border px-4">
        <Icon name="search" className="size-5 shrink-0 text-text-secondary" />
        <input
          autoFocus
          type="text"
          role="combobox"
          aria-expanded="true"
          aria-controls={results.length > 0 ? listId : undefined}
          aria-activedescendant={activeId}
          aria-autocomplete="list"
          aria-label={t("inputLabel")}
          placeholder={t("placeholder")}
          autoComplete="off"
          spellCheck={false}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setActiveIndex(0);
          }}
          onKeyDown={onKeyDown}
          className="h-14 min-w-0 flex-1 bg-transparent text-base text-text-primary outline-none placeholder:text-text-secondary"
        />
        <button
          type="button"
          onClick={onClose}
          className="rounded-control border border-border px-2 py-1 font-sans text-xs font-medium text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary"
        >
          {t("close")}
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-2">
        {results.length > 0 ? (
          <>
            <p
              id={groupId}
              className="px-3 pb-1 pt-2 font-display text-xs font-semibold text-text-secondary"
            >
              {t("groupPages")}
            </p>
            <ul
              id={listId}
              role="listbox"
              aria-labelledby={groupId}
              className="space-y-0.5"
            >
              {results.map((entry, index) => (
                <li
                  key={entry.id}
                  id={optionId(listId, entry)}
                  role="option"
                  aria-selected={index === activeIndex}
                  onMouseMove={() => setActiveIndex(index)}
                  onClick={() => go(entry)}
                  className={cn(
                    "flex cursor-pointer items-center gap-3 rounded-control px-3 py-2.5",
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
        ) : (
          <div className="px-3 py-8 text-center">
            <p className="font-display text-sm font-semibold">{t("emptyTitle")}</p>
            <p className="mt-1 text-sm text-text-secondary">{t("emptyBody")}</p>
          </div>
        )}
      </div>

      <p className="sr-only" role="status" aria-live="polite">
        {t("resultCount", { count: results.length })}
      </p>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border px-4 py-2.5 text-xs text-text-secondary">
        <span>{t("hintNavigate")}</span>
        <span>{t("hintOpen")}</span>
        <span>{t("hintClose")}</span>
        <span className="ms-auto max-sm:hidden">{t("scope")}</span>
      </div>
    </div>
  );
}

function optionId(listId: string, entry: SearchEntry): string {
  return `${listId}-${entry.id}`;
}
