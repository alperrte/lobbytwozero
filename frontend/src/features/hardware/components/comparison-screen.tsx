"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { isUuid } from "../schemas/hardware";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import type { Product } from "../types/hardware";
import { MAX_COMPARISON, useComparison } from "./comparison-provider";
import { ComparisonView } from "./comparison-view";
import { button, HardwareEmpty, HardwareError, HardwareHeading, HardwareLoading } from "./hardware-ui";

/** `?ids=a,b,c` değerinden geçerli, tekrarsız ve en çok dört kimliği çıkarır. */
export function parseComparisonIds(raw: string | null): string[] {
  const ids = (raw ?? "").split(",").map((value) => value.trim()).filter(isUuid);
  return [...new Set(ids.map((value) => value.toLowerCase()))].slice(0, MAX_COMPARISON);
}

/** Karşılaştırma sayfası: ürünler URL'deki kimliklerden yüklenir, bu yüzden yenileme ve bağlantı paylaşımı çalışır. */
export function ComparisonScreen() {
  const t = useTranslations("Hardware");
  const router = useRouter();
  const search = useSearchParams();
  const selection = useComparison();
  const raw = search.get("ids");
  const ids = useMemo(() => parseComparisonIds(raw), [raw]);
  const key = ids.join(",");
  const enabled = ids.length >= 2;
  const result = useHardwareQuery<Product[]>(`compare:${key}`, (signal) => Promise.all(ids.map((id) => hardwareApi.product(id, signal))), enabled);
  const products = result.data;
  const sameCategory = !products || products.every((product) => product.category === products[0].category);

  function remove(product: Product) {
    const next = ids.filter((id) => id !== product.id.toLowerCase());
    if (selection.products.some((item) => item.id === product.id)) selection.toggle(product);
    router.replace(next.length ? { pathname: "/hardware/compare", query: { ids: next.join(",") } } : { pathname: "/hardware" }, { scroll: false });
  }

  return <>
    <HardwareHeading title={t("comparison.title")} description={t("comparison.facts")}>
      <Link href="/hardware" className={button}>{t("detail.backToCatalog")}</Link>
    </HardwareHeading>
    {!enabled ? <HardwareEmpty title={t("comparison.needTwoTitle")} description={t("comparison.needTwo")} /> : null}
    {enabled && result.loading ? <HardwareLoading /> : null}
    {enabled ? <HardwareError error={result.error} retry={result.reload} /> : null}
    {enabled && products && !sameCategory ? <HardwareEmpty title={t("comparison.mixedTitle")} description={t("comparison.mixed")} /> : null}
    {enabled && products && sameCategory ? <ComparisonView products={products} onRemove={remove} /> : null}
  </>;
}
