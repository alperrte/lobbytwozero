"use client";
import { useTranslations } from "next-intl";
import { HardwareProductVisual } from "./hardware-product-visual";
import { Link } from "@/shared/lib/i18n/navigation";
import type { Product } from "../types/hardware";
import { useComparison } from "./comparison-provider";
import { button, panel } from "./hardware-ui";

export function ProductCard({ product, list = false }: { product: Product; list?: boolean }) {
  const t = useTranslations("Hardware");
  const selection = useComparison();
  const selected = selection.products.some((item) => item.id === product.id);
  return <article className={`${panel} overflow-hidden ${list ? "flex flex-wrap sm:flex-nowrap" : "flex flex-col"}`}>
    <div className={`flex items-center justify-center border-border bg-surface-elevated/40 ${list ? "w-full border-b p-6 sm:w-36 sm:shrink-0 sm:border-e sm:border-b-0" : "aspect-[5/3] border-b"}`}>
      <HardwareProductVisual product={product} className="min-h-44" />
    </div>
    <div className="flex min-w-0 flex-1 flex-col p-5">
      <span className="text-xs font-semibold uppercase tracking-wider text-brand-text">{t(`categories.${product.category}`)}</span>
      <h2 className="mt-2 font-display text-lg font-semibold">
        <Link className="break-words hover:underline" href={{ pathname: "/hardware/[productId]", params: { productId: product.id } }}>{product.model}</Link>
      </h2>
      <p className="mt-2 text-xs text-text-secondary">{product.brands.length ? product.brands.map((brand) => brand.name).join(" · ") : t("catalog.brandIndependent")}</p>
      <dl className="my-4 space-y-1 text-xs">{Object.entries(product.specifications).filter(([, value]) => value !== null).slice(0, 3).map(([key, value]) => <div className="flex justify-between gap-3" key={key}>
        <dt className="text-text-secondary">{t(`specs.${key}`)}</dt>
        <dd className="break-words text-end">{value}</dd>
      </div>)}</dl>
      <button type="button" aria-pressed={selected} className={`${button} mt-auto w-full ${selected ? "border-primary text-brand-text" : ""}`} onClick={() => selection.toggle(product)}>{t(selected ? "comparison.remove" : "comparison.add")}</button>
    </div>
  </article>;
}
