"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useComparison } from "./comparison-provider";
import { CatalogIcon } from "./catalog-icons";
import { CatalogProductArt } from "./catalog-product-art";
import type { Product, Specifications } from "../types/hardware";
import styles from "./catalog-styles";

const order: Record<string, (keyof Specifications)[]> = {
  CPU: ["coreCount", "clockMhz", "socket", "powerWatts"],
  GPU: ["capacityGb", "memoryType", "clockMhz", "powerWatts"],
  RAM: ["capacityGb", "memoryType", "clockMhz", "formFactor"],
  STORAGE: ["capacityGb", "interfaceType", "formFactor", "powerWatts"],
  MOTHERBOARD: ["socket", "memoryType", "formFactor", "interfaceType"],
  COOLING: ["formFactor", "socket", "powerWatts", "interfaceType"]
};

export function CatalogProductCard({ product }: { product: Product }) {
  const t = useTranslations("Hardware");
  const selection = useComparison();
  const selected = selection.products.some((item) => item.id === product.id);
  const fields = (order[product.category] ?? Object.keys(product.specifications) as (keyof Specifications)[])
    .filter((key) => product.specifications[key] !== null).slice(0, 4);
  const href = { pathname: "/hardware/[productId]" as const, params: { productId: product.id } };

  return <article className={styles.card} data-selected={selected} data-testid="catalog-product">
    <div className={styles.media}>
      <label className={styles.cardCheck}>
        <input type="checkbox" aria-label={t("visual.selectProduct", { model: product.model })} checked={selected} onChange={() => selection.toggle(product)} />
      </label>
      <span className={styles.badge}>{t(`categories.${product.category}`)}</span>
      <CatalogProductArt category={product.category} className={styles.art} />
      <span className={styles.artCaption}>{t("visual.categoryDrawing")}</span>
    </div>
    <div className={styles.cardBody}>
      <h2 className={styles.productName}><Link href={href} title={product.model}>{product.model}</Link></h2>
      <p className={styles.productDescription}>{product.brands.length ? product.brands.map((brand) => brand.name).join(" · ") : t("visual.brandMissing")}</p>
      <dl className={styles.specList}>
        {fields.length ? fields.map((key) => <div className={styles.specRow} key={key}>
          <CatalogIcon name={product.category} />
          <dt>{t(`specs.${key}`)}</dt><dd>{product.specifications[key]}</dd>
        </div>) : <div className={styles.noSpecs}><dt className="sr-only">{t("detail.specifications")}</dt><dd>{t("visual.noSpecs")}</dd></div>}
      </dl>
      <div className={styles.actions}>
        <Link href={href} className={`${styles.action} ${styles.actionPrimary}`}><CatalogIcon name="detail" />{t("visual.details")}</Link>
        <button type="button" className={styles.action} aria-pressed={selected} aria-label={t(selected ? "comparison.remove" : "comparison.add")} onClick={() => selection.toggle(product)}>
          <CatalogIcon name="compare" />{t(selected ? "visual.removeShort" : "visual.compareShort")}
        </button>
      </div>
    </div>
  </article>;
}
