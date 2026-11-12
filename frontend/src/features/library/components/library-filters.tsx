"use client";

import { useTranslations } from "next-intl";
import { Select } from "@/shared/components/ui/select";
import { buttonClass } from "@/shared/components/ui/button-styles";
import type { LibraryQuery, LibraryView } from "../types/library";

export function LibraryFilters({ query, view, onChange }: { query: LibraryQuery; view: LibraryView; onChange: (changes: Record<string, string>) => void }) {
  const t = useTranslations("Library");
  return <div className="flex flex-wrap items-end gap-3">
    {(["favorite", "completed"] as const).map((field) => <div key={field} className="min-w-36 flex-1 basis-36 space-y-2">
      <span id={`library-${field}`} className="text-sm">{t(field)}</span>
      <Select aria-labelledby={`library-${field}`} value={query[field] === undefined ? "all" : String(query[field])} disabled={(view === "favorites" && field === "favorite") || (view === "completed" && field === "completed")} options={[{ value: "all", label: t("filters.all") }, { value: "true", label: t("filters.yes") }, { value: "false", label: t("filters.no") }]} onChange={(value) => onChange({ [field]: value === "all" ? "" : value, page: "" })} />
    </div>)}
    <div className="min-w-48 flex-1 basis-48 space-y-2"><span id="library-sort" className="text-sm">{t("sort")}</span>
      <Select aria-labelledby="library-sort" value={`${query.sort}:${query.direction}`} options={["ADDED_AT:DESC", "ADDED_AT:ASC", "TITLE:ASC", "TITLE:DESC"].map((value) => ({ value, label: t(`sorting.${value.replace(":", "_")}`) }))} onChange={(value) => { const [sort, direction] = value.split(":"); onChange({ sort, direction, page: "" }); }} />
    </div>
    <div className="w-28 space-y-2"><span id="library-size" className="text-sm">{t("pageSize")}</span><Select aria-labelledby="library-size" value={String(query.size)} options={[10, 20, 50].map((size) => ({ value: String(size), label: String(size) }))} onChange={(size) => onChange({ size, page: "" })} /></div>
  </div>;
}

export function LibraryPagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (page: number) => void }) {
  const t = useTranslations("Library");
  const last = Math.max(0, Math.ceil(total / size) - 1);
  return <nav aria-label={t("pagination")} className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
    <p className="text-sm text-text-secondary">{t("page", { page: page + 1, pages: last + 1 })}</p>
    <div className="flex gap-2"><button type="button" disabled={page === 0} onClick={() => onChange(Math.max(0, Math.min(last, page - 1)))} className={buttonClass("secondary")}>{t("previous")}</button><button type="button" disabled={page >= last} onClick={() => onChange(page + 1)} className={buttonClass("secondary")}>{t("next")}</button></div>
  </nav>;
}
