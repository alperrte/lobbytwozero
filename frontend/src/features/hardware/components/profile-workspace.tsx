"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { hardwareApi } from "../services/hardware-api";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import type { Component, Profile } from "../types/hardware";
import { ComponentComparison } from "./component-comparison";
import { ProfileForm } from "./profile-form";
import { ComponentForm } from "./component-form";
import { PrivacyForm } from "./privacy-form";
import { SpecificationTable } from "./specification-table";
import { button, primaryButton, panel, HardwareDialog, HardwareEmpty, HardwareError, HardwareLoading, HardwarePagination, HardwareSuccess } from "./hardware-ui";

export function ProfileWorkspace({ profileId, onChanged, onDeleted }: { profileId: string; onChanged: () => void; onDeleted: () => void }) {
  const t = useTranslations("Hardware");
  const [page, setPage] = useState(0);
  const detail = useHardwareQuery(`profile:${profileId}:${page}`, (signal) => hardwareApi.profile(profileId, page, signal));
  const [editing, setEditing] = useState<Profile>();
  const [component, setComponent] = useState<{ value?: Component; profileVersion: number }>();
  const [privacy, setPrivacy] = useState(false);
  const [deleting, setDeleting] = useState<Profile | Component>();
  const [deleteVersion, setDeleteVersion] = useState(0);
  const mutation = useHardwareMutation(() => { detail.reload(); onChanged(); });
  function refresh() { detail.reload(); onChanged(); }
  return <section className={`${panel} min-w-0 space-y-5 p-5 sm:p-6`} aria-label={t("profiles.workspace")}>


    {detail.loading ? <HardwareLoading /> : null}
    <HardwareError error={detail.error} retry={detail.reload} />
    {detail.data ? <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-brand-text">{t(`devices.${detail.data.profile.deviceType}`)}</p>
          <h2 className="mt-1 break-words font-display text-2xl font-semibold">{detail.data.profile.name}</h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className={button} onClick={() => setEditing(detail.data!.profile)}>{t("common.edit")}</button>
          <button className={button} onClick={() => setPrivacy(true)}>{t("privacy.title")}</button>
          <button className={button} onClick={() => { setDeleting(detail.data!.profile); setDeleteVersion(detail.data!.profile.version); }}>{t("common.delete")}</button>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
        <h3 className="font-display text-lg font-semibold">{t("components.title", { count: detail.data.totalComponents })}</h3>
        <button className={primaryButton} onClick={() => setComponent({ profileVersion: detail.data!.profile.version })}>{t("components.add")}</button>
      </div>
      {detail.data.components.length === 0 ? <HardwareEmpty title={t("components.emptyTitle")} description={t("components.emptyDescription")} /> : null}
      <div className="space-y-3">{detail.data.components.map((item) => <article key={item.id} className="rounded-control border border-border p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs text-brand-text">
              {t(`categories.${item.category}`)} · {t(item.source === "DESKTOP_SYNC" ? "components.synced" : "components.manual")}
            </p>
            <h4 className="mt-1 break-words font-display text-lg font-semibold">{item.modelName}</h4>
            <p className="mt-1 text-sm text-text-secondary">
              {item.brandName ? `${item.brandName} · ` : ""}
              {t("components.units", { count: item.quantity })}
            </p>
          </div>
          <div className="flex gap-2">
            <button className={button} onClick={() => setComponent({ value: item, profileVersion: detail.data!.profile.version })}>{t("common.edit")}</button>
            <button className={button} onClick={() => { setDeleting(item); setDeleteVersion(detail.data!.profile.version); }}>{t("common.delete")}</button>
          </div>
        </div>
        <details className="mt-3">
          <summary className="min-h-11 cursor-pointer py-2 text-sm text-text-secondary">{t("detail.specifications")}</summary>
          <SpecificationTable specifications={item.specifications} />
        </details>
      </article>)}</div>
      <ComponentComparison key={detail.data.profile.version} profileId={profileId} components={detail.data.components} />
      <HardwarePagination page={page} totalPages={detail.data.totalPages} onPage={setPage} />
    </> : null}

    {mutation.success ? <HardwareSuccess>{t("common.saved")}</HardwareSuccess> : null}

    {editing ? <ProfileForm profile={editing} onClose={() => setEditing(undefined)} onSaved={refresh} /> : null}

    {component ? <ComponentForm profileId={profileId} profileVersion={component.profileVersion} component={component.value} onClose={() => setComponent(undefined)} onSaved={refresh} /> : null}

    {privacy ? <PrivacyForm profileId={profileId} onClose={() => setPrivacy(false)} onSaved={refresh} /> : null}

    {deleting ? <HardwareDialog title={t("profiles.deleteTitle")} onClose={() => setDeleting(undefined)} busy={mutation.pending}>
      <p className="break-words font-medium">{"name" in deleting ? deleting.name : deleting.modelName}</p>
      <p className="text-sm leading-6 text-text-secondary">{t("name" in deleting ? "profiles.deleteDescription" : "components.deleteDescription")}</p>
      <HardwareError error={mutation.error} />
      <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview} onClick={async () => { await mutation.run(async () => { if ("name" in deleting) { await hardwareApi.deleteProfile({ ...deleting, version: deleteVersion }); setDeleting(undefined); onDeleted(); } else { await hardwareApi.deleteComponent(profileId, deleting.id, deleteVersion); setDeleting(undefined); } }); }}>{t("common.confirmDelete")}</button>
    </HardwareDialog> : null}

  </section>;
}
