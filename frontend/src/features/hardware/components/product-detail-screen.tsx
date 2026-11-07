"use client";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { Icon } from "@/shared/components/ui/icons";
import { isUuid } from "../schemas/hardware";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { useComparison } from "./comparison-provider";
import { button, panel, HardwareEmpty, HardwareError, HardwareLoading } from "./hardware-ui";
import { SpecificationTable } from "./specification-table";
import { ProductReviews } from "./product-reviews";
import { ProductCard } from "./product-card";

function RelatedProducts({ category, productId }: { category: string; productId: string }) {
  const t = useTranslations("Hardware");
  const related = useHardwareQuery(`related:${category}`, (signal) => hardwareApi.products(`?category=${category}&size=4&page=0`, signal));
  return <section className="mt-8 space-y-4">
    <h2 className="font-display text-xl font-semibold">{t("detail.related")}</h2>
    <HardwareError error={related.error} retry={related.reload} />
    {related.loading ? <HardwareLoading /> : null}
    <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-3">{related.data?.items.filter((item) => item.id !== productId).slice(0, 3).map((item) => <ProductCard key={item.id} product={item} />)}</div>
  </section>;
}
export function ProductDetailScreen({ productId }: { productId: string }) {
  const t = useTranslations("Hardware");
  const valid = isUuid(productId);
  const result = useHardwareQuery(`product:${productId}`, (signal) => hardwareApi.product(productId, signal), valid);
  const comparison = useComparison();
  const product = result.data;
  if (!valid) return <HardwareEmpty title={t("errors.404.title")} description={t("errors.404.description")} />;
  return <>
    <nav aria-label={t("common.breadcrumb")} className="mb-6 flex flex-wrap gap-2 text-sm text-text-secondary">
      <Link className="hover:text-text-primary" href="/hardware">{t("catalog.title")}</Link>
      {product ? <>
        <span aria-hidden="true">/</span>
        <Link href={{ pathname: "/hardware", query: { category: product.category } }}>{t(`categories.${product.category}`)}</Link>
        <span aria-hidden="true">/</span>
        <span className="break-words text-text-primary">{product.model}</span>
      </> : null}
    </nav>
    {result.loading ? <HardwareLoading /> : null}
    <HardwareError error={result.error} retry={result.reload} />
    {product ? <>
      <div className="grid items-start gap-7 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <div className={`${panel} flex aspect-square max-h-[27rem] flex-col items-center justify-center gap-5 bg-surface-elevated/30`}>
          <Icon name="cpu" className="size-24 text-text-secondary/60" />
          <span className="text-sm text-text-secondary">{t("common.noImage")}</span>
        </div>
        <div className="min-w-0">
          <span className="text-xs font-semibold uppercase tracking-wider text-brand-text">{t(`categories.${product.category}`)}</span>
          <h1 className="mt-3 break-words font-display text-3xl font-bold sm:text-4xl">{product.model}</h1>
          <p className="mt-3 text-sm text-text-secondary">{product.brands.length ? product.brands.map((brand) => brand.name).join(" · ") : t("catalog.brandIndependent")}</p>
          <div className="my-6 flex flex-wrap gap-3">
            <button className={button} onClick={() => comparison.toggle(product)} aria-pressed={comparison.products.some((item) => item.id === product.id)}>{t(comparison.products.some((item) => item.id === product.id) ? "comparison.remove" : "comparison.add")}</button>
            <a href="#reviews" className={button}>{t("reviews.title")}</a>
          </div>
          <p className="border-s-2 border-border ps-4 text-sm leading-6 text-text-secondary">{t("detail.dataNotice")}</p>
        </div>
      </div>
      <section className={`${panel} mt-8 p-5 sm:p-7`}>
        <h2 className="mb-3 font-display text-xl font-semibold">{t("detail.specifications")}</h2>
        <SpecificationTable specifications={product.specifications} />
      </section>
      <ProductReviews key={product.id} productId={product.id} />
      <RelatedProducts category={product.category} productId={product.id} />
    </> : null}
  </>;
}
