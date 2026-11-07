"use client";

import { useTranslations } from "next-intl";
import { controlClass } from "./game-status";

export function GameFilters({ query, onChange }: { query: string; onChange: (values: Record<string, string>) => void }) {
  const t = useTranslations("Game");
  const params = new URLSearchParams(query);
  return <details className="mt-5 rounded-panel border border-border p-4">
    <summary className="cursor-pointer py-2 font-semibold">{t("filters")}</summary>
    <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {["genre", "platform", "language", "releasedAfter", "releasedBefore", "minRating"].map((key) => <label key={key} className="flex min-w-0 flex-col gap-2 text-sm">{t(`fields.${key}`)}
        <input type={key.startsWith("released") ? "date" : key === "minRating" ? "number" : "text"} min={key === "minRating" ? 1 : undefined} max={key === "minRating" ? 10 : undefined} step={key === "minRating" ? "0.01" : undefined} maxLength={64} value={params.get(key) ?? ""} onChange={(event) => onChange({ [key]: event.target.value, page: "" })} className={controlClass} />
      </label>)}
      <label className="flex flex-col gap-2 text-sm">{t("fields.earlyAccess")}
        <select className={controlClass} value={params.get("earlyAccess") ?? ""} onChange={(event) => onChange({ earlyAccess: event.target.value, page: "" })}>
          <option value="">{t("all")}</option><option value="true">{t("yes")}</option><option value="false">{t("no")}</option>
        </select>
      </label>
      {["popularOnly", "discountedOnly"].map((key) => <label key={key} className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={params.get(key) === "true"} onChange={(event) => onChange({ [key]: event.target.checked ? "true" : "", page: "" })} />{t(`fields.${key}`)}</label>)}
      <label className="flex flex-col gap-2 text-sm">{t("fields.country")}<select className={controlClass} value={params.get("country") || "TR"} onChange={(event) => onChange({ country: event.target.value, page: "" })}>{["TR", "US", "GB", "DE"].map((value) => <option key={value}>{value}</option>)}</select></label>
      <label className="flex flex-col gap-2 text-sm">{t("fields.currency")}<select className={controlClass} value={params.get("currency") || "TRY"} onChange={(event) => onChange({ currency: event.target.value, page: "" })}>{["USD", "EUR", "GBP", "TRY"].map((value) => <option key={value}>{value}</option>)}</select></label>
    </div>
    <p className="mt-4 text-sm text-text-secondary">{t("filterHelp")}</p>
    <button type="button" className={`${controlClass} mt-4`} onClick={() => onChange(Object.fromEntries(["genre", "platform", "language", "releasedAfter", "releasedBefore", "minRating", "earlyAccess", "popularOnly", "discountedOnly", "page"].map((key) => [key, ""])))}>{t("clearFilters")}</button>
  </details>;
}
