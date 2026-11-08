"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { productName } from "../schemas/spec-profile";
import { productVisual } from "../schemas/product-visual";
import { builderSlots, slotComponents, type WorkshopDraft, type WorkshopSlot } from "../schemas/workshop";
import type { Component, Profile } from "../types/hardware";
import { HardwareProductVisual } from "./hardware-product-visual";
import { button, panel, HardwareError } from "./hardware-ui";
import { productSpecs } from "./workshop-part-picker";

export type WorkshopSummaryProps = {
  profile: Profile;
  components: Component[];
  /** Kayıtlı hâlin taslağı (ürün ayrıntıları yüklenmiş). */
  saved: WorkshopDraft;
  totalComponents: number;
  complete: boolean;
  onPick: (slot: WorkshopSlot) => void;
  onEdit: () => void;
  onReplay: () => void;
  onChanged: () => void;
  onDeleted: () => void;
};

const autoParts = ["aio", "fans", "rgb", "psu", "cables"] as const;

/** "Bilgisayar Bilgilerim": kayıtlı parçalar, kategori bilgisi ve parça başına değiştir/kaldır eylemleri. */
export function WorkshopSummary({ profile, components, saved, totalComponents, complete, onPick, onEdit, onReplay, onChanged, onDeleted }: WorkshopSummaryProps) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const [removing, setRemoving] = useState<Component>();
  const [deleting, setDeleting] = useState(false);
  const mutation = useHardwareMutation(onChanged);
  const bySlot = slotComponents(components);
  const extra = totalComponents - Object.keys(bySlot).length;
  const labels = { yes: t("visual.yes"), no: t("visual.no") };
  const color = t(`workshop.colors.${profile.caseColor}`);

  return <section aria-labelledby="workshop-summary-title" className={`${panel} min-w-0 p-5 sm:p-6`} data-testid="workshop-summary">
    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
      <div className="min-w-0">
        <h2 id="workshop-summary-title" className="font-display text-xl font-semibold">{t("workshop.summary.title")}</h2>
        <p className="mt-1 break-words text-sm text-text-secondary">{t("workshop.summary.subtitle", { name: profile.name, color })}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={button} onClick={onEdit}>{t("common.edit")}</button>
        <button type="button" className={button} onClick={onReplay} disabled={!complete}>{t("workshop.stage.replay")}</button>
        <button type="button" className={button} onClick={() => setDeleting(true)}>{t("workshop.summary.deleteComputer")}</button>
      </div>
    </div>

    <ul className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {builderSlots.map((slot) => {
        const part = saved.slots[slot];
        const component = bySlot[slot];
        const category = t(`categories.${slot}`);
        if (!part || !component) return <li key={slot} className="flex min-h-40 flex-col justify-between rounded-panel border border-dashed border-control-border p-4" data-testid={`workshop-info-${slot}`}>
          <div>
            <p className="text-xs font-semibold text-text-secondary">{category}</p>
            <p className="mt-1 text-sm text-text-secondary">{t("workshop.summary.missing")}</p>
          </div>
          <button type="button" className={`${button} mt-4 self-start`} onClick={() => onPick(slot)} aria-label={t("workshop.builder.selectLabel", { category })}>{t("workshop.builder.select")}</button>
        </li>;
        const visual = productVisual(part.product);
        const vendor = slot === "CPU" ? visual.cpuVendor : slot === "GPU" ? visual.vendor : undefined;
        const specs = productSpecs(part.product, locale, labels);
        return <li key={slot} className="flex min-h-40 flex-col rounded-panel border border-border p-4" data-testid={`workshop-info-${slot}`}>
          <div className="flex items-start gap-3">
            <HardwareProductVisual product={part.product} variant="compact" className="w-16 shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-brand-text">{category}</p>
              <h3 className="mt-0.5 break-words font-display font-semibold">{productName(part.product)}</h3>
              <p className="mt-0.5 text-xs text-text-secondary">{[visual.manufacturer ?? t("catalog.brandIndependent"), vendor && vendor !== visual.manufacturer ? vendor : undefined, t("components.units", { count: component.quantity })].filter(Boolean).join(" / ")}</p>
            </div>
          </div>
          {specs.length ? <dl className="mt-3 space-y-1 text-xs">{specs.map((spec) => <div key={spec.key} className="flex justify-between gap-3">
            <dt className="text-text-secondary">{t(`fields.${slot}.${spec.key}`)}</dt><dd className="text-end font-medium">{spec.value}</dd>
          </div>)}</dl> : null}
          {!component.productId ? <p className="mt-2 text-xs text-text-secondary">{t("workshop.builder.manual")}</p> : null}
          <div className="mt-auto flex gap-2 pt-4">
            <button type="button" className={`${button} px-3`} onClick={() => onPick(slot)} aria-label={t("workshop.builder.changeLabel", { category })}>{t("workshop.builder.change")}</button>
            <button type="button" className={`${button} px-3`} onClick={() => setRemoving(component)} aria-label={t("workshop.summary.removeLabel", { category })}>{t("workshop.summary.remove")}</button>
          </div>
        </li>;
      })}
    </ul>
    {extra > 0 ? <p className="mt-3 text-xs text-text-secondary">{t("workshop.summary.extra", { count: extra })}</p> : null}
    <HardwareError error={mutation.error} />

    <div className="mt-6 border-t border-border pt-5">
      <h3 className="font-display font-semibold">{t("workshop.summary.autoTitle")}</h3>
      <p className="mt-1 text-sm leading-6 text-text-secondary">{t("workshop.summary.autoNote")}</p>
      <ul className="mt-3 flex flex-wrap gap-2">{autoParts.map((key) => <li key={key} className="rounded-control border border-border bg-surface-elevated px-2.5 py-1 text-xs">{t(`workshop.auto.${key}`)}</li>)}</ul>
    </div>

    {removing ? <ConfirmDialog tone="danger" title={t("workshop.summary.removeTitle")} description={t("workshop.summary.removeDescription", { model: removing.modelName })}
      confirmLabel={t("workshop.summary.remove")} cancelLabel={t("common.cancel")} busy={mutation.pending} onCancel={() => setRemoving(undefined)}
      onConfirm={() => { void mutation.run(() => hardwareApi.deleteComponent(profile.id, removing.id, profile.version)).finally(() => setRemoving(undefined)); }} /> : null}
    {deleting ? <ConfirmDialog tone="danger" title={t("profiles.deleteTitle")} description={t("profiles.deleteDescription")}
      confirmLabel={t("common.confirmDelete")} cancelLabel={t("common.cancel")} busy={mutation.pending} onCancel={() => setDeleting(false)}
      onConfirm={() => { void mutation.run(async () => { await hardwareApi.deleteProfile(profile); return true; }).then((done) => { setDeleting(false); if (done) onDeleted(); }); }} /> : null}
  </section>;
}
