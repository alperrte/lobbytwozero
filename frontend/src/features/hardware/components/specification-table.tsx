"use client";
import { useTranslations } from "next-intl";
import { specKeys, type Specifications } from "../types/hardware";

export function SpecificationTable({ specifications }: { specifications: Specifications }) {
  const t = useTranslations("Hardware");
  return <dl className="divide-y divide-border">{specKeys.map((key) => <div key={key} className="grid grid-cols-2 gap-4 py-3 text-sm">
    <dt className="text-text-secondary">{t(`specs.${key}`)}</dt>
    <dd className="break-words font-medium">{specifications[key] ?? t("common.unknown")}</dd>
  </div>)}</dl>;
}
