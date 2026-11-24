"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useGenerateRun, useLatestRun } from "../../hooks/use-runs";
import { GAME_SIZE_OPTIONS, PLATFORM_PATTERN, clampSize, optionalCode } from "../../schemas/filters";
import { InlineError, RecommendationEmpty, RecommendationError, RecommendationSkeleton, SectionHeading, panelClass } from "../recommendation-ui";
import { GameFilters, type GameFilterValue } from "./game-filters";
import { GameRunView, RunMeta } from "./game-run-view";

/** Üretme düğmesinin metni: bekleme, sunucu geri sayımı veya normal eylem. */
export function GenerateLabel({ pending, cooldown, idle }: { pending: boolean; cooldown: number; idle: string }) {
  const t = useTranslations("Recommendation.common");
  if (pending) return <>{t("working")}</>;
  if (cooldown > 0) return <>{t("retryIn", { seconds: cooldown })}</>;
  return <>{idle}</>;
}

/**
 * Kişisel oyun önerileri. Sayfa açılışında yalnız son kayıtlı çalışma okunur; yeni öneri YALNIZ "Öneri oluştur" ile
 * üretilir (her üretim geçmişe yazılır ve dakikalık kotadan düşer).
 */
export function GamesScreen() {
  const t = useTranslations("Recommendation.games");
  const headingId = useId();
  const resultId = useId();
  const latest = useLatestRun();
  const generator = useGenerateRun();
  const [filters, setFilters] = useState<GameFilterValue>({ genre: "", platform: "", size: 12 });
  const run = generator.run ?? latest.data ?? null;
  const fresh = generator.run !== undefined;

  async function generate() {
    await generator.generate({
      genre: optionalCode(filters.genre),
      platform: optionalCode(filters.platform, PLATFORM_PATTERN),
      size: clampSize("games", filters.size)
    });
  }

  return <div className="space-y-10">
    <section aria-labelledby={headingId} className={cn(panelClass, "p-5 sm:p-6")}>
      <SectionHeading id={headingId} title={t("generateTitle")} description={t("generateDescription")} />
      <GameFilters value={filters} onChange={setFilters} sizes={GAME_SIZE_OPTIONS} disabled={generator.pending} />
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button type="button" className={buttonClass("primary", "md")} disabled={generator.pending || generator.cooldown > 0} aria-busy={generator.pending} aria-describedby={`${headingId}-note`} onClick={() => void generate()}>
          <Icon name="sparkles" className="size-4" />
          <GenerateLabel pending={generator.pending} cooldown={generator.cooldown} idle={t("generate")} />
        </button>
        <InlineError error={generator.error} />
      </div>
      <p id={`${headingId}-note`} className="mt-3 text-xs leading-5 text-text-secondary">{t("generateNote")}</p>
    </section>

    <section aria-labelledby={resultId} aria-busy={latest.loading && !generator.run}>
      <SectionHeading id={resultId} title={fresh ? t("freshTitle") : t("latestTitle")} action={<Link href="/for-you/history" className={buttonClass("ghost", "sm")}><Icon name="clock" className="size-4" />{t("openHistory")}</Link>} />
      {run ? <div className="mb-5"><RunMeta run={run} /></div> : null}
      {run
        ? <GameRunView key={run.runId} run={run} fresh={fresh} />
        : latest.loading
          ? <RecommendationSkeleton count={3} />
          : latest.error
            ? <RecommendationError error={latest.error} retry={latest.reload} />
            : <RecommendationEmpty icon="sparkles" title={t("noRunTitle")} description={t("noRunDescription")} />}
    </section>
  </div>;
}
