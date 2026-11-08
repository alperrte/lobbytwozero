"use client";

import Image from "next/image";
import type { Ref } from "react";
import { useTranslations } from "next-intl";
import { safeTextPattern } from "../schemas/hardware";
import { productName } from "../schemas/spec-profile";
import { productVisual } from "../schemas/product-visual";
import { builderSlots, isDraftDirty, maxRamQuantity, workshopFallbackPath, type WorkshopDraft, type WorkshopSlot } from "../schemas/workshop";
import { caseColors } from "../types/hardware";
import { button, input, panel, primaryButton, Field, HardwareError, HardwareSuccess } from "./hardware-ui";

export type WorkshopBuilderProps = {
  mode: "create" | "edit";
  draft: WorkshopDraft;
  initial: WorkshopDraft;
  onChange: (draft: WorkshopDraft) => void;
  onPick: (slot: WorkshopSlot) => void;
  onSave: () => void;
  onReset: () => void;
  pending: boolean;
  error: unknown;
  saved: boolean;
  nameRef?: Ref<HTMLInputElement>;
};

/** Atölye kurulum formu: ad, beş parça (katalogdan) ve kasa rengi. Değişiklikler sahnede anında önizlenir; kayıt tek düğmedir. */
export function WorkshopBuilder({ mode, draft, initial, onChange, onPick, onSave, onReset, pending, error, saved, nameRef }: WorkshopBuilderProps) {
  const t = useTranslations("Hardware");
  const dirty = isDraftDirty(draft, initial);
  const ram = draft.slots.RAM;
  return <section aria-labelledby="workshop-builder-title" className={`${panel} min-w-0 p-5`} data-testid="workshop-builder">
    <h2 id="workshop-builder-title" className="font-display text-lg font-semibold">{t(mode === "create" ? "workshop.builder.createTitle" : "workshop.builder.editTitle")}</h2>
    <p className="mt-1 text-sm leading-6 text-text-secondary">{t("workshop.builder.description")}</p>
    <form className="mt-5 space-y-5" onSubmit={(event) => { event.preventDefault(); if (event.currentTarget.reportValidity()) onSave(); }}>
      <Field label={t("profiles.name")}>
        <input ref={nameRef} required name="name" maxLength={100} pattern={safeTextPattern} value={draft.name} className={input} autoComplete="off"
          onChange={(event) => { event.currentTarget.setCustomValidity(event.currentTarget.value.trim() ? "" : t("common.required")); onChange({ ...draft, name: event.target.value }); }} />
      </Field>

      <fieldset className="min-w-0">
        <legend className="mb-2 font-display text-sm font-semibold">{t("workshop.builder.parts")}</legend>
        <ul className="divide-y divide-border rounded-panel border border-border">
          {builderSlots.map((slot) => {
            const part = draft.slots[slot];
            const visual = part ? productVisual(part.product) : undefined;
            const vendor = slot === "CPU" ? visual?.cpuVendor : slot === "GPU" ? visual?.vendor : undefined;
            return <li key={slot} className="flex min-h-16 items-center gap-3 px-3 py-2.5" data-testid={`workshop-slot-${slot}`}>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-text-secondary">{t(`categories.${slot}`)}{vendor ? <span className="ms-1.5 text-brand-text">{vendor}</span> : null}</p>
                <p className={`mt-0.5 break-words text-sm ${part ? "font-medium" : "text-text-secondary"}`}>{part ? productName(part.product) : t("workshop.builder.notSelected")}</p>
                {part && !part.productId ? <p className="text-xs text-text-secondary">{t("workshop.builder.manual")}</p> : null}
              </div>
              <button type="button" className={`${button} shrink-0 px-3`} onClick={() => onPick(slot)} aria-label={t(part ? "workshop.builder.changeLabel" : "workshop.builder.selectLabel", { category: t(`categories.${slot}`) })}>
                {t(part ? "workshop.builder.change" : "workshop.builder.select")}
              </button>
            </li>;
          })}
        </ul>
      </fieldset>

      {ram ? <Field label={t("workshop.builder.ramQuantity")} hint={t("workshop.builder.ramHint")}>
        <select className={input} value={ram.quantity} onChange={(event) => onChange({ ...draft, slots: { ...draft.slots, RAM: { ...ram, quantity: Number(event.target.value) } } })}>
          {Array.from({ length: maxRamQuantity }, (_, index) => index + 1).map((count) => <option key={count} value={count}>{t("components.units", { count })}</option>)}
        </select>
      </Field> : null}

      <fieldset className="min-w-0">
        <legend className="mb-2 font-display text-sm font-semibold">{t("workshop.caseColor")}</legend>
        <div className="workshopColors">
          {caseColors.map((color) => <label key={color} className="workshopColor">
            <input type="radio" name="caseColor" value={color} checked={draft.caseColor === color} onChange={() => onChange({ ...draft, caseColor: color })} className="sr-only" />
            <Image src={workshopFallbackPath(color)} alt="" width={120} height={90} sizes="6rem" />
            <span>{t(`workshop.colors.${color}`)}</span>
          </label>)}
        </div>
      </fieldset>

      <HardwareError error={error} />
      {saved && !dirty ? <HardwareSuccess>{t("workshop.builder.saved")}</HardwareSuccess> : null}
      {dirty ? <p className="text-xs text-text-secondary" role="status">{t("workshop.builder.dirty")}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button className={`${primaryButton} flex-1`} disabled={pending || !dirty || !draft.name.trim()}>
          {t(pending ? "common.saving" : mode === "create" ? "workshop.builder.create" : "workshop.builder.save")}
        </button>
        {dirty && mode === "edit" ? <button type="button" className={button} disabled={pending} onClick={onReset}>{t("workshop.builder.reset")}</button> : null}
      </div>
    </form>
  </section>;
}
