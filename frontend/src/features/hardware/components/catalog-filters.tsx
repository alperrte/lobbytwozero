"use client";
import { useLocale, useTranslations } from "next-intl";
import type { Category, FilterOption, FilterOptions } from "../types/hardware";
import { categoryFilters, exactFieldName, filterOptionLabel, specName, technicalQueryKeys, type FilterField } from "../schemas/spec-profile";
import { Checkbox } from "@/shared/components/ui/checkbox";
import { Select, type SelectOption } from "@/shared/components/ui/select";
import { Field } from "./hardware-ui";
import { CatalogIcon } from "./catalog-icons";
import styles from "./catalog-styles";

const maxValues: Record<string, number> = { CapacityGb: 1048576, ClockMhz: 20000, CoreCount: 1024, PowerWatts: 10000 };
const unitSuffix: Record<string, string> = { CapacityGb: "GB", ClockMhz: "MHz", PowerWatts: "W" };

export type FilterOptionsState = { data?: FilterOptions; loading: boolean; error?: unknown; reload: () => void };

/** "Tümü" her zaman başta; seçili değer seçenekler arasında yoksa (eski bağlantı) kaybolmasın diye ardına eklenir. */
function selectOptions(name: string, options: FilterOption[], current: string, locale: string, anyLabel: string): SelectOption[] {
  const legacy = current && !options.some((option) => option.value === current) && name !== "brandId" ? [{ value: current, label: current }] : [];
  return [{ value: "", label: anyLabel }, ...legacy, ...options.map((option) => ({ value: option.value, label: filterOptionLabel(name, option, locale) }))];
}

/**
 * Yalnız API'nin gerçekten desteklediği ve seçili kategorinin verisinde dolu olan filtreler sunulur.
 * Açılır liste seçenekleri katalogdaki kayıtlı değerlerden gelir (`filterOptions`); kategori seçilmediyse
 * yalnız üretici seçilebilir. Kategori sekmeleri filtre formunun dışındadır.
 */
export function CatalogFilters({ category, search, filterOptions, onApply }: { category?: Category; search: URLSearchParams; filterOptions: FilterOptionsState; onApply: (values: Record<string, string>) => void }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const fields: FilterField[] = category ? categoryFilters[category] ?? [] : [];
  const rangeKeys = fields.flatMap((field) => field.kind === "range" ? [field.key] : []);
  const clearValues = Object.fromEntries(["brand", "brandId", ...technicalQueryKeys].map((key) => [key, ""]));
  const options = filterOptions.data ?? {};
  const listLabel = (field: FilterField) => specName(field) === "chipset" ? t("catalog.gpuModel") : t(`fields.${category}.${specName(field)}`);
  const unavailable = !filterOptions.loading && Boolean(filterOptions.error);
  return <form className={styles.filterForm} onInput={(event) => {
    for (const element of Array.from(event.currentTarget.elements)) {
      if (element instanceof HTMLInputElement) element.setCustomValidity("");
    }
  }} onSubmit={(event) => {
    event.preventDefault();
    const form = event.currentTarget;
    for (const key of rangeKeys) {
      const low = form.elements.namedItem(`min${key}`) as HTMLInputElement | null;
      const high = form.elements.namedItem(`max${key}`) as HTMLInputElement | null;
      if (low && high) high.setCustomValidity(low.value && high.value && Number(low.value) > Number(high.value) ? t("catalog.rangeInvalid") : "");
    }
    if (!form.reportValidity()) return;
    // İşaretsiz onay kutusu forma girmez; eski değer URL'de kalmasın diye açıkça boşaltılır.
    const values: Record<string, string> = { page: "0", ...Object.fromEntries(fields.flatMap((field) => field.kind === "toggle" ? [[field.key, ""]] : [])) };
    for (const [name, value] of new FormData(form).entries()) {
      if (typeof value !== "string") continue;
      // Tam değer seçimi API'de en az = en fazla olarak uygulanır.
      if (name.startsWith("exact")) { values[`min${name.slice(5)}`] = value; values[`max${name.slice(5)}`] = value; } else values[name] = value;
    }
    onApply(values);
  }}>
    <div className={styles.filterHeader}>
      <h2><CatalogIcon name="filter" />{t("catalog.filters")}</h2>
      <button type="button" className={styles.clear} onClick={() => onApply({ ...clearValues, page: "0" })}>{t("visual.clearFilters")}</button>
    </div>
    <Field label={t("catalog.brand")}>
      <Select key={filterOptions.loading ? "loading" : "ready"} name="brandId" size="sm" defaultValue={search.get("brandId") ?? ""} disabled={filterOptions.loading}
        options={selectOptions("brandId", options.brandId ?? [], search.get("brandId") ?? "", locale, t("catalog.anyValue"))} />
    </Field>
    {unavailable ? <p className={styles.brandNotice}>{t("catalog.optionsUnavailable")} <button type="button" onClick={filterOptions.reload}>{t("common.reload")}</button></p> : null}
    {fields.length ? <h3 className={styles.filterGroup}>{t("catalog.technical")}</h3> : null}
    {fields.map((field) => {
      if (field.kind === "range") {
        const label = t(`fields.${category}.${specName(field)}`);
        return <fieldset key={field.key} className={styles.range}>
          <legend>{label}{unitSuffix[field.key] ? ` (${unitSuffix[field.key]})` : ""}</legend>
          <div className={styles.rangeFields}>{["min", "max"].map((prefix) => <Field key={prefix} label={t(`catalog.${prefix}`)}>
            <input className={styles.field} type="number" inputMode="numeric" min={1} max={maxValues[field.key]} name={`${prefix}${field.key}`} defaultValue={search.get(`${prefix}${field.key}`) ?? ""} />
          </Field>)}</div>
        </fieldset>;
      }
      if (field.kind === "toggle") {
        return <Checkbox key={field.key} name={field.key} value="true" defaultChecked={search.get(field.key) === "true"} label={t(`fields.${category}.${field.key}`)} />;
      }
      const name = specName(field);
      const selectName = field.kind === "exact" ? exactFieldName(field.key) : field.key;
      const low = field.kind === "exact" ? search.get(`min${field.key}`) : null;
      const current = field.kind === "exact" ? (low !== null && low === search.get(`max${field.key}`) ? low : "") : search.get(field.key) ?? "";
      return <Field key={selectName} label={listLabel(field)}>
        <Select key={filterOptions.loading ? "loading" : "ready"} name={selectName} size="sm" defaultValue={current} disabled={filterOptions.loading}
          options={selectOptions(name, options[name] ?? [], current, locale, t("catalog.anyValue"))} />
      </Field>;
    })}
    <p className={styles.filterHint}>{t(category ? "catalog.categoryHint" : "visual.chooseCategory")}</p>
    <button type="submit" className={styles.apply}>{t("catalog.apply")}</button>
  </form>;
}
