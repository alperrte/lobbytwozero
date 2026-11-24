"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { GameCover } from "@/features/game/components/game-cover";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Icon } from "@/shared/components/ui/icons";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useListingVisuals, useMyListings } from "../hooks/use-lfp-data";
import { lfpApi } from "../services/lfp-api";
import { lfpErrorKey } from "../services/lfp-errors";
import type { Listing, ListingStatus } from "../types/lfp.types";
import { ListingCard } from "./listing-card";
import { LfpSessionGate, LfpShell, StatusBadge, useListingTime, usePlatformLabel } from "./listing-parts";

type StatusTab = "ALL" | ListingStatus;
const STATUS_TABS: readonly StatusTab[] = ["ALL", "OPEN", "CLOSED", "EXPIRED"];

export function MyListingsView() {
  return <LfpSessionGate>{(meId) => <MyListingsScreen meId={meId} />}</LfpSessionGate>;
}

/**
 * İlanlarım (11.03; LFP-12..15): kullanıcının tüm ilanları ve durumları (aktif, kapalı, süresi dolmuş). Açık ilan düzenlenir ya da
 * onayla kapatılır; kapalı/süresi dolmuş ilan yalnız görüntülenir (yeniden açılmaz).
 */
function MyListingsScreen({ meId }: Readonly<{ meId: string }>) {
  const t = useTranslations("Lfp");
  const mine = useMyListings();
  const visuals = useListingVisuals(mine.items);
  const platform = usePlatformLabel();
  const time = useListingTime();
  const [tab, setTab] = useState<StatusTab>("ALL");
  const [closing, setClosing] = useState<Listing | null>(null);
  const [detail, setDetail] = useState<Listing | null>(null);
  const [closeBusy, setCloseBusy] = useState(false);
  const [closeError, setCloseError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const items = tab === "ALL" ? mine.items : mine.items.filter((item) => item.status === tab);
  const countOf = (value: StatusTab) => (value === "ALL" ? mine.items.length : mine.counts[value]);

  async function confirmClose() {
    if (!closing) return;
    setCloseBusy(true);
    setCloseError(null);
    try {
      const closed = await lfpApi.close(closing.id);
      mine.replace(closed);
      setNotice(t("mine.closed", { game: closing.gameTitle }));
      setClosing(null);
    } catch (error) {
      setCloseError(t(`errors.${lfpErrorKey(error)}`));
    } finally {
      setCloseBusy(false);
    }
  }

  const summary = (
    <ul aria-label={t("mine.summary")} className="flex flex-wrap gap-2">
      {(["OPEN", "CLOSED", "EXPIRED"] as const).map((status) => (
        <li key={status} className="flex items-center gap-2.5 rounded-panel border border-border bg-surface px-3.5 py-2">
          <StatusBadge status={status} className="sr-only" />
          <span aria-hidden="true" className={cn("size-2 rounded-full", status === "OPEN" ? "bg-success" : status === "EXPIRED" ? "bg-warning" : "bg-text-secondary")} />
          <span className="font-display text-lg font-bold tabular-nums">{mine.loading ? "–" : mine.counts[status]}</span>
          <span className="text-xs text-text-secondary">{t(`mine.countLabel.${status}`)}</span>
        </li>
      ))}
    </ul>
  );

  return (
    <LfpShell section="mine" title={t("mine.title")} subtitle={t("mine.subtitle")} aside={summary}>
      <div className="space-y-4">
        <div role="tablist" aria-label={t("mine.filter")} className="flex flex-wrap gap-2">
          {STATUS_TABS.map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => setTab(value)}
              className={cn(
                "inline-flex h-10 items-center gap-2 rounded-control border px-3.5 text-sm font-semibold transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-focus motion-reduce:transition-none",
                tab === value ? "border-primary bg-primary/12 text-text-primary" : "border-border bg-surface text-text-secondary hover:border-control-border hover:text-text-primary"
              )}
            >
              {value === "ALL" ? t("mine.all") : t(`status.${value}`)}
              <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-surface-elevated px-1.5 text-xs font-bold tabular-nums text-text-primary">
                {mine.loading ? "–" : countOf(value)}
              </span>
            </button>
          ))}
        </div>

        <p aria-live="polite" className={cn("text-sm", notice ? "rounded-panel border border-success/50 bg-surface px-4 py-2.5 text-success" : "sr-only")}>
          {notice}
        </p>

        {mine.loading ? (
          <p role="status" className="rounded-panel border border-border bg-surface py-16 text-center text-sm text-text-secondary">{t("states.loading")}</p>
        ) : mine.error ? (
          <div role="alert" className="rounded-panel border border-primary/50 bg-surface px-6 py-10 text-center">
            <p className="text-sm text-text-secondary">{t(`errors.${lfpErrorKey(mine.error)}`)}</p>
            <button type="button" onClick={mine.reload} className={buttonClass("secondary", "md", "mt-4")}>{t("states.retry")}</button>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center rounded-panel border border-dashed border-border bg-surface px-6 py-12 text-center">
            <Icon name="fileText" className="size-8 text-text-secondary" />
            <h2 className="mt-3 font-display text-lg font-semibold">{tab === "ALL" ? t("mine.emptyTitle") : t("mine.emptyTabTitle")}</h2>
            <p className="mt-1.5 max-w-sm text-sm text-text-secondary">{t("mine.emptyBody")}</p>
            <Link href="/find-team/new" className={buttonClass("primary", "md", "mt-5")}>
              <Icon name="plus" className="size-4" />
              {t("actions.create")}
            </Link>
          </div>
        ) : (
          <div className="overflow-hidden rounded-panel border border-border bg-surface">
            <table className="w-full border-collapse text-sm max-md:block">
              <caption className="sr-only">{t("mine.tableCaption")}</caption>
              <thead className="border-b border-border text-start text-xs font-semibold uppercase tracking-wide text-text-secondary max-md:hidden">
                <tr>
                  <th scope="col" className="px-4 py-3 text-start">{t("mine.columns.listing")}</th>
                  <th scope="col" className="px-4 py-3 text-start">{t("mine.columns.game")}</th>
                  <th scope="col" className="px-4 py-3 text-start">{t("mine.columns.playTime")}</th>
                  <th scope="col" className="px-4 py-3 text-start">{t("mine.columns.expires")}</th>
                  <th scope="col" className="px-4 py-3 text-start">{t("mine.columns.status")}</th>
                  <th scope="col" className="px-4 py-3 text-end">{t("mine.columns.actions")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border max-md:block">
                {items.map((listing) => (
                  <tr key={listing.id} className="align-middle max-md:grid max-md:grid-cols-[4.5rem_minmax(0,1fr)] max-md:gap-x-3 max-md:gap-y-1 max-md:p-4">
                    <td className="px-4 py-3 max-md:col-start-2 max-md:row-start-1 max-md:p-0">
                      <p className="line-clamp-2 font-semibold [overflow-wrap:anywhere]">{listing.description}</p>
                      <p className="mt-0.5 text-xs text-text-secondary">
                        {t("card.players", { count: listing.playersNeeded })} · {listing.roles.join(", ")}
                      </p>
                    </td>
                    <td className="px-4 py-3 max-md:contents">
                      <div className="flex items-center gap-3 max-md:col-start-1 max-md:row-span-3 max-md:row-start-1 max-md:items-start">
                        <GameCover url={visuals.game(listing).coverUrl} title={listing.gameTitle} className="w-16 shrink-0 rounded-control max-md:w-[4.5rem]" />
                        <span className="min-w-0 max-md:hidden">
                          <span className="block truncate font-medium">{listing.gameTitle}</span>
                          <span className="block text-xs text-text-secondary">{platform(listing.platform)}</span>
                        </span>
                      </div>
                      <p className="hidden text-xs text-text-secondary max-md:col-start-2 max-md:block">
                        {listing.gameTitle} · {platform(listing.platform)}
                      </p>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap max-md:col-start-2 max-md:p-0 max-md:text-xs">
                      <span className="md:hidden text-text-secondary">{t("mine.columns.playTime")}: </span>
                      {time.playTime(listing)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap max-md:col-start-2 max-md:p-0 max-md:text-xs">
                      <span className="md:hidden text-text-secondary">{t("mine.columns.expires")}: </span>
                      {time.short(listing.expiresAt)}
                    </td>
                    <td className="px-4 py-3 max-md:col-start-2 max-md:p-0">
                      <StatusBadge status={listing.status} />
                    </td>
                    <td className="px-4 py-3 max-md:col-span-2 max-md:p-0 max-md:pt-2">
                      <div className="flex flex-wrap justify-end gap-2 max-md:justify-start xl:flex-nowrap">
                        {listing.status === "OPEN" ? (
                          <>
                            <Link
                              href={{ pathname: "/find-team/[listingId]/edit", params: { listingId: listing.id } }}
                              aria-label={t("mine.editNamed", { game: listing.gameTitle })}
                              className={buttonClass("secondary", "sm")}
                            >
                              <Icon name="pencil" className="size-4" />
                              {t("actions.edit")}
                            </Link>
                            <button
                              type="button"
                              onClick={() => {
                                setCloseError(null);
                                setNotice(null);
                                setClosing(listing);
                              }}
                              aria-label={t("mine.closeNamed", { game: listing.gameTitle })}
                              className={buttonClass("primary", "sm")}
                            >
                              {t("actions.close")}
                            </button>
                          </>
                        ) : (
                          <button type="button" onClick={() => setDetail(listing)} aria-label={t("mine.detailNamed", { game: listing.gameTitle })} className={buttonClass("secondary", "sm")}>
                            <Icon name="eye" className="size-4" />
                            {t("actions.detail")}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="flex items-center gap-2 border-t border-border px-4 py-3 text-xs text-text-secondary">
              <Icon name="clock" className="size-4" />
              {t("mine.expiryNote")}
              {mine.truncated ? ` ${t("mine.truncated")}` : null}
            </p>
          </div>
        )}
      </div>

      {closing ? (
        <ConfirmDialog
          title={t("mine.closeTitle")}
          description={t("mine.closeBody", { game: closing.gameTitle })}
          confirmLabel={t("actions.close")}
          cancelLabel={t("form.cancel")}
          tone="danger"
          busy={closeBusy}
          onConfirm={() => void confirmClose()}
          onCancel={() => setClosing(null)}
        >
          {closeError ? <p role="alert" className="text-sm text-brand-text">{closeError}</p> : null}
        </ConfirmDialog>
      ) : null}

      {detail ? (
        <ModalDialog title={t("mine.detailTitle")} onClose={() => setDetail(null)} closeLabel={t("filters.close")}>
          <ListingCard
            listing={detail}
            owner={{ userId: meId, name: t("form.you"), handle: null, avatarUrl: null }}
            game={visuals.game(detail)}
            mine={false}
            contacting={false}
            contactError={null}
            onContact={() => undefined}
            variant="static"
          />
          {detail.closedAt ? <p className="text-xs text-text-secondary">{t("mine.closedAt", { time: time.short(detail.closedAt) })}</p> : null}
        </ModalDialog>
      ) : null}
    </LfpShell>
  );
}
