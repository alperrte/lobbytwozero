"use client";

import { useId } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useRunHistory } from "../../hooks/use-runs";
import { InlineError, RecommendationEmpty, RecommendationError, RecommendationSkeleton, SectionHeading, panelClass } from "../recommendation-ui";

/** Öneri geçmişi (son 30 gün). İmleçli sayfalama; her satır çalışmanın ayrıntısına gider. */
export function RunHistoryScreen() {
  const t = useTranslations("Recommendation.history");
  const format = useFormatter();
  const headingId = useId();
  const history = useRunHistory();

  return <section aria-labelledby={headingId}>
    <SectionHeading id={headingId} title={t("title")} description={t("description")} />
    {history.loading && !history.items.length
      ? <RecommendationSkeleton count={4} variant="row" />
      : history.error
        ? <RecommendationError error={history.error} retry={history.reload} />
        : history.items.length === 0
          ? <RecommendationEmpty icon="clock" title={t("emptyTitle")} description={t("emptyDescription")}>
            <Link href="/for-you/games" className={buttonClass("primary", "md")}>{t("goGenerate")}</Link>
          </RecommendationEmpty>
          : <>
            <ol className={cn(panelClass, "divide-y divide-border")}>
              {history.items.map((run) => <li key={run.runId}>
                <Link href={{ pathname: "/for-you/history/[runId]", params: { runId: run.runId } }} className="group flex min-h-16 items-center gap-4 px-4 py-3 transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus motion-reduce:transition-none sm:px-5">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-control border border-border bg-surface-elevated"><Icon name="gamepad" className="size-5 text-text-secondary" /></span>
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold text-text-primary">{format.dateTime(new Date(run.createdAt), { dateStyle: "medium", timeStyle: "short" })}</span>
                    <span className="block text-sm text-text-secondary">{t("resultCount", { count: run.resultCount })}</span>
                  </span>
                  <span className="hidden text-sm font-semibold text-text-secondary group-hover:text-text-primary sm:inline">{t("open")}</span>
                  <Icon name="chevron" className="size-4 shrink-0 -rotate-90 text-text-secondary rtl:rotate-90" />
                </Link>
              </li>)}
            </ol>
            <div className="mt-5 flex flex-col items-center gap-2">
              {history.nextCursor
                ? <button type="button" className={buttonClass("secondary", "md")} disabled={history.loadingMore} aria-busy={history.loadingMore} onClick={() => void history.loadMore()}>{history.loadingMore ? t("loadingMore") : t("loadMore")}</button>
                : <p className="text-sm text-text-secondary">{t("end")}</p>}
              <InlineError error={history.moreError} />
            </div>
          </>}
  </section>;
}
