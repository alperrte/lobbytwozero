"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { catalogCategories, type Category } from "../types/hardware";
import { hardwareApi } from "../services/hardware-api";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { Field, HardwarePagination } from "./hardware-ui";
import { CatalogIcon } from "./catalog-icons";
import styles from "./catalog-styles";

const categorySpecs: Partial<Record<Category, string[]>> = {
  CPU: ["CoreCount", "ClockMhz", "PowerWatts", "socket"], GPU: ["CapacityGb", "ClockMhz", "PowerWatts", "memoryType"],
  RAM: ["CapacityGb", "ClockMhz", "memoryType", "formFactor"], STORAGE: ["CapacityGb", "interfaceType", "formFactor"],
  MOTHERBOARD: ["socket", "memoryType", "formFactor"], PSU: ["PowerWatts", "formFactor"], COOLING: ["PowerWatts", "socket"]
};
const maxValues: Record<string, number> = { CapacityGb: 1048576, ClockMhz: 20000, CoreCount: 1024, PowerWatts: 10000 };
export function CatalogFilters({ category, search, onApply, catalogUnavailable = false }: { category?: Category; search: URLSearchParams; onApply: (values: Record<string, string>) => void; catalogUnavailable?: boolean }) {
  const t = useTranslations("Hardware");
  const [selectedCategory, setSelectedCategory] = useState<Category | "">(category ?? "");
  const [brandPage, setBrandPage] = useState(0);
  const brands = useHardwareQuery(`brands:${brandPage}`, (signal) => hardwareApi.brands(brandPage, signal));
  const specFields = selectedCategory ? categorySpecs[selectedCategory] ?? ["formFactor"] : [];
  return <form className={styles.filterForm} onInput={(event) => {
    for (const element of Array.from(event.currentTarget.elements)) {
      if (element instanceof HTMLInputElement) element.setCustomValidity("");
    }
  }} onSubmit={(event) => {
    event.preventDefault();
    const form = event.currentTarget;
    for (const key of specFields.filter((key) => /^[A-Z]/.test(key))) {
      const low = form.elements.namedItem(`min${key}`) as HTMLInputElement;
      const high = form.elements.namedItem(`max${key}`) as HTMLInputElement;
      high.setCustomValidity(low.value && high.value && Number(low.value) > Number(high.value) ? t("catalog.rangeInvalid") : "");
    }
    if (!form.reportValidity()) return;
    onApply({ ...Object.fromEntries(new FormData(form).entries()) as Record<string, string>, page: "0" });
  }}>
    <div className={styles.filterHeader}>
      <h2><CatalogIcon name="filter" />{t("catalog.filters")}</h2>
      <button type="button" className={styles.clear} onClick={() => onApply({ category: "", page: "0" })}>{t("visual.clearFilters")}</button>
    </div>
    <Field label={t("catalog.categories")}>
      <select name="category" className={styles.field} value={selectedCategory} onChange={(event) => setSelectedCategory(event.target.value as Category | "")}>
        <option value="">{t("catalog.allCategories")}</option>
        {catalogCategories.map((item) => <option key={item} value={item}>{t(`categories.${item}`)}</option>)}
      </select>
    </Field>
    <Field label={t("catalog.brand")}>
      <input name="brand" className={styles.field} maxLength={100} defaultValue={search.get("brand") ?? ""} />
    </Field>
    <Field label={t("catalog.brandSelect")}>
      <select name="brandId" className={styles.field} defaultValue={search.get("brandId") ?? ""} disabled={brands.loading || !!brands.error}>
        <option value="">{t("catalog.allBrands")}</option>
        {search.get("brandId") && !brands.data?.items.some((brand) => brand.id === search.get("brandId")) ? <option value={search.get("brandId")!}>{t("catalog.selectedBrand")}</option> : null}
        {brands.data?.items.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
      </select>
    </Field>
    {brands.error && !catalogUnavailable ? <p className={styles.brandNotice}>{t("visual.brandsUnavailable")} <button type="button" onClick={brands.reload}>{t("common.reload")}</button></p> : null}
    {brands.data ? <HardwarePagination page={brandPage} totalPages={brands.data.totalPages} onPage={setBrandPage} /> : null}
    {specFields.map((key) => /^[A-Z]/.test(key) ? <fieldset key={key} className={styles.range}>
      <legend>{t(`specs.${key[0].toLowerCase()}${key.slice(1)}`)}</legend>
      <div className={styles.rangeFields}>{["min", "max"].map((prefix) => <Field key={prefix} label={t(`catalog.${prefix}`)}>
        <input className={styles.field} type="number" min={1} max={maxValues[key]} name={`${prefix}${key}`} defaultValue={search.get(`${prefix}${key}`) ?? ""} />
      </Field>)}</div>
    </fieldset> : <Field key={key} label={t(`specs.${key}`)}>
      <input name={key} maxLength={32} className={styles.field} defaultValue={search.get(key) ?? ""} />
    </Field>)}
    <p className={styles.filterHint}>{t(selectedCategory ? "visual.recordedFilters" : "visual.chooseCategory")}</p>
    <button type="submit" className={styles.apply}>{t("catalog.apply")}</button>
  </form>;
}
