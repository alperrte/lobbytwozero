"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { categories, specKeys, type Category, type Component, type ComponentInput, type Specifications } from "../types/hardware";
import { numericSpecs, safeTextPattern } from "../schemas/hardware";
import { hardwareApi, queryString } from "../services/hardware-api";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import { button, primaryButton, input, Field, HardwareDialog, HardwareError, HardwareLoading, HardwarePagination } from "./hardware-ui";
import { PsuResource } from "./psu-resource";

export function ComponentForm({ profileId, profileVersion, component, onClose, onSaved }: { profileId: string; profileVersion: number; component?: Component; onClose: () => void; onSaved: () => void }) {
  const t = useTranslations("Hardware");
  const [version] = useState(profileVersion);
  const [category, setCategory] = useState<Category>(component?.category ?? "CPU");
  const [manual, setManual] = useState(!component?.productId);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(component?.productId ?? "");
  const products = useHardwareQuery(`select:${category}:${search}:${page}`, (signal) => hardwareApi.products(queryString({ category, q: search, page, size: 10 }), signal), !manual);
  const selected = useHardwareQuery(`selection:${selectedId}`, (signal) => hardwareApi.product(selectedId, signal), !manual && Boolean(selectedId));
  const mutation = useHardwareMutation(onSaved);
  return <HardwareDialog title={t(component ? "components.edit" : "components.add")} onClose={onClose} busy={mutation.pending}>
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label={t("components.category")}>
        <select className={input} value={category} onChange={(event) => { setCategory(event.target.value as Category); setSelectedId(""); setPage(0); }}>{categories.map((value) => <option key={value} value={value}>{t(`categories.${value}`)}</option>)}</select>
      </Field>
      <Field label={t("components.entryMode")}>
        <select className={input} value={manual ? "manual" : "catalog"} onChange={(event) => setManual(event.target.value === "manual")}>
          <option value="manual">{t("components.manual")}</option>
          <option value="catalog">{t("components.catalog")}</option>
        </select>
      </Field>
    </div>
    {category === "PSU" ? <PsuResource /> : null}
    {!manual ? <div className="space-y-4">
      <form className="flex items-end gap-2" onSubmit={(event) => { event.preventDefault(); setSearch(String(new FormData(event.currentTarget).get("search"))); setPage(0); }}>
        <div className="min-w-0 flex-1">
          <Field label={t("catalog.search")}>
            <input name="search" className={input} maxLength={160} />
          </Field>
        </div>
        <button className={button}>{t("common.search")}</button>
      </form>
      {products.loading ? <HardwareLoading /> : null}
      <HardwareError error={products.error} retry={products.reload} />
      <fieldset className="space-y-2">
        <legend className="mb-2 text-sm font-medium">{t("components.selectModel")}</legend>
        {products.data?.items.map((product) => <label key={product.id} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-control border border-border p-3 text-sm">
          <input type="radio" name="productId" checked={selectedId === product.id} onChange={() => setSelectedId(product.id)} />
          <span className="break-words">{product.model}</span>
        </label>)}
      </fieldset>
      {products.data?.items.length === 0 ? <p className="text-sm text-text-secondary">{t("components.noModels")}</p> : null}
      {products.data ? <HardwarePagination page={page} totalPages={products.data.totalPages} onPage={setPage} /> : null}
      <HardwareError error={selected.error} retry={selected.reload} />
      {selected.data ? <p className="text-sm text-brand-text">{t("components.selected", { model: selected.data.model })}</p> : null}
    </div> : null}
    <form key={`${manual}:${category}:${selectedId}`} className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const specifications: Partial<Specifications> = {}; for (const key of specKeys) { const value = String(form.get(key) ?? "").trim(); if (value) Object.assign(specifications, { [key]: key in numericSpecs ? Number(value) : value }); } const body: ComponentInput = { profileVersion: version, category, quantity: Number(form.get("quantity")), ...(manual ? { modelName: String(form.get("modelName")).trim(), ...(form.get("brandName") ? { brandName: String(form.get("brandName")).trim() } : {}) } : { productId: selectedId, ...(form.get("brandId") ? { brandId: String(form.get("brandId")) } : {}) }), ...(Object.keys(specifications).length ? { specifications } : {}) }; const response = await mutation.run(() => hardwareApi.saveComponent(profileId, body, component?.id)); if (response) onClose(); }}>


      {manual ? <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("components.model")}>
          <input required name="modelName" maxLength={160} pattern={safeTextPattern} className={input} defaultValue={component?.modelName} />
        </Field>
        <Field label={t("components.brand")} hint={t("catalog.brandOptional")}>
          <input name="brandName" maxLength={100} pattern={safeTextPattern} className={input} defaultValue={component?.brandName ?? ""} />
        </Field>
      </div> : <Field label={t("components.brand")} hint={t("catalog.brandOptional")}>
        <select className={input} name="brandId" defaultValue={component?.productId === selectedId ? component?.brandId ?? "" : ""}>
          <option value="">{t("catalog.brandIndependent")}</option>
          {selected.data?.brands.map((brand) => <option value={brand.id} key={brand.id}>{brand.name}</option>)}
        </select>
      </Field>}

      <Field label={t("components.quantity")}>
        <input required className={input} type="number" name="quantity" min={1} max={128} defaultValue={component?.quantity ?? 1} />
      </Field>
      <fieldset>
        <legend className="mb-3 text-sm font-semibold">{t("detail.specifications")}</legend>
        <p className="mb-4 text-xs leading-5 text-text-secondary">{t("components.specsHint")}</p>
        <div className="grid gap-4 sm:grid-cols-2">{specKeys.map((key) => <Field key={key} label={t(`specs.${key}`)}>
          <input name={key} className={input} type={key in numericSpecs ? "number" : "text"} min={key in numericSpecs ? 1 : undefined} max={key in numericSpecs ? numericSpecs[key as keyof typeof numericSpecs] : undefined} maxLength={32} pattern={key in numericSpecs ? undefined : safeTextPattern} defaultValue={component?.category === category && (manual || component.productId === selectedId) ? component.specifications[key] ?? "" : ""} />
        </Field>)}</div>
      </fieldset>
      <HardwareError error={mutation.error} />
      <div className="flex flex-wrap justify-end gap-2">
        <button type="button" className={button} disabled={mutation.pending} onClick={onClose}>{t("common.cancel")}</button>
        <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview || (!manual && (!selected.data || selected.loading))}>{t(mutation.pending ? "common.saving" : "common.save")}</button>
      </div>
    </form>
  </HardwareDialog>;
}
