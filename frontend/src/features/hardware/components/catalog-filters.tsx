"use client";
import { useLocale, useTranslations } from "next-intl";
import type { Category, FilterOption, FilterOptions } from "../types/hardware";
import { categoryFilters, filterOptionLabel, multiQueryKeys, specName, technicalQueryKeys, type FilterField } from "../schemas/spec-profile";
import { Checkbox } from "@/shared/components/ui/checkbox";
import { MultiSelect } from "@/shared/components/ui/multi-select";
import type { SelectOption } from "@/shared/components/ui/select";
import { Field } from "./hardware-ui";
import { CatalogIcon } from "./catalog-icons";
import styles from "./catalog-styles";

const maxValues: Record<string, number> = { ClockMhz: 20000, PowerWatts: 10000 };
const unitSuffix: Record<string, string> = { ClockMhz: "MHz", PowerWatts: "W" };

export type FilterOptionsState = { data?: FilterOptions; loading: boolean; error?: unknown; reload: () => void };
/** Dizi değer çoklu seçimdir (tekrarlanan parametre); boş dizi/metin parametreyi temizler. */
export type FilterValues = Record<string, string | string[]>;

/** Seçili değer seçenekler arasında yoksa (eski bağlantı) kaybolmasın diye listenin başına eklenir; marka kimliği hariç. */
function selectOptions(name: string, options: FilterOption[], current: string[], locale: string): SelectOption[] {
  const legacy = name === "brandId" ? [] : current.filter((value) => !options.some((option) => option.value === value)).map((value) => ({ value, label: value }));
  return [...legacy, ...options.map((option) => ({ value: option.value, label: filterOptionLabel(name, option, locale) }))];
}

/**
 * Yalnız API'nin gerçekten desteklediği ve seçili kategorinin verisinde dolu olan filtreler sunulur.
 * Liste seçenekleri katalogdaki kayıtlı değerlerden gelir (`filterOptions`) ve hepsi çoklu seçimdir;
 * kategori seçilmediyse yalnız marka seçilebilir. Kategori sekmeleri filtre formunun dışındadır.
 */
export function CatalogFilters({ category, search, filterOptions, onApply }: { category?: Category; search: URLSearchParams; filterOptions: FilterOptionsState; onApply: (values: FilterValues) => void }) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const fields: FilterField[] = category ? categoryFilters[category] ?? [] : [];
  const rangeKeys = fields.flatMap((field) => field.kind === "range" ? [field.key] : []);
  const multiNames = ["brandId", ...fields.flatMap((field) => field.kind === "multi" ? [field.key] : [])];
  const clearValues = Object.fromEntries(["brand", ...new Set<string>([...multiQueryKeys, ...technicalQueryKeys])].map((key) => [key, ""]));
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
    // İşaretsiz onay kutusu ve boş çoklu seçim forma girmez; eski değer URL'de kalmasın diye açıkça boşaltılır.
    const data = new FormData(form);
    const values: FilterValues = { page: "0", ...Object.fromEntries(fields.flatMap((field) => field.kind === "toggle" ? [[field.key, ""]] : [])) };
    for (const name of multiNames) values[name] = data.getAll(name).filter((value): value is string => typeof value === "string" && value !== "");
    for (const [name, value] of data.entries()) {
      if (typeof value === "string" && !multiNames.includes(name)) values[name] = value;
    }
    onApply(values);
  }}>
    <div className={styles.filterHeader}>
      <h2><CatalogIcon name="filter" />{t("catalog.filters")}</h2>
      <button type="button" className={styles.clear} onClick={() => onApply({ ...clearValues, page: "0" })}>{t("visual.clearFilters")}</button>
    </div>
    <Field label={t("catalog.brand")}>
      <MultiSelect key={filterOptions.loading ? "loading" : "ready"} name="brandId" variant="oval" defaultValue={search.getAll("brandId")} disabled={filterOptions.loading} placeholder={t("catalog.anyValue")}
        options={selectOptions("brandId", options.brandId ?? [], search.getAll("brandId"), locale)} />
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
      const current = search.getAll(field.key);
      return <Field key={field.key} label={listLabel(field)}>
        <MultiSelect key={filterOptions.loading ? "loading" : "ready"} name={field.key} variant="oval" defaultValue={current} disabled={filterOptions.loading} placeholder={t("catalog.anyValue")}
          options={selectOptions(name, options[name] ?? [], current, locale)} />
      </Field>;
    })}
    {category ? null : <p className={styles.filterHint}>{t("visual.chooseCategory")}</p>}
    <button type="submit" className={styles.apply}>{t("catalog.apply")}</button>
  </form>;
}
