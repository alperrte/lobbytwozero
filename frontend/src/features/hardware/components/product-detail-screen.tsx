"use client";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { isUuid } from "../schemas/hardware";
import { cardSpecKeys, formatSpec, productName, productSubtitle, readSpec } from "../schemas/spec-profile";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { useComparison } from "./comparison-provider";
import { HardwareProductVisual } from "./hardware-product-visual";
import { CatalogProductCard } from "./catalog-product-card";
import { button, panel, primaryButton, HardwareEmpty, HardwareError, HardwareLoading } from "./hardware-ui";
import { ProductSpecificationTable } from "./specification-table";
import { ProductReviews } from "./product-reviews";

function RelatedProducts({ category, productId }: { category: string; productId: string }) {
  const t = useTranslations("Hardware");
  const related = useHardwareQuery(`related:${category}`, (signal) => hardwareApi.products(`?category=${category}&size=4&page=0&discovery=true`, signal));
  const others = related.data?.items.filter((item) => item.id !== productId).slice(0, 3) ?? [];
  if (related.data && others.length === 0 && !related.error) return null;
  return <section className="mt-10 space-y-4">
    <h2 className="font-display text-xl font-semibold">{t("detail.related")}</h2>
    <HardwareError error={related.error} retry={related.reload} />
    {related.loading ? <HardwareLoading /> : null}
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{others.map((item) => <CatalogProductCard key={item.id} product={item} />)}</div>
  </section>;
}

export function ProductDetailScreen({ productId }: { productId: string }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const valid = isUuid(productId);
  const result = useHardwareQuery(`product:${productId}`, (signal) => hardwareApi.product(productId, signal), valid);
  const comparison = useComparison();
  const product = result.data;
  if (!valid) return <HardwareEmpty title={t("errors.404.title")} description={t("errors.404.description")} />;
  const selected = product ? comparison.products.some((item) => item.id === product.id) : false;
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  const name = product ? productName(product) : "";
  const subtitle = product ? productSubtitle(product) : undefined;
  const features = product ? cardSpecKeys(product, 4) : [];
  return <>
    <nav aria-label={t("common.breadcrumb")} className="mb-5 flex flex-wrap items-center gap-2 text-sm text-text-secondary">
      <Link className="font-medium text-text-primary hover:text-brand-text" href="/hardware">{t("detail.backToCatalog")}</Link>
      {product ? <>
        <span aria-hidden="true">/</span>
        <Link className="hover:text-text-primary" href={{ pathname: "/hardware", query: { category: product.category } }}>{t(`categories.${product.category}`)}</Link>
        <span aria-hidden="true">/</span>
        <span className="break-words text-text-primary">{name}</span>
      </> : null}
    </nav>
    {result.loading ? <HardwareLoading /> : null}
    <HardwareError error={result.error} retry={result.reload} />
    {product ? <>
      <div className="grid items-stretch gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <HardwareProductVisual product={product} variant="detail" />
        <div className="min-w-0">
          <span className="inline-block rounded-full border border-primary/70 bg-primary/10 px-3 py-0.5 text-xs font-medium text-brand-text">{t(`categories.${product.category}`)}</span>
          <h1 className="mt-3 break-words font-display text-3xl font-bold leading-tight sm:text-4xl" data-testid="product-title">{name}</h1>
          {subtitle ? <p className="mt-2 text-base text-text-secondary">{subtitle}</p> : null}
          <p className="mt-2 text-sm text-text-secondary">{product.brands.length ? product.brands.map((brand) => brand.name).join(" · ") : t("catalog.brandIndependent")}</p>
          <h2 className="mt-6 text-sm font-semibold text-text-secondary">{t("detail.keyFeatures")}</h2>
          {features.length ? <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">{features.map((key) => <div key={key} className="min-w-0 rounded-panel border border-border bg-surface px-3 py-2.5">
            <dt className="truncate text-xs text-text-secondary" title={t(`fields.${product.category}.${key}`)}>{t(`fields.${product.category}.${key}`)}</dt>
            <dd className="mt-1 break-words font-display text-base font-semibold">{formatSpec(readSpec(product, key), locale, labels)}</dd>
          </div>)}</dl> : <p className="mt-3 text-sm text-text-secondary">{t("visual.noSpecs")}</p>}
          <div className="my-6 flex flex-wrap gap-3">
            <button type="button" className={selected ? button : primaryButton} onClick={() => comparison.toggle(product)} aria-pressed={selected}>{t(selected ? "comparison.remove" : "comparison.add")}</button>
            <a href="#reviews" className={button}>{t("detail.reviewsLink")}</a>
          </div>
        </div>
      </div>
      <section className={`${panel} mt-8 p-5 sm:p-7`} aria-labelledby="specs-title">
        <h2 id="specs-title" className="mb-3 font-display text-xl font-semibold">{t("detail.specifications")}</h2>
        <ProductSpecificationTable product={product} />
      </section>
      <ProductReviews key={product.id} productId={product.id} />
      <RelatedProducts category={product.category} productId={product.id} />
    </> : null}
  </>;
}
