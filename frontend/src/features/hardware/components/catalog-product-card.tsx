"use client";

import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useComparison } from "./comparison-provider";
import { CatalogIcon } from "./catalog-icons";
import { HardwareProductVisual } from "./hardware-product-visual";
import { cardSpecKeys, formatSpec, productName, productSubtitle, readSpec } from "../schemas/spec-profile";
import type { Product } from "../types/hardware";
import styles from "./catalog-styles";

export function CatalogProductCard({ product }: { product: Product }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const selection = useComparison();
  const selected = selection.products.some((item) => item.id === product.id);
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  const keys = cardSpecKeys(product, 4);
  const name = productName(product);
  const subtitle = productSubtitle(product);
  const href = { pathname: "/hardware/[productId]" as const, params: { productId: product.id } };

  return <article className={styles.card} data-selected={selected} data-testid="catalog-product">
    <div className={styles.media}>
      <label className={styles.cardCheck}>
        <input type="checkbox" aria-label={t("visual.selectProduct", { model: name })} checked={selected} onChange={() => selection.toggle(product)} />
      </label>
      <span className={styles.badge}>{t(`categories.${product.category}`)}</span>
      <HardwareProductVisual product={product} />
    </div>
    <div className={styles.cardBody}>
      <h2 className={styles.productName}><Link href={href} title={name}>{name}</Link></h2>
      <p className={styles.productDescription} title={subtitle}>{subtitle ?? " "}</p>
      <dl className={styles.specList}>
        {keys.length ? keys.map((key) => <div className={styles.specRow} key={key}>
          <CatalogIcon name={product.category} />
          <dt>{t(`fields.${product.category}.${key}`)}</dt><dd>{formatSpec(readSpec(product, key), locale, labels)}</dd>
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
