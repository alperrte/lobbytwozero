"use client";
import { useLocale, useTranslations } from "next-intl";
import { formatSpec, readSpec, specProfiles } from "../schemas/spec-profile";
import { specKeys, type Product, type Specifications } from "../types/hardware";

export function SpecificationTable({ specifications }: { specifications: Specifications }) {
  const t = useTranslations("Hardware");
  return <dl className="divide-y divide-border">{specKeys.map((key) => <div key={key} className="grid grid-cols-2 gap-4 py-3 text-sm">
    <dt className="text-text-secondary">{t(`specs.${key}`)}</dt>
    <dd className="break-words font-medium">{specifications[key] ?? t("common.unknown")}</dd>
  </div>)}</dl>;
}

/** Kategoriye özel ayrıntılı tablo: her satır typedSpecifications veya temel alandan okunur; kayıt yoksa tire gösterilir. */
export function ProductSpecificationTable({ product }: { product: Product }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const keys = specProfiles[product.category]?.detail;
  if (!keys) return <SpecificationTable specifications={product.specifications} />;
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  return <dl className="divide-y divide-border" data-testid="product-specifications">{keys.map((key) => {
    const value = formatSpec(readSpec(product, key), locale, labels);
    return <div key={key} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 py-3 text-sm">
      <dt className="text-text-secondary">{t(`fields.${product.category}.${key}`)}</dt>
      <dd className="break-words font-medium">{value ?? <><span aria-hidden="true" className="text-text-secondary">—</span><span className="sr-only">{t("visual.notRecorded")}</span></>}</dd>
    </div>;
  })}</dl>;
}
