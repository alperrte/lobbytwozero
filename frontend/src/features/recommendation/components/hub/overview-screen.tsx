"use client";

import { useId, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";
import { useCatalogTerms, useGameCards, useGameMetadata } from "../../hooks/use-enrichment";
import { useLabels } from "../../hooks/use-labels";
import { useLatestRun } from "../../hooks/use-runs";
import { SIZE_LIMITS } from "../../schemas/filters";
import { toGameViews } from "../../services/adapters";
import type { RecommendationRun } from "../../types/recommendation";
import { EventSuggestions } from "../events/event-suggestions";
import { GameRecommendationCard } from "../games/game-recommendation-card";
import { RunMeta } from "../games/game-run-view";
import { RecommendationEmpty, RecommendationError, RecommendationSkeleton, SectionHeading, panelClass } from "../recommendation-ui";
import { PeopleSuggestions } from "../social/people-suggestions";

const PREVIEW_GAMES = 4;

/**
 * Hub genel bakışı: yalnız mevcut veriden önizleme. Oyunlar için son kayıtlı çalışma okunur (yeni öneri ÜRETİLMEZ);
 * arkadaş ve etkinlik önizlemeleri bölüm sayfalarıyla aynı önbellek anahtarını kullanır. Dekoratif sayaç/metrik yoktur.
 */
export function OverviewScreen() {
  const t = useTranslations("Recommendation.overview");
  const id = useId();
  const latest = useLatestRun();

  return <div className="space-y-10">
    <section aria-labelledby={`${id}-games`} aria-busy={latest.loading}>
      <SectionHeading id={`${id}-games`} title={t("gamesTitle")} description={t("gamesDescription")}
        action={<Link href="/for-you/games" className={buttonClass("ghost", "sm")}>{t("openGames")}<Icon name="chevron" className="size-4 -rotate-90" /></Link>} />
      {latest.data
        ? <LatestPreview run={latest.data} />
        : latest.loading
          ? <RecommendationSkeleton count={PREVIEW_GAMES} />
          : latest.error
            ? <RecommendationError error={latest.error} retry={latest.reload} compact />
            : <RecommendationEmpty icon="sparkles" title={t("noRunTitle")} description={t("noRunDescription")}>
              <Link href="/for-you/games" className={buttonClass("primary", "md")}><Icon name="sparkles" className="size-4" />{t("createFirst")}</Link>
            </RecommendationEmpty>}
    </section>

    <div className="grid gap-10 lg:grid-cols-2 lg:gap-6">
      <PreviewSection id={`${id}-friends`} title={t("friendsTitle")} description={t("friendsDescription")} href="/for-you/friends" linkLabel={t("openFriends")}>
        <PeopleSuggestions kind="FRIEND_SUGGESTIONS" filters={{ size: SIZE_LIMITS.people.fallback }} compact limit={3} />
      </PreviewSection>
      <PreviewSection id={`${id}-events`} title={t("eventsTitle")} description={t("eventsDescription")} href="/for-you/events" linkLabel={t("openEvents")}>
        <EventSuggestions size={SIZE_LIMITS.events.fallback} compact limit={2} />
      </PreviewSection>
    </div>

    <nav aria-labelledby={`${id}-more`}>
      <h2 id={`${id}-more`} className="mb-4 font-display text-lg font-semibold">{t("moreTitle")}</h2>
      <ul className="grid gap-3 md:grid-cols-3">
        <EntryLink href="/for-you/hardware" icon="cpu" title={t("hardwareTitle")} description={t("hardwareDescription")} />
        <EntryLink href="/for-you/players" icon="users" title={t("playersTitle")} description={t("playersDescription")} />
        <EntryLink href="/for-you/history" icon="clock" title={t("historyTitle")} description={t("historyDescription")} />
      </ul>
    </nav>
  </div>;
}

function LatestPreview({ run }: { run: RecommendationRun }) {
  const t = useTranslations("Recommendation.games");
  const shown = run.items.slice(0, PREVIEW_GAMES);
  const ids = shown.map((item) => item.gameId);
  const cards = useGameCards(ids);
  const metadata = useGameMetadata(ids);
  const genres = useCatalogTerms("GENRE");
  const labels = useLabels();
  if (!shown.length) return <RecommendationEmpty icon="gamepad" title={t("emptyRunTitle")} description={t("emptyRunDescription")} />;
  return <div className="space-y-4">
    <RunMeta run={run} />
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label={t("listLabel")}>
      {toGameViews(shown, cards, metadata.data).map((view) => <li key={view.item.itemId} className="min-w-0">
        <GameRecommendationCard view={view} genreLabel={(code) => labels.term(genres.data, code)} />
      </li>)}
    </ul>
  </div>;
}

function PreviewSection({ id, title, description, href, linkLabel, children }: { id: string; title: string; description: string; href: AppPathname; linkLabel: string; children: ReactNode }) {
  return <section aria-labelledby={id} className="min-w-0">
    <SectionHeading id={id} title={title} description={description} action={<Link href={href} className={buttonClass("ghost", "sm")}>{linkLabel}<Icon name="chevron" className="size-4 -rotate-90" /></Link>} />
    {children}
  </section>;
}

function EntryLink({ href, icon, title, description }: { href: AppPathname; icon: IconName; title: string; description: string }) {
  return <li className="min-w-0">
    <Link href={href} className={cn(panelClass, "group flex h-full items-start gap-3 p-4 transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus")}>
      <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-control bg-surface-elevated text-text-secondary group-hover:text-brand-text">
        <Icon name={icon} className="size-4" />
      </span>
      <span className="min-w-0">
        <span className="block font-display text-sm font-semibold">{title}</span>
        <span className="mt-1 block text-xs leading-5 text-text-secondary">{description}</span>
      </span>
    </Link>
  </li>;
}
