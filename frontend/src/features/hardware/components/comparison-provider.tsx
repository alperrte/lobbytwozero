"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/shared/lib/i18n/navigation";
import { CatalogComparisonBar } from "./catalog-comparison-bar";
import type { Product } from "../types/hardware";
import { button, primaryButton } from "./hardware-ui";

/** Aynı anda karşılaştırılabilecek en fazla ürün sayısı. */
export const MAX_COMPARISON = 4;
const MIN_COMPARISON = 2;

type Selection = { products: Product[]; toggle: (product: Product) => void; clear: () => void };
const Context = createContext<Selection | null>(null);
export function useComparison() {
  const value = useContext(Context);
  if (!value) throw new Error("ComparisonProvider required");
  return value;
}
export function ComparisonProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("Hardware");
  const router = useRouter();
  const pathname = usePathname();
  const catalogPage = pathname === "/hardware";
  const comparePage = pathname === "/hardware/compare";
  const [products, setProducts] = useState<Product[]>([]);
  const [selectionError, setSelectionError] = useState(false);
  function toggle(product: Product) {
    if (products.some((item) => item.id === product.id)) {
      setProducts(products.filter((item) => item.id !== product.id)); setSelectionError(false); return;
    }
    if (products.length >= MAX_COMPARISON || products.some((item) => item.category !== product.category)) { setSelectionError(true); return; }
    setProducts([...products, product]); setSelectionError(false);
  }
  function clear() { setProducts([]); setSelectionError(false); }
  function compare() {
    if (products.length < MIN_COMPARISON) return;
    router.push({ pathname: "/hardware/compare", query: { ids: products.map((item) => item.id).join(",") } });
  }
  return <Context value={{ products, toggle, clear }}>
    {children}
    {products.length && catalogPage ? <CatalogComparisonBar products={products} onRemove={toggle} onClear={clear} onCompare={compare} minimum={MIN_COMPARISON} maximum={MAX_COMPARISON} selectionError={selectionError} /> : null}
    {products.length && !catalogPage && !comparePage ? <section aria-label={t("comparison.title")} className="sticky bottom-3 z-20 mx-auto mt-8 max-w-4xl rounded-panel border border-control-border bg-surface p-4 shadow-float">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-display font-semibold">{t("comparison.selected", { count: products.length, max: MAX_COMPARISON })}</p>
          <p className="mt-1 break-words text-sm text-text-secondary">{products.map((item) => item.model).join(" / ")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className={`${button} rounded-control`} onClick={clear}>{t("common.clear")}</button>
          <button className={`${primaryButton} rounded-control`} disabled={products.length < MIN_COMPARISON} onClick={compare}>{t("comparison.compare")}</button>
        </div>
      </div>
      {selectionError ? <p role="status" className="mt-3 text-sm text-warning">{t("comparison.limit", { max: MAX_COMPARISON })}</p> : null}
    </section> : null}
  </Context>;
}
