"use client";

import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useRemote, type Remote } from "../hooks/use-remote";
import { getPerformanceSummary } from "../services/activity-api";
import type { ComputerType, HardwareSummary, PerformanceSummary } from "../types/activity.types";
import { ProfileSection, RemoteBody, sectionLinkClass, StatBox } from "./remote-section";

const computerTypes: readonly ComputerType[] = ["DESKTOP", "LAPTOP", "OTHER"];

/**
 * Donanım sekmesi: kayıtlı bilgisayarların özeti (Hardware) ve oyun performansı özeti (Performance, yalnız sahibine açık).
 * Kapasite yalnız kayıtlı değerlerin toplamıdır; eksikler ayrıca belirtilir, tahmin yapılmaz.
 */
export function ProfileHardware({ hardware }: { hardware: Remote<HardwareSummary> }) {
  const t = useTranslations("Profile.hardware");
  const performance = useRemote("performance", getPerformanceSummary);

  return (
    <div className="flex flex-col gap-6">
      <ProfileSection
        title={t("title")}
        action={
          <Link href="/profile/computers" className={sectionLinkClass}>
            {t("open")}
          </Link>
        }
      >
        <RemoteBody remote={hardware}>{(summary) => <ComputersSummary summary={summary} />}</RemoteBody>
      </ProfileSection>
      <ProfileSection title={t("performance.title")}>
        <RemoteBody remote={performance}>{(summary) => <PerformanceBlock summary={summary} />}</RemoteBody>
      </ProfileSection>
    </div>
  );
}

function ComputersSummary({ summary }: { summary: HardwareSummary }) {
  const t = useTranslations("Profile.hardware");
  const format = useFormatter();

  if (summary.computers === 0) {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-sm text-text-secondary">{t("empty")}</p>
        <Link href="/profile/computers" className={sectionLinkClass}>
          {t("add")}
        </Link>
      </div>
    );
  }

  const breakdown = computerTypes
    .filter((type) => (summary.byType[type] ?? 0) > 0)
    .map((type) => t("typeCount", { type: t(`types.${type}`), count: summary.byType[type] ?? 0 }))
    .join(" · ");
  const capacityMissing = summary.ramUnitsWithoutCapacity > 0 || summary.storageUnitsWithoutCapacity > 0;

  return (
    <>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatBox label={t("computers")} value={format.number(summary.computers)} />
        <StatBox label={t("components")} value={format.number(summary.componentUnits)} />
        <StatBox label={t("ram")} value={summary.ramGb > 0 ? t("gb", { value: format.number(summary.ramGb) }) : t("noValue")} />
        <StatBox
          label={t("storage")}
          value={summary.storageGb > 0 ? t("gb", { value: format.number(summary.storageGb) }) : t("noValue")}
        />
      </dl>
      {breakdown ? <p className="mt-4 text-sm text-text-secondary">{breakdown}</p> : null}
      {capacityMissing ? <p className="mt-2 text-xs text-text-secondary">{t("capacityNote")}</p> : null}
    </>
  );
}

function PerformanceBlock({ summary }: { summary: PerformanceSummary }) {
  const t = useTranslations("Profile.hardware.performance");
  const format = useFormatter();
  const number = (value: number | null, unit: string | null): string =>
    value === null ? "—" : `${format.number(value, { maximumFractionDigits: 1 })}${unit ? ` ${unit}` : ""}`;

  if (summary.sessions === 0) {
    return <p className="text-sm text-text-secondary">{t("empty")}</p>;
  }
  return (
    <>
      <p className="text-sm text-text-secondary">
        {t("totals", { sessions: format.number(summary.sessions), samples: format.number(summary.samples) })}
      </p>
      {summary.metrics.length > 0 ? (
        <ul className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
          {summary.metrics.map((metric) => (
            <li key={metric.metric} className="rounded-control border border-border bg-surface-elevated p-4">
              <p className="text-sm font-semibold text-text-primary">{metric.metric}</p>
              <dl className="mt-2 grid grid-cols-3 gap-2 text-xs">
                <div>
                  <dt className="text-text-secondary">{t("average")}</dt>
                  <dd className="mt-0.5 font-semibold text-text-primary">{number(metric.average, metric.unit)}</dd>
                </div>
                <div>
                  <dt className="text-text-secondary">{t("min")}</dt>
                  <dd className="mt-0.5 font-semibold text-text-primary">{number(metric.min, metric.unit)}</dd>
                </div>
                <div>
                  <dt className="text-text-secondary">{t("max")}</dt>
                  <dd className="mt-0.5 font-semibold text-text-primary">{number(metric.max, metric.unit)}</dd>
                </div>
              </dl>
              <p className="mt-2 text-xs text-text-secondary">{t("sessions", { count: metric.sessions })}</p>
            </li>
          ))}
        </ul>
      ) : null}
    </>
  );
}
