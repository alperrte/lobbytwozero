"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { usePathname } from "@/shared/lib/i18n/navigation";
import { CatalogComparisonBar } from "./catalog-comparison-bar";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { specKeys, type Comparison, type Product } from "../types/hardware";
import { button, primaryButton, HardwareDialog, HardwareError } from "./hardware-ui";

type Selection = { products: Product[]; toggle: (product: Product) => void; clear: () => void };
const Context = createContext<Selection | null>(null);
export function useComparison() {
  const value = useContext(Context);
  if (!value) throw new Error("ComparisonProvider required");
  return value;
}
export function ComparisonProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("Hardware");
  const catalogPage = usePathname() === "/hardware";
  const [products, setProducts] = useState<Product[]>([]);
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<Comparison>();
  const [selectionError, setSelectionError] = useState(false);
  const mutation = useHardwareMutation();
  function toggle(product: Product) {
    if (products.some((item) => item.id === product.id)) {
      setProducts(products.filter((item) => item.id !== product.id)); setSelectionError(false); return;
    }
    if (products.length === 2 || products.some((item) => item.category !== product.category)) { setSelectionError(true); return; }
    setProducts([...products, product]); setSelectionError(false);
  }
  async function compare() {
    setResult(undefined); setOpen(true);
    const response = await mutation.run(() => hardwareApi.compareProducts(products[0].id, products[1].id));
    if (response) setResult(response);
  }
  return <Context value={{ products, toggle, clear: () => setProducts([]) }}>
    {children}
    {products.length && catalogPage ? <CatalogComparisonBar products={products} onRemove={toggle} onClear={() => { setProducts([]); setSelectionError(false); }} onCompare={compare} pending={mutation.pending} selectionError={selectionError} /> : null}
    {products.length && !catalogPage ? <section aria-label={t("comparison.title")} className="sticky bottom-3 z-20 mx-auto mt-8 max-w-4xl rounded-panel border border-control-border bg-surface p-4 shadow-float">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-display font-semibold">{t("comparison.selected", { count: products.length })}</p>
          <p className="mt-1 break-words text-sm text-text-secondary">{products.map((item) => item.model).join(" / ")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className={button} onClick={() => { setProducts([]); setSelectionError(false); }}>{t("common.clear")}</button>
          <button className={primaryButton} disabled={products.length !== 2 || mutation.pending} onClick={compare}>{t("comparison.compare")}</button>
        </div>
      </div>
      {selectionError ? <p role="status" className="mt-3 text-sm text-warning">{t("comparison.limit")}</p> : null}
    </section> : null}
    {open ? <HardwareDialog title={t("comparison.title")} onClose={() => setOpen(false)} busy={mutation.pending}>
      <p className="text-sm text-text-secondary">{t("comparison.facts")}</p>
      <HardwareError error={mutation.error} />
      {mutation.pending ? <p role="status">{t("common.loading")}</p> : null}
      {result ? <div className="overflow-x-auto">
        <table className="w-full text-start text-sm">
          <thead>
            <tr>
              <th className="p-2 text-start">{t("comparison.property")}</th>
              {[result.left, result.right].map((item) => <th key={item.id} className="min-w-36 p-2 text-start font-display">{item.modelName}</th>)}
            </tr>
          </thead>
          <tbody>{specKeys.map((key) => <tr key={key} className="border-t border-border">
            <th className="p-2 text-start font-normal text-text-secondary">{t(`specs.${key}`)}</th>
            <td className="p-2">{result.left.specifications[key] ?? t("common.unknown")}</td>
            <td className="p-2">{result.right.specifications[key] ?? t("common.unknown")}</td>
          </tr>)}</tbody>
        </table>
      </div> : null}
    </HardwareDialog> : null}
  </Context>;
}
