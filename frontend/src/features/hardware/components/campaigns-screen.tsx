"use client";
import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { Icon } from "@/shared/components/ui/icons";
import { hardwareApi } from "../services/hardware-api";
import { safeExternalUrl, isUuid } from "../schemas/hardware";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import type { Campaign } from "../types/hardware";
import { button, panel, HardwareDialog, HardwareEmpty, HardwareError, HardwareHeading, HardwareLoading, HardwarePagination } from "./hardware-ui";

function CampaignFacts({ campaign }: { campaign: Campaign }) {
  const t = useTranslations("Hardware");
  const format = useFormatter();
  const url = safeExternalUrl(campaign.sourceUrl);
  return <div className="space-y-4">
    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
      <strong className="font-display text-2xl">{format.number(campaign.salePrice, { style: "currency", currency: campaign.currency })}</strong>
      <del className="text-sm text-text-secondary">{format.number(campaign.originalPrice, { style: "currency", currency: campaign.currency })}</del>
      <span className="text-sm font-semibold text-success">{t("campaigns.discount", { value: format.number(campaign.discountPercent, { maximumFractionDigits: 2 }) })}</span>
    </div>
    <dl className="space-y-2 text-xs leading-5 text-text-secondary">
      <div>
        <dt className="inline font-semibold">
          {t("campaigns.source")}: </dt>
        <dd className="inline break-words">{campaign.sourceName}</dd>
      </div>
      <div>
        <dt className="inline font-semibold">
          {t("campaigns.observed")}: </dt>
        <dd className="inline">{format.dateTime(new Date(campaign.observedAt), { dateStyle: "medium", timeStyle: "short" })}</dd>
      </div>
      <div>
        <dt className="inline font-semibold">
          {t("campaigns.validity")}: </dt>
        <dd className="inline">
          {format.dateTime(new Date(campaign.startsAt), { dateStyle: "medium", timeStyle: "short" })} — {format.dateTime(new Date(campaign.endsAt), { dateStyle: "medium", timeStyle: "short" })}
        </dd>
      </div>
    </dl>
    <div className="flex flex-wrap gap-2">
      <Link className={button} href={{ pathname: "/hardware/[productId]", params: { productId: campaign.productId } }}>{t("campaigns.product")}</Link>
      {url ? <a className={button} href={url} rel="noopener noreferrer" target="_blank">{t("campaigns.visitSource")}</a> : <span className="text-sm text-warning">{t("campaigns.unsafeUrl")}</span>}
    </div>
  </div>;
}
function CampaignDetail({ campaignId, onClose }: { campaignId: string; onClose: () => void }) {
  const t = useTranslations("Hardware");
  const result = useHardwareQuery(`campaign:${campaignId}`, (signal) => hardwareApi.campaign(campaignId, signal));
  return <HardwareDialog title={result.data?.title ?? t("campaigns.detail")} onClose={onClose}>
    {result.loading ? <HardwareLoading /> : null}
    <HardwareError error={result.error} retry={result.reload} />
    {result.data ? <CampaignFacts campaign={result.data} /> : null}
    <p className="text-sm leading-6 text-text-secondary">{t("campaigns.priceNotice")}</p>
  </HardwareDialog>;
}
export function CampaignsScreen() {
  const t = useTranslations("Hardware");
  const search = useSearchParams();
  const router = useRouter();
  const number = Number(search.get("page"));
  const page = Number.isInteger(number) && number >= 0 && number <= 10000 ? number : 0;
  const filter = search.get("productId");
  const productId = filter && isUuid(filter) ? filter : undefined;
  const result = useHardwareQuery(`campaigns:${page}:${productId ?? ""}`, (signal) => hardwareApi.campaigns(page, productId, signal));
  const [selected, setSelected] = useState<string>();
  return <>
    <HardwareHeading title={t("campaigns.title")} description={t("campaigns.description")} />
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_17rem]">
      <section className="min-w-0 space-y-4">
        {productId ? <button className={button} onClick={() => router.push("/hardware/campaigns")}>{t("campaigns.clearProduct")}</button> : null}
        {result.data ? <p className="text-sm text-text-secondary">{t("campaigns.results", { count: result.data.totalElements })}</p> : null}
        {result.loading ? <HardwareLoading /> : null}
        <HardwareError error={result.error} retry={result.reload} />
        {result.data?.items.length === 0 ? <HardwareEmpty title={t("campaigns.emptyTitle")} description={t("campaigns.emptyDescription")} /> : null}
        {result.data?.items.map((campaign) => <article className={`${panel} p-5 sm:p-6`} key={campaign.id}>
          <div className="mb-4 flex items-start gap-4">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-control border border-border bg-surface-elevated">
              <Icon name="tag" className="size-5 text-brand-text" />
            </span>
            <div className="min-w-0">
              <p className="text-xs text-text-secondary">{t("campaigns.editorRecord")}</p>
              <h2 className="mt-1 break-words font-display text-xl font-semibold">
                <button type="button" className="min-h-11 text-start hover:underline" onClick={() => setSelected(campaign.id)}>{campaign.title}</button>
              </h2>
            </div>
          </div>
          <CampaignFacts campaign={campaign} />
        </article>)}
        {result.data ? <HardwarePagination page={page} totalPages={result.data.totalPages} onPage={(next) => router.push({ pathname: "/hardware/campaigns", query: { page: String(next), ...(productId ? { productId } : {}) } }, { scroll: false })} /> : null}
      </section>
      <aside className="space-y-5">
        <section className={`${panel} p-5`}>
          <h2 className="font-display text-lg font-semibold">{t("campaigns.aboutTitle")}</h2>
          <p className="mt-3 text-sm leading-6 text-text-secondary">{t("campaigns.priceNotice")}</p>
        </section>
        <section className={`${panel} p-5`}>
          <h2 className="font-display text-lg font-semibold">{t("campaigns.communityTitle")}</h2>
          <p className="mt-3 text-sm leading-6 text-text-secondary">{t("campaigns.communityUnavailable")}</p>
        </section>
      </aside>
    </div>
    {selected ? <CampaignDetail campaignId={selected} onClose={() => setSelected(undefined)} /> : null}
  </>;
}
