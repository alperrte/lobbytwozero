"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useReviewAction } from "../hooks/use-review-action";
import { useReviewResource } from "../hooks/use-review-resource";
import { reviewApi, reviewMutations } from "../services/review-api";
import { describeReviewError, isNotFound } from "../services/review-errors";
import { formatDateTime, wasEdited } from "../services/format";
import type { ReviewLikeResponse, ReviewReport, ReviewResponse } from "../types/review";
import { GameHeader } from "./game-header";
import { LikeButton } from "./like-button";
import { ReportReview } from "./report-review";
import { ReviewBoundary } from "./review-boundary";
import { ReviewForm } from "./review-form";
import { EmptyState, Panel, ReviewAlert, ReviewLoading, Skeleton, actionClass, focusRing } from "./review-ui";

/**
 * İnceleme detayı + sahibin düzenleme/silme işlemleri. Sahip işlemleri yalnız sunucunun `ownedByActor=true` yanıtında görünür
 * (rol/arayüz yetki değildir; nihai karar backend'dedir). Düzenleme ve silme gerçek `version` ile gider; 409'da veri sessizce
 * EZİLMEZ: kullanıcı güncel hali yükler, kendi taslağı korunur. Silme yalnız metni kaldırır; puan ve tavsiye kalır.
 */
export function ReviewDetailPage({ reviewId }: { reviewId: string }) {
  return (
    <ReviewBoundary>
      <DetailWorkspace reviewId={reviewId} />
    </ReviewBoundary>
  );
}

function DetailWorkspace({ reviewId }: { reviewId: string }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const router = useRouter();
  const loaded = useReviewResource<ReviewResponse>({ kind: "review", reviewId });
  const likeState = useReviewResource<ReviewLikeResponse>({ kind: "like", reviewId });
  // Raporlama yalnız başkasının incelemesinde sunulur; mevcut rapor sunucudan okunur (yoksa 404 → null).
  const ownReport = useReviewResource<ReviewReport | null>(loaded.data && !loaded.data.ownedByActor ? { kind: "ownReport", reviewId } : null);
  const update = useReviewAction();
  const remove = useReviewAction();
  const refresh = useReviewAction();
  const [latest, setLatest] = useState<ReviewResponse>();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [serverCopy, setServerCopy] = useState<ReviewResponse>();
  const [confirming, setConfirming] = useState(false);
  const [saved, setSaved] = useState(false);

  const review = latest ?? loaded.data;

  if (!review) {
    if (loaded.error) {
      return isNotFound(loaded.error) ? (
        <EmptyState title={t("detail.notFound")} hint={t("detail.notFoundHint")} action={<Link href="/reviews" className={actionClass("secondary")}>{t("hub.title")}</Link>} />
      ) : (
        <ReviewAlert error={loaded.error} onRetry={loaded.reload} />
      );
    }
    return <ReviewLoading />;
  }

  async function reloadLatest() {
    const result = await refresh.run((signal) => reviewApi.review(reviewId, signal));
    if (result?.ok) {
      setLatest(result.data);
      setServerCopy(result.data);
      update.clearError();
      remove.clearError();
    }
    return result?.ok === true;
  }

  async function save(current: ReviewResponse) {
    const result = await update.run((signal) => reviewMutations.updateReview(reviewId, current.version, draft, signal));
    if (result?.ok) {
      setLatest(result.data);
      setEditing(false);
      setServerCopy(undefined);
      setSaved(true);
    }
  }

  async function confirmDelete(current: ReviewResponse) {
    const result = await remove.run((signal) => reviewMutations.deleteReview(reviewId, current.version, signal));
    if (result?.ok) router.replace({ pathname: "/reviews/games/[gameId]", params: { gameId: current.gameId } });
  }

  const created = formatDateTime(review.createdAt, locale);
  const updated = wasEdited(review.createdAt, review.updatedAt) ? formatDateTime(review.updatedAt, locale) : null;
  const updateFailure = update.error ? describeReviewError(update.error, "update") : null;
  const deleteFailure = remove.error ? describeReviewError(remove.error, "delete") : null;

  return (
    <>
      <nav aria-label={t("breadcrumb")} className="flex flex-wrap gap-x-2 text-sm text-text-secondary">
        <Link href="/reviews" className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("hub.title")}</Link>
        <span aria-hidden="true">/</span>
        <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId: review.gameId } }} className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("game.reviewsLink")}</Link>
      </nav>

      <GameHeader gameId={review.gameId} heading="p" />

      <header className="space-y-2">
        <h1 className="font-display text-3xl font-semibold text-balance">{review.ownedByActor ? t("detail.titleMine") : t("detail.title")}</h1>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-secondary">
          {review.ownedByActor ? <span className="rounded-control border border-primary/40 bg-primary/10 px-2 py-0.5 font-semibold text-brand-text">{t("card.mine")}</span> : null}
          <span>{created ? t("card.created", { date: created }) : t("card.dateUnknown")}</span>
          {updated ? <span>{t("card.edited", { date: updated })}</span> : null}
        </p>
      </header>

      {saved ? <p role="status" className="text-sm text-success">{t("detail.saved")}</p> : null}

      <Panel>
        {editing ? (
          <ReviewForm
            value={draft}
            onChange={(value) => {
              setDraft(value);
              update.clearError();
              setSaved(false);
            }}
            onSubmit={() => save(review)}
            pending={update.pending}
            submitLabel={t("detail.saveEdit")}
            pendingLabel={t("saving")}
            onCancel={() => {
              setEditing(false);
              setServerCopy(undefined);
              update.clearError();
            }}
          >
            <div className="space-y-3">
              <ReviewAlert error={update.error} context="update" onReload={reloadLatest} />
              {updateFailure?.conflict || serverCopy ? (
                <div className="space-y-2 rounded-panel border border-border bg-surface-elevated p-4 text-sm">
                  <p className="font-semibold">{t("detail.conflictTitle")}</p>
                  <p className="text-text-secondary">{t("detail.conflictHint")}</p>
                  {serverCopy ? (
                    <blockquote className="whitespace-pre-wrap border-s-2 border-primary ps-3 leading-6 [overflow-wrap:anywhere]">{serverCopy.body}</blockquote>
                  ) : null}
                </div>
              ) : null}
              <ReviewAlert error={refresh.error} context="update" onRetry={reloadLatest} />
            </div>
          </ReviewForm>
        ) : (
          <p className="whitespace-pre-wrap text-sm leading-7 [overflow-wrap:anywhere]">{review.body}</p>
        )}
      </Panel>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          {likeState.data ? (
            <LikeButton reviewId={review.id} ownedByActor={review.ownedByActor} initialCount={likeState.data.likeCount} initialLiked={likeState.data.likedByActor} />
          ) : likeState.error ? (
            <ReviewAlert error={likeState.error} onRetry={likeState.reload} />
          ) : (
            <Skeleton className="h-11 w-40" />
          )}
        </div>
        {!review.ownedByActor && ownReport.data !== undefined ? (
          <ReportReview reviewId={review.id} ownedByActor={review.ownedByActor} initialReport={ownReport.data} />
        ) : null}
        {review.ownedByActor && !editing ? (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={actionClass("secondary")}
              onClick={() => {
                setDraft(review.body);
                setEditing(true);
                setSaved(false);
                setServerCopy(undefined);
              }}
            >
              {t("detail.edit")}
            </button>
            <button type="button" className={actionClass("ghost")} onClick={() => { remove.clearError(); setConfirming(true); }}>{t("detail.delete")}</button>
          </div>
        ) : null}
      </div>

      {confirming ? (
        <ConfirmDialog
          tone="danger"
          title={t("detail.deleteTitle")}
          description={t("detail.deleteDescription")}
          confirmLabel={t("detail.deleteConfirm")}
          cancelLabel={t("cancel")}
          busy={remove.pending}
          onCancel={() => setConfirming(false)}
          onConfirm={() => confirmDelete(review)}
        >
          <ReviewAlert
            error={remove.error}
            context="delete"
            onReload={async () => {
              if (await reloadLatest()) setConfirming(false);
            }}
          />
          {deleteFailure?.conflict ? <p className="text-sm text-text-secondary">{t("detail.conflictHint")}</p> : null}
        </ConfirmDialog>
      ) : null}
    </>
  );
}
