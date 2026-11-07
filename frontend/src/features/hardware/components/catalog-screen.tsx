"use client";
import { useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { catalogCategories, type Category } from "../types/hardware";
import { isCategory } from "../schemas/hardware";
import { useCatalogLocation } from "../hooks/use-catalog-location";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { HardwareDialog, HardwareEmpty, HardwareError, HardwarePagination } from "./hardware-ui";
import { CatalogFilters } from "./catalog-filters";
import { CatalogIcon } from "./catalog-icons";
import { CatalogProductCard } from "./catalog-product-card";
import styles from "./catalog-styles";

const primaryCategories = catalogCategories;
export function CatalogScreen() {
  const t = useTranslations("Hardware");
  const format = useFormatter();
  const { search, query, update } = useCatalogLocation();
  const [filterOpen, setFilterOpen] = useState(false);
  const [list, setList] = useState(false);
  const [draft, setDraft] = useState({ value: search.get("q") ?? "", base: search.toString() });
  const draftValue = draft.base === search.toString() ? draft.value : search.get("q") ?? "";
  useEffect(() => {
    if (draft.base !== search.toString() || draft.value === (search.get("q") ?? "")) return;
    const timer = setTimeout(() => update({ q: draft.value, page: "0" }), 400);
    return () => clearTimeout(timer);
  }, [draft, search, update]);
  const discoveryQuery = new URLSearchParams(query);
  discoveryQuery.set("discovery", "true");
  if (!catalogCategories.includes(discoveryQuery.get("category") as Category)) discoveryQuery.delete("category");
  const result = useHardwareQuery(`catalog:${discoveryQuery}`, (signal) => hardwareApi.products(`?${discoveryQuery}`, signal));
  const rawCategory = search.get("category");
  const category = isCategory(rawCategory) && catalogCategories.includes(rawCategory) ? rawCategory : undefined;
  const apply = (values: Record<string, string>) => { update({ q: search.get("q") ?? "", category: category ?? "", ...values }, true); setFilterOpen(false); };
  function chooseCategory(value: string) {
    update({ q: search.get("q") ?? "", brand: search.get("brand") ?? "", brandId: search.get("brandId") ?? "", category: value }, true);
  }
  const filters = <CatalogFilters key={query} category={category} search={new URLSearchParams(search.toString())} onApply={apply} catalogUnavailable={!!result.error} />;
  return <div className={styles.catalog}>
    <header className={styles.banner}>
      <div className={styles.bannerIcon}><CatalogIcon name="cube" /></div>
      <div className={styles.bannerCopy}><h1>{t("catalog.title")}</h1><p>{t("catalog.description")}</p></div>
      <div className={styles.bannerArt} aria-hidden="true"><i /><i /><i /><div className={styles.motto}><span>{t("visual.discover")}</span><span>{t("visual.compareMotto")}</span><span>{t("visual.buildMotto")}</span></div></div>
    </header>
    <nav aria-label={t("catalog.categories")} className={styles.categories}>
      {primaryCategories.map((item) => <button type="button" className={styles.category} aria-pressed={category === item} key={item} onClick={() => chooseCategory(category === item ? "" : item)}><CatalogIcon name={item} /><span>{t(`categories.${item}`)}</span></button>)}
    </nav>
    <div className={styles.body}>
      <aside className={styles.filterPanel}>{filters}</aside>
      <section className={styles.results} aria-label={t("visual.results")}>
        <div className={styles.toolbar}>
          <p role="status" className={styles.resultCount}>{result.data ? <><strong>{format.number(result.data.totalElements)}</strong> {t("visual.productsFound")}</> : t("catalog.title")}</p>
          <label className={styles.search}>
            <span className="sr-only">{t("catalog.search")}</span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><circle cx="10" cy="10" r="7" /><path d="m15 15 6 6" /></svg>
            <input type="search" className={styles.field} maxLength={160} placeholder={t("catalog.search")} value={draftValue} onChange={(event) => setDraft({ value: event.target.value, base: search.toString() })} />
          </label>
          <button type="button" className={`${styles.action} ${styles.mobileFilters}`} onClick={() => setFilterOpen(true)}><CatalogIcon name="filter" />{t("catalog.filters")}</button>
          <div className={styles.views} role="group" aria-label={t("catalog.view")}>
            <button type="button" className={styles.viewButton} aria-label={t("catalog.grid")} aria-pressed={!list} onClick={() => setList(false)}><CatalogIcon name="grid" /><span>{t("catalog.grid")}</span></button>
            <button type="button" className={styles.viewButton} aria-label={t("catalog.list")} aria-pressed={list} onClick={() => setList(true)}><CatalogIcon name="list" /><span>{t("catalog.list")}</span></button>
          </div>
        </div>
        {result.loading ? <div className={styles.grid} role="status" aria-label={t("common.loading")}>{Array.from({ length: 6 }, (_, index) => <div className={styles.loadingCard} key={index}><div /><span /><span /><span /></div>)}</div> : null}
        {result.error ? <div className={styles.errorState}><HardwareError error={result.error} retry={result.reload} /></div> : null}
        {result.data?.items.length === 0 ? <div className={styles.emptyState}><HardwareEmpty title={t("catalog.emptyTitle")} description={t("catalog.emptyDescription")} /></div> : null}
        <div className={list ? styles.list : styles.grid} data-testid="catalog-grid">{result.data?.items.map((product) => <CatalogProductCard key={product.id} product={product} />)}</div>
        {result.data ? <HardwarePagination page={result.data.page} totalPages={result.data.totalPages} onPage={(page) => update({ page: String(page) })} /> : null}
      </section>
    </div>
    {filterOpen ? <HardwareDialog title={t("catalog.filters")} onClose={() => setFilterOpen(false)}>{filters}</HardwareDialog> : null}
  </div>;
}
