"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useReviewAction } from "../hooks/use-review-action";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { useReviewResource } from "../hooks/use-review-resource";
import { reviewMutations } from "../services/review-api";
import { describeReviewError, retryAfterOf } from "../services/review-errors";
import { formatAverage, formatCount } from "../services/format";
import type { RatingSummary, ReviewResponse } from "../types/review";
import { GameHeader } from "./game-header";
import { ReviewBoundary } from "./review-boundary";
import { ReviewForm } from "./review-form";
import { Panel, ReviewAlert, Skeleton, actionClass, focusRing } from "./review-ui";

/**
 * İnceleme yazma. Yalnız METİN gönderilir; puan ve tavsiye ayrı kayıtlardır ve oyun sayfasından yönetilir. Hatada metin
 * korunur. Başarıda gerçek inceleme detayına gidilir. Kullanıcının bu oyunda zaten görünür incelemesi varsa (Review'ın
 * `reviews/mine` ucu) form açılmaz; mevcut incelemeyi düzenleme bağlantısı sunulur. Yarış durumunda 409 gelirse aynı bağlantı
 * sunucudan yeniden okunarak gösterilir.
 */
export function ReviewWritePage({ gameId }: { gameId: string }) {
  return (
    <ReviewBoundary>
      <WriteWorkspace gameId={gameId} />
    </ReviewBoundary>
  );
}

function WriteWorkspace({ gameId }: { gameId: string }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const router = useRouter();
  const summary = useReviewResource<RatingSummary>({ kind: "ratingSummary", gameId });
  const existing = useReviewResource<ReviewResponse | null>({ kind: "ownReview", gameId });
  const action = useReviewAction();
  const countdown = useRetryCountdown();
  const [body, setBody] = useState("");

  async function submit() {
    const result = await action.run((signal) => reviewMutations.createReview(gameId, body, signal));
    if (!result) return;
    if (result.ok) {
      router.push({ pathname: "/reviews/[reviewId]", params: { reviewId: result.data.id } });
      return;
    }
    const wait = retryAfterOf(result.error);
    if (wait) {
      // Kota mesajı geri sayımla gösterilir; süre dolunca eski hata kutusu tekrar çıkmaz. Otomatik yeniden deneme yoktur.
      countdown.start(wait);
      action.clearError();
      return;
    }
    // Başka sekmede/istekte inceleme oluşmuş olabilir: gerçek durumu sunucudan oku (taslak metin korunur).
    if (describeReviewError(result.error, "create").key === "409create") existing.reload();
  }

  const average = summary.data ? formatAverage(summary.data.averageRating, locale) : null;
  const mine = existing.data ?? null;
  const checking = existing.data === undefined && !existing.error;

  return (
    <>
      <nav aria-label={t("breadcrumb")} className="flex flex-wrap gap-x-2 text-sm text-text-secondary">
        <Link href="/reviews" className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("hub.title")}</Link>
        <span aria-hidden="true">/</span>
        <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId } }} className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("game.reviewsLink")}</Link>
      </nav>

      <GameHeader gameId={gameId} heading="p" />
      {summary.data ? (
        <p className="text-sm text-text-secondary">
          {average ? t("write.summary", { average, count: formatCount(summary.data.ratingCount, locale) }) : t("write.summaryNone")}
        </p>
      ) : null}

      <header className="space-y-2">
        <h1 className="font-display text-3xl font-semibold text-balance">{t("write.title")}</h1>
        <p className="max-w-prose text-sm leading-6 text-text-secondary">{t("write.description")}</p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Panel>
          {checking ? <Skeleton className="h-48" /> : null}
          <ReviewAlert error={existing.error} onRetry={existing.reload} />
          {mine ? (
            <div className="space-y-4">
              <p role="status" className="text-sm font-semibold">{t("write.alreadyHave")}</p>
              <p className="text-sm text-text-secondary">{t("write.duplicateHint")}</p>
              <div className="flex flex-wrap gap-2">
                <Link href={{ pathname: "/reviews/[reviewId]", params: { reviewId: mine.id } }} className={actionClass("primary")}>{t("write.foundMine")}</Link>
                <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId } }} className={actionClass("secondary")}>{t("game.reviewsLink")}</Link>
              </div>
              {body.trim() ? <p className="text-xs text-text-secondary">{t("write.draftKept")}</p> : null}
            </div>
          ) : !checking ? (
            <ReviewForm
              value={body}
              onChange={(value) => {
                setBody(value);
                action.clearError();
              }}
              onSubmit={submit}
              pending={action.pending}
              submitLabel={t("write.submit")}
              pendingLabel={t("saving")}
              disabledReason={countdown.remaining > 0 ? t("like.wait", { seconds: countdown.remaining }) : undefined}
            >
              {action.error ? <ReviewAlert error={action.error} context="create" /> : null}
            </ReviewForm>
          ) : null}
        </Panel>

        <aside aria-label={t("write.asideTitle")} className="h-fit space-y-3 rounded-panel border border-border bg-surface p-5 text-sm leading-6 text-text-secondary">
          <h2 className="font-display text-base font-semibold text-text-primary">{t("write.asideTitle")}</h2>
          <p>{t("write.asideIndependent")}</p>
          <p>{t("write.asideDelete")}</p>
          <p>{t("write.asidePlain")}</p>
          <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId } }} className={`inline-flex min-h-11 items-center rounded-control font-semibold text-brand-text underline underline-offset-2 ${focusRing}`}>{t("write.asideLink")}</Link>
        </aside>
      </div>
    </>
  );
}
