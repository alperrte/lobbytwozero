"use client";

import { useId, type ReactNode } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import type { CheckStatus, HardwareAssessment, LevelAssessment } from "../../types/recommendation";
import { LimitationsNotice } from "../recommendation-ui";

type Tone = "ok" | "warn" | "neutral";

const toneClass: Record<Tone, string> = {
  ok: "border-success/50 text-success",
  warn: "border-warning/60 text-warning",
  neutral: "border-border text-text-secondary"
};
const toneIcon: Record<Tone, IconName> = { ok: "check", warn: "info", neutral: "info" };

/** Durum etiketi. Renk tek başına anlam taşımaz: her etikette ikon ve metin vardır. */
export function StatusChip({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={cn("inline-flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold", toneClass[tone])}>
    <Icon name={toneIcon[tone]} className="size-3.5 shrink-0" />
    {children}
  </span>;
}

const checkTone = (status: CheckStatus): Tone => (status === "MEETS" ? "ok" : status === "BELOW" ? "warn" : "neutral");

/** Gerekçe kodu → açıklama. Bilinmeyen kod uydurma metin yerine genel "ayrıntı yok" ile gösterilir. */
function useReason() {
  const t = useTranslations("Recommendation.reasons");
  return (code: string | null | undefined) => (code && t.has(code) ? t(code) : t("UNKNOWN_REASON"));
}

function Block({ title, children, aside }: { title: string; children: ReactNode; aside?: ReactNode }) {
  const id = useId();
  return <section aria-labelledby={id} className="rounded-panel border border-border p-4">
    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h3 id={id} className="font-display text-base font-semibold">{title}</h3>
      {aside}
    </div>
    {children}
  </section>;
}

function LevelBlock({ level }: { level: LevelAssessment }) {
  const t = useTranslations("Recommendation.assessment");
  const reason = useReason();
  return <Block title={t(`level.${level.level}`)} aside={<StatusChip tone={checkTone(level.overallStatus)}>{t(`check.${level.overallStatus}`)}</StatusChip>}>
    <ul className="divide-y divide-border">
      {level.checks.map((check, index) => <li key={`${check.field}-${index}`} className="py-2.5 first:pt-0 last:pb-0">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-sm font-semibold">{t.has(`fields.${check.field}`) ? t(`fields.${check.field}`) : check.field}</span>
          <StatusChip tone={checkTone(check.status)}>{t(`check.${check.status}`)}</StatusChip>
        </div>
        {check.actual || check.required
          ? <dl className="mt-1.5 grid gap-x-4 gap-y-0.5 text-xs sm:grid-cols-2">
            <div className="min-w-0"><dt className="inline text-text-secondary">{t("yours")} </dt><dd className="inline [overflow-wrap:anywhere]">{check.actual ?? t("notDeclared")}</dd></div>
            <div className="min-w-0"><dt className="inline text-text-secondary">{t("required")} </dt><dd className="inline [overflow-wrap:anywhere]">{check.required ?? t("notDeclared")}</dd></div>
          </dl>
          : null}
        <p className="mt-1 text-xs leading-5 text-text-secondary">{reason(check.reason)}</p>
      </li>)}
    </ul>
  </Block>;
}

/**
 * Donanım değerlendirmesi. Backend kesin "uyumlu" sonucu vermez: uygunluk yalnız `BELOW_MINIMUM` veya `UNKNOWN`'dır ve
 * arayüz `UNKNOWN`'ı asla "uyumlu" diye göstermez. FPS yalnız `ESTIMATED` iken ve "geçmiş ölçümlerden tahmin" etiketiyle görünür.
 */
export function AssessmentResult({ assessment }: { assessment: HardwareAssessment }) {
  const t = useTranslations("Recommendation.assessment");
  const reason = useReason();
  const format = useFormatter();
  const { minimum, fps, graphics, componentCompatibility: compat } = assessment;
  const below = assessment.suitability === "BELOW_MINIMUM";
  const computeMet = !below && minimum.computeStatus === "MEETS";
  const summaryKey = below ? "below" : computeMet ? "computeMet" : "unknown";
  const limitations = [...assessment.limitations, ...compat.limitations, ...(fps.evidence?.limitations ?? [])];

  return <div className="space-y-4">
    <div role="status" className={cn("rounded-panel border border-s-2 p-4", below ? "border-warning/60 border-s-warning" : "border-border border-s-text-secondary")}>
      <p className="font-display text-lg font-semibold">{t(`summary.${summaryKey}.title`)}</p>
      <p className="mt-1 text-sm leading-6 text-text-secondary">{t(`summary.${summaryKey}.description`)}</p>
    </div>

    <LevelBlock level={assessment.minimum} />
    <LevelBlock level={assessment.recommended} />

    <Block title={t("fps.title")} aside={<StatusChip tone="neutral">{fps.status === "ESTIMATED" ? t("fps.estimatedChip") : t("fps.noEstimateChip")}</StatusChip>}>
      {fps.status === "ESTIMATED" && fps.estimatedLowerFps !== null && fps.estimatedUpperFps !== null
        ? <div className="space-y-1.5 text-sm">
          <p><span className="font-display text-2xl font-bold">{t("fps.range", { lower: fps.estimatedLowerFps, upper: fps.estimatedUpperFps })}</span></p>
          <p className="leading-6 text-text-secondary">{t("fps.estimateNote", { sessions: fps.observedSessionCount, samples: fps.observedSampleCount })}</p>
          <p className="text-text-secondary">{t("fps.confidence", { level: t(`fps.confidenceLevel.${fps.confidence}`) })}</p>
          {fps.lastObservedAt ? <p className="text-xs text-text-secondary">{t("fps.lastObserved", { date: format.dateTime(new Date(fps.lastObservedAt), { dateStyle: "medium" }) })}</p> : null}
        </div>
        : <p className="text-sm leading-6 text-text-secondary">{reason(fps.reason)}</p>}
    </Block>

    <Block title={t("graphics.title")}>
      {graphics.status === "CONDITIONAL" && graphics.suggestedPreset
        ? <p className="text-sm leading-6">{t("graphics.conditional", { preset: t.has(`preset.${graphics.suggestedPreset}`) ? t(`preset.${graphics.suggestedPreset}`) : graphics.suggestedPreset })}</p>
        : null}
      <p className="text-sm leading-6 text-text-secondary">{reason(graphics.reason)}</p>
    </Block>

    {assessment.upgrades.length
      ? <Block title={t("upgrades.title")}>
        <ul className="space-y-2 text-sm">
          {assessment.upgrades.map((upgrade, index) => <li key={`${upgrade.code}-${index}`} className="leading-6">
            <span className="font-semibold">{t.has(`upgrades.${upgrade.code}`) ? t(`upgrades.${upgrade.code}`) : upgrade.code}</span>
            {upgrade.required ? <span className="text-text-secondary"> {t("upgrades.target", { level: t(`level.${upgrade.requirementLevel === "RECOMMENDED" ? "RECOMMENDED" : "MINIMUM"}`), required: upgrade.required })}</span> : null}
          </li>)}
        </ul>
      </Block>
      : null}

    <Block title={t("components.title")} aside={<StatusChip tone={compat.status === "INCOMPATIBLE" ? "warn" : "neutral"}>{t(`components.status.${compat.status}`)}</StatusChip>}>
      <ul className="space-y-2">
        {compat.checks.map((check, index) => <li key={`${check.field}-${index}`} className="text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-semibold">{t.has(`fields.${check.field}`) ? t(`fields.${check.field}`) : check.field}</span>
            <StatusChip tone={check.status === "INCOMPATIBLE" ? "warn" : "neutral"}>{t(`components.check.${check.status}`)}</StatusChip>
          </div>
          <p className="mt-1 text-xs leading-5 text-text-secondary">{reason(check.reason)}</p>
        </li>)}
      </ul>
    </Block>

    <LimitationsNotice codes={limitations} />
  </div>;
}
