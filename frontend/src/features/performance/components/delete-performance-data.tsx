"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { usePerformanceMutation } from "../hooks/use-performance-mutation";
import { performanceApi } from "../services/performance-api";
import type { PerformanceSession } from "../types/performance";
import { control, PerformanceError } from "./performance-ui";

export function DeletePerformanceData({ session, onDeleted, onReview }: { session?: Pick<PerformanceSession, "id" | "gameTitle">; onDeleted: () => void; onReview: () => void }) {
  const t = useTranslations("Performance");
  const [open, setOpen] = useState(false);
  const [success, setSuccess] = useState(false);
  const mutation = usePerformanceMutation();
  const label = session ? t("deleteSession") : t("deleteAll");
  async function confirm() {
    const result = await mutation.run(async (signal) => {
      if (session) await performanceApi.deleteSession(session.id, signal);
      else await performanceApi.deleteAllSessions(signal);
    });
    if (result) { setOpen(false); setSuccess(true); onDeleted(); }
  }
  return <div className="space-y-3">
    <button className={control} onClick={() => { setSuccess(false); mutation.reset(); setOpen(true); }}>{label}</button>
    {success ? <p role="status" className="text-sm text-success">{t("deleted")}</p> : null}
    {open ? <ConfirmDialog title={label} description={session ? t("deleteSessionDescription", { game: session.gameTitle }) : t("deleteAllDescription")} confirmLabel={t(mutation.requiresReview ? "reviewRequired" : mutation.pending ? "deleting" : "confirmDelete")} cancelLabel={t("cancel")} tone="danger" busy={mutation.pending || mutation.requiresReview} onConfirm={() => { void confirm(); }} onCancel={() => setOpen(false)}>
      <PerformanceError error={mutation.error} />
      {mutation.requiresReview ? <div className="space-y-3"><p className="text-sm">{t("uncertainMutation")}</p><button className={control} onClick={() => { setOpen(false); mutation.reset(); onReview(); }}>{t("reviewData")}</button></div> : null}
    </ConfirmDialog> : null}
  </div>;
}
