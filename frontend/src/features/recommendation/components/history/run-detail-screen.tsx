"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useRunDetail } from "../../hooks/use-runs";
import { errorStatus } from "../../services/recommendation-errors";
import { GameRunView, RunMeta } from "../games/game-run-view";
import { RecommendationEmpty, RecommendationError, RecommendationSkeleton, SectionHeading } from "../recommendation-ui";

/** Geçmişteki tek çalışma. 404: başkasına ait, silinmiş veya 30 günlük saklama süresi dolmuş (ayrım yapılmaz). */
export function RunDetailScreen({ runId }: { runId: string }) {
  const t = useTranslations("Recommendation.history");
  const headingId = useId();
  const detail = useRunDetail(runId);
  const back = <Link href="/for-you/history" className={buttonClass("ghost", "sm")}><Icon name="chevron" className="size-4 rotate-90 rtl:-rotate-90" />{t("back")}</Link>;

  if (detail.error && errorStatus(detail.error) === 404) {
    return <RecommendationEmpty icon="clock" title={t("notFoundTitle")} description={t("notFoundDescription")}>{back}</RecommendationEmpty>;
  }
  return <section aria-labelledby={headingId}>
    <SectionHeading id={headingId} title={t("detailTitle")} action={back} />
    {detail.data
      ? <>
        <div className="mb-5"><RunMeta run={detail.data} /></div>
        <GameRunView run={detail.data} />
      </>
      : detail.error
        ? <RecommendationError error={detail.error} retry={detail.reload} />
        : <RecommendationSkeleton count={3} />}
  </section>;
}
