"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi, queryString } from "../services/hardware-api";
import { cardSpecKeys, formatSpec, productName, productSubtitle, readSpec } from "../schemas/spec-profile";
import { productVisual } from "../schemas/product-visual";
import type { WorkshopSlot } from "../schemas/workshop";
import type { Product } from "../types/hardware";
import { HardwareProductVisual } from "./hardware-product-visual";
import { button, input, Field, HardwareDialog, HardwareError, HardwareLoading, HardwarePagination } from "./hardware-ui";

/** Ürünün kısa teknik özeti (en fazla `limit` değer); kayıtta olmayan değer gösterilmez. */
export function productSpecs(product: Product, locale: string, labels: { yes: string; no: string }, limit = 3) {
  return cardSpecKeys(product, limit).flatMap((key) => {
    const value = formatSpec(readSpec(product, key), locale, labels);
    return value ? [{ key, value }] : [];
  });
}

/** Atölye parçası seçimi: yalnız Hardware kataloğundaki gerçek ürünler listelenir. */
export function WorkshopPartPicker({ slot, selectedId, onSelect, onClose }: { slot: WorkshopSlot; selectedId?: string | null; onSelect: (product: Product) => void; onClose: () => void }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const products = useHardwareQuery(`workshop-pick:${slot}:${search}:${page}`, (signal) => hardwareApi.products(queryString({ category: slot, q: search || undefined, page, size: 8 }), signal), true, `workshop-pick:${slot}`);
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  return <HardwareDialog title={t("workshop.picker.title", { category: t(`categories.${slot}`) })} onClose={onClose} wide>
    <form role="search" className="flex items-end gap-2" onSubmit={(event) => { event.preventDefault(); setSearch(String(new FormData(event.currentTarget).get("search") ?? "").trim()); setPage(0); }}>
      <div className="min-w-0 flex-1">
        <Field label={t("workshop.picker.search")}>
          <input name="search" type="search" className={input} maxLength={160} defaultValue={search} autoComplete="off" />
        </Field>
      </div>
      <button className={button}><Icon name="search" className="size-4" />{t("common.search")}</button>
    </form>
    {products.loading && !products.data ? <HardwareLoading /> : null}
    <HardwareError error={products.error} retry={products.reload} />
    {products.data?.items.length === 0 ? <p className="text-sm text-text-secondary">{t("workshop.picker.empty")}</p> : null}
    {products.data?.items.length ? <ul className="grid gap-2 sm:grid-cols-2" aria-busy={products.loading}>
      {products.data.items.map((product) => {
        const visual = productVisual(product);
        const subtitle = productSubtitle(product);
        const specs = productSpecs(product, locale, labels);
        const selected = selectedId === product.id;
        return <li key={product.id}>
          <button type="button" aria-pressed={selected} onClick={() => onSelect(product)}
            className={`flex min-h-24 w-full items-center gap-3 rounded-panel border p-3 text-start transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus ${selected ? "border-primary bg-primary/5" : "border-border bg-surface"}`}>
            <HardwareProductVisual product={product} variant="compact" className="w-20 shrink-0" />
            <span className="min-w-0 flex-1">
              <span className="block break-words font-display font-semibold">{productName(product)}</span>
              <span className="mt-0.5 block text-xs text-text-secondary">{[visual.manufacturer ?? t("catalog.brandIndependent"), subtitle].filter(Boolean).join(" / ")}</span>
              {specs.length ? <span className="mt-1.5 flex flex-wrap gap-1">{specs.map((spec) => <span key={spec.key} className="rounded-control border border-border px-1.5 py-0.5 text-xs text-text-secondary">{t(`fields.${product.category}.${spec.key}`)}: {spec.value}</span>)}</span> : null}
            </span>
            {selected ? <span className="shrink-0 text-xs font-semibold text-brand-text">{t("workshop.picker.selected")}</span> : null}
          </button>
        </li>;
      })}
    </ul> : null}
    {products.data ? <HardwarePagination page={page} totalPages={products.data.totalPages} onPage={setPage} /> : null}
  </HardwareDialog>;
}
