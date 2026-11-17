"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { usePerformanceMutation } from "../hooks/use-performance-mutation";
import { performanceApi } from "../services/performance-api";
import { DeletePerformanceData } from "./delete-performance-data";
import { control, panel, PerformanceError, PerformanceLoading } from "./performance-ui";

export function PerformancePrivacy({ accountId, onDataChanged }: { accountId: string; onDataChanged: () => void }) {
  const t = useTranslations("Performance");
  const format = useFormatter();
  const consent = usePerformanceQuery(`consent:${accountId}`, performanceApi.consent);
  const mutation = usePerformanceMutation();
  const [confirmGrant, setConfirmGrant] = useState(false);
  const [saved, setSaved] = useState(false);
  async function save(granted: boolean) {
    setSaved(false);
    const response = await mutation.run((signal) => performanceApi.setConsent(granted, signal));
    setConfirmGrant(false);
    if (response) { setSaved(true); consent.reload(); }
  }
  return <section aria-labelledby="performance-privacy" className="space-y-4">
    <h2 id="performance-privacy" className="font-display text-2xl font-semibold">{t("privacy")}</h2>
    <p className="max-w-3xl text-sm text-text-secondary">{t("privacyDescription")}</p>
    <Link href="/profile/settings" className={control}>{t("profilePrivacySettings")}</Link>
    <div className={`${panel} space-y-4`}>
      <h3 className="font-semibold">{t("syncConsent")}</h3>
      <p className="text-sm text-text-secondary">{t("syncConsentDescription")}</p>
      {consent.loading ? <PerformanceLoading /> : consent.error ? <PerformanceError error={consent.error} retry={consent.reload} /> : consent.data ? <>
        <p>{t(consent.data.granted ? "consentGranted" : "consentRevoked")}</p>
        {consent.data.grantedAt ? <p className="text-sm text-text-secondary">{t("consentGrantedAt", { value: format.dateTime(new Date(consent.data.grantedAt), { dateStyle: "medium", timeStyle: "short" }) })}</p> : null}
        {consent.data.revokedAt ? <p className="text-sm text-text-secondary">{t("consentRevokedAt", { value: format.dateTime(new Date(consent.data.revokedAt), { dateStyle: "medium", timeStyle: "short" }) })}</p> : null}
        <button className={control} disabled={mutation.pending || mutation.requiresReview} onClick={() => consent.data?.granted ? void save(false) : setConfirmGrant(true)}>{t(mutation.pending ? "saving" : consent.data.granted ? "revokeConsent" : "grantConsent")}</button>
      </> : null}
      {!confirmGrant ? <PerformanceError error={mutation.error} /> : null}
      {saved ? <p role="status" className="text-sm text-success">{t("consentSaved")}</p> : null}
      {mutation.requiresReview ? <><p className="text-sm">{t("uncertainMutation")}</p><button className={control} onClick={() => { setConfirmGrant(false); mutation.reset(); consent.reload(); }}>{t("reviewData")}</button></> : null}
    </div>
    <div className={`${panel} space-y-4`}>
      <h3 className="font-semibold">{t("storedData")}</h3>
      <p className="text-sm text-text-secondary">{t("storedDataDescription")}</p>
      <DeletePerformanceData onDeleted={onDataChanged} onReview={onDataChanged} />
    </div>
    {confirmGrant ? <ConfirmDialog title={t("grantConsent")} description={t("grantConsentDescription")} confirmLabel={t("confirmConsent")} cancelLabel={t("cancel")} busy={mutation.pending} onConfirm={() => { void save(true); }} onCancel={() => setConfirmGrant(false)}><PerformanceError error={mutation.error} /></ConfirmDialog> : null}
  </section>;
}
