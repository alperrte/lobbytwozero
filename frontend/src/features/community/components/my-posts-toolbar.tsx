"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { activeFilterCount, supportsFilters, type MyPostsView } from "../services/my-posts-query";
import { isInvalidRange } from "../services/post-query";
import { emptyFilters, postCategories, type PostFilters } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import { buttonClass, FieldError, inputClass } from "./community-ui";
import { GamePicker } from "./game-picker";
import { PriorityMenu } from "./priority-menu";

/**
 * Gönderilerim araç çubuğu. Yalnız API'nin desteklediği denetimler vardır: metin arama, "Öncelik" (tarihe göre sıralama
 * açılır listesi: eskiden yeniye / yeniden eskiye) ve (kompakt
 * "Filtrele" panelinde) oyun, kategori ve tarih aralığı. Yeniden paylaşımlarda yalnız sıralama sunulur: arama/oyun/kategori
 * süzgeçleri o kayıtların kendi alanlarına uygulandığından anlamlı sonuç vermez.
 */
export function MyPostsToolbar({
  view,
  filters,
  onChange
}: {
  view: MyPostsView;
  filters: PostFilters;
  onChange: (next: PostFilters) => void;
}) {
  const t = useTranslations("Community.mine");
  const id = useId();
  const filterable = supportsFilters(view);
  const count = activeFilterCount(filters);
  const extraActive = Boolean(filters.gameId || filters.category || filters.fromDate || filters.toDate);
  // Panel, URL'den gelen etkin filtrelerle açık başlar; sonrasında serbestçe açılıp kapanır.
  const [panelOpen, setPanelOpen] = useState(extraActive);
  const open = filterable && panelOpen;
  const invalidRange = isInvalidRange(filters);

  return (
    <section aria-label={t("toolbarLabel")} className="mb-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
        {filterable ? <SearchField key={filters.q} initial={filters.q} onSubmit={(q) => onChange({ ...filters, q })} /> : null}

        {/* Arama solda. Sağ grup yan yana ve sağ kenara hizalı: Öncelik ⌄ → Filtrele → Yeniden paylaşımlarım (aynı yükseklik,
            eşit aralık). "Öncelik" kapalıyken daima kendi adını taşır; seçili sıralama yalnız menüde işaretlenir. */}
        <div className="flex flex-wrap items-center gap-3 sm:ms-auto">
          <PriorityMenu value={filters.sort} onChange={(sort) => onChange({ ...filters, sort })} />
          {filterable ? (
            <button
              type="button"
              className={cn(buttonClass, "gap-2")}
              aria-expanded={open}
              aria-controls={`${id}-panel`}
              onClick={() => setPanelOpen((value) => !value)}
            >
              <Icon name="settings" className="size-4" />
              {t("filter")}
              {count > 0 ? (
                <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-semibold text-primary-foreground">
                  <span aria-hidden="true">{count}</span>
                  <span className="sr-only">{t("filterCount", { count })}</span>
                </span>
              ) : null}
            </button>
          ) : null}

          {view !== "reposts" ? (
            <Link href="/community/mine/reposts" className={cn(buttonClass, "gap-2")}>
              <CommunityIcon name="repost" className="size-4" />
              {t("repostsLink")}
            </Link>
          ) : null}
        </div>
      </div>

      {open ? (
        <div id={`${id}-panel`} className="mt-3 rounded-panel border border-border bg-surface p-4">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <GamePicker id={`${id}-game`} label={t("game")} value={filters.gameId} onChange={(gameId) => onChange({ ...filters, gameId })} />
            <div>
              <label htmlFor={`${id}-category`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("category")}
              </label>
              <select
                id={`${id}-category`}
                value={filters.category}
                onChange={(event) => onChange({ ...filters, category: event.target.value as PostFilters["category"] })}
                className={inputClass}
              >
                <option value="">{t("allCategories")}</option>
                {postCategories.map((category) => (
                  <option key={category} value={category}>
                    {t(`categories.${category}`)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor={`${id}-from`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("from")}
              </label>
              <input id={`${id}-from`} type="date" value={filters.fromDate} max={filters.toDate || undefined} onChange={(event) => onChange({ ...filters, fromDate: event.target.value })} className={inputClass} />
            </div>
            <div>
              <label htmlFor={`${id}-to`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("to")}
              </label>
              <input id={`${id}-to`} type="date" value={filters.toDate} min={filters.fromDate || undefined} onChange={(event) => onChange({ ...filters, toDate: event.target.value })} className={inputClass} />
            </div>
          </div>
          {invalidRange ? <FieldError id={`${id}-range`}>{t("invalidRange")}</FieldError> : null}
          {count > 0 ? (
            <div className="mt-3">
              <button type="button" className={buttonClass} onClick={() => onChange({ ...emptyFilters, sort: filters.sort })}>
                {t("clear")}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

/** Arama gönderimle uygulanır (her tuşta istek ya da URL değişimi olmaz); en az 2 karakter sunucu kuralıdır. */
function SearchField({ initial, onSubmit }: { initial: string; onSubmit: (q: string) => void }) {
  const t = useTranslations("Community.mine");
  const id = useId();
  const [draft, setDraft] = useState(initial);
  const short = draft.trim().length === 1;

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = draft.trim();
    if (value.length === 1) return;
    onSubmit(value);
  }

  return (
    <form role="search" onSubmit={submit} className="w-full sm:w-72">
      <label htmlFor={`${id}-q`} className="sr-only">
        {t("search")}
      </label>
      <div className="relative">
        <Icon name="search" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary" />
        <input
          id={`${id}-q`}
          type="search"
          value={draft}
          maxLength={100}
          onChange={(event) => setDraft(event.target.value)}
          aria-describedby={short ? `${id}-hint` : undefined}
          placeholder={t("searchPlaceholder")}
          className={cn(inputClass, "ps-9")}
        />
      </div>
      {short ? (
        <p id={`${id}-hint`} className="mt-1 text-xs text-text-secondary">
          {t("searchMin")}
        </p>
      ) : null}
    </form>
  );
}
