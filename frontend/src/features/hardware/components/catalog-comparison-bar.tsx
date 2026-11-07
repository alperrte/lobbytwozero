"use client";
import { useTranslations } from "next-intl";
import type { Product } from "../types/hardware";
import { CatalogIcon } from "./catalog-icons";
import { CatalogProductArt } from "./catalog-product-art";
import styles from "./catalog-styles";

export function CatalogComparisonBar({ products, onRemove, onClear, onCompare, pending, selectionError }: {
  products: Product[]; onRemove: (product: Product) => void; onClear: () => void; onCompare: () => void; pending: boolean; selectionError: boolean;
}) {
  const t = useTranslations("Hardware");
  return <section aria-label={t("comparison.title")} className={styles.comparisonBar}>
    <div className={styles.comparisonIntro}><CatalogIcon name="compare" /><div><strong>{t("visual.comparisonList")}</strong><span>{t("comparison.selected", { count: products.length })}</span></div></div>
    <div className={styles.selectionItems}>{products.map((product) => <div className={styles.selectionItem} key={product.id}>
      <CatalogProductArt category={product.category} className={styles.selectionArt} />
      <div><p title={product.model}>{product.model}</p><small>{t(`categories.${product.category}`)}</small></div>
      <button type="button" className={styles.remove} aria-label={t("visual.removeProduct", { model: product.model })} onClick={() => onRemove(product)}><span aria-hidden="true">×</span></button>
    </div>)}</div>
    <div className={styles.selectionControls}>
      <button type="button" className={styles.clear} onClick={onClear}>{t("common.clear")}</button>
      <button type="button" className={styles.compareAction} disabled={products.length !== 2 || pending} onClick={onCompare}>{t("visual.openComparison")}<CatalogIcon name="arrow" /></button>
    </div>
    {selectionError ? <p role="status" className={styles.selectionError}>{t("comparison.limit")}</p> : null}
  </section>;
}
