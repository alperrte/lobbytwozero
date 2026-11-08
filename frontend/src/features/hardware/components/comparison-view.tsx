"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { formatSpec, productName, productSubtitle, readSpec, specProfiles, specSignature } from "../schemas/spec-profile";
import type { Product } from "../types/hardware";
import { HardwareProductVisual } from "./hardware-product-visual";
import { button } from "./hardware-ui";

type RowState = "similar" | "different" | "missing";
const stateStyle: Record<RowState, string> = {
  similar: "border-s-2 border-s-transparent",
  different: "border-s-2 border-s-primary bg-primary/5",
  missing: "border-s-2 border-s-transparent text-text-secondary"
};

/**
 * Yan yana teknik karşılaştırma. Yalnız kayıtlı değerler gösterilir; eksik değer "—" olur,
 * "kazanan" ilan edilmez ve eksik alan "aynı" sayılmaz.
 */
export function ComparisonView({ left, right }: { left: Product; right: Product }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const [onlyDifferences, setOnlyDifferences] = useState(false);
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  const keys = specProfiles[left.category]?.detail ?? [];
  const rows = keys.map((key) => {
    const a = readSpec(left, key);
    const b = readSpec(right, key);
    const state: RowState = !a || !b ? "missing" : specSignature(a) === specSignature(b) ? "similar" : "different";
    return { key, state, a: formatSpec(a, locale, labels), b: formatSpec(b, locale, labels) };
  }).filter((row) => row.a !== undefined || row.b !== undefined);
  const count = (state: RowState) => rows.filter((row) => row.state === state).length;
  const visible = onlyDifferences ? rows.filter((row) => row.state === "different") : rows;
  const states: RowState[] = ["different", "similar", "missing"];

  return <div className="space-y-4" data-testid="comparison-view">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p role="status" className="text-sm text-text-secondary">{t("comparison.summary", { different: count("different"), same: count("similar"), missing: count("missing") })}</p>
      <button type="button" className={`${button} min-h-9 py-1`} aria-pressed={onlyDifferences} onClick={() => setOnlyDifferences(!onlyDifferences)}>
        {t(onlyDifferences ? "comparison.showAll" : "comparison.onlyDifferences")}
      </button>
    </div>
    <ul className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-text-secondary" aria-label={t("comparison.legend")}>
      {states.map((state) => <li key={state} className="flex items-center gap-2">
        <span aria-hidden="true" className={`inline-block h-4 w-1.5 ${state === "different" ? "bg-primary" : state === "similar" ? "bg-control-border" : "border border-dashed border-control-border"}`} />
        {t(`comparison.${state}`)}
      </li>)}
    </ul>
    <p className="text-xs text-text-secondary">{t("productVisual.representative")}</p>
    <div className="overflow-x-auto rounded-panel border border-border">
      <table className="w-full min-w-[34rem] table-fixed border-collapse text-start text-sm">
        <caption className="sr-only">{t("comparison.title")}</caption>
        <thead>
          <tr className="align-bottom">
            <th scope="col" className="w-[28%] p-3 text-start text-xs font-medium text-text-secondary">{t("comparison.property")}</th>
            {[left, right].map((item) => <th scope="col" key={item.id} className="p-3 text-start align-top">
              <div className="flex items-center gap-3">
                <HardwareProductVisual product={item} variant="compact" className="h-12 w-20 flex-none rounded-control bg-background" />
                <div className="min-w-0">
                  <Link href={{ pathname: "/hardware/[productId]", params: { productId: item.id } }} className="block break-words font-display text-sm font-semibold leading-5 hover:text-brand-text">{productName(item)}</Link>
                  <p className="mt-0.5 break-words text-xs font-normal text-text-secondary">{productSubtitle(item) ?? t(`categories.${item.category}`)}</p>
                </div>
              </div>
            </th>)}
          </tr>
        </thead>
        <tbody>
          {visible.map((row) => <tr key={row.key} data-state={row.state} className={`border-t border-border ${stateStyle[row.state]}`}>
            <th scope="row" className="p-3 text-start text-sm font-normal text-text-secondary">
              {t(`fields.${left.category}.${row.key}`)}
              <span className="sr-only"> — {t(`comparison.${row.state}`)}</span>
            </th>
            <td className="break-words p-3 font-medium">{row.a ?? <span className="text-text-secondary">—</span>}</td>
            <td className="break-words p-3 font-medium">{row.b ?? <span className="text-text-secondary">—</span>}</td>
          </tr>)}
          {visible.length === 0 ? <tr className="border-t border-border"><td colSpan={3} className="p-4 text-sm text-text-secondary">{t(rows.length ? "comparison.noDifferences" : "visual.noSpecs")}</td></tr> : null}
        </tbody>
      </table>
    </div>
  </div>;
}
