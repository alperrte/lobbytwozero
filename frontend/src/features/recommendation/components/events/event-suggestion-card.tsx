"use client";

import { useId } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { GameCover, coverOf } from "@/features/game/components/game-cover";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useLabels } from "../../hooks/use-labels";
import { useJoinEvent } from "../../hooks/use-social-recommendations";
import type { EventSuggestionView } from "../../services/adapters";
import { ChipList, InlineError, panelClass } from "../recommendation-ui";

const TIME_ZONE = "Europe/Istanbul";

/**
 * Etkinlik önerisi. Etkinlik detay sayfası frontend'de henüz olmadığından kartta detay bağlantısı yoktur; katılım doğrudan
 * Event Service'e gider. Başarı yalnız sunucu onayından sonra gösterilir.
 */
export function EventSuggestionCard({ view, compact = false }: { view: EventSuggestionView; compact?: boolean }) {
  const t = useTranslations("Recommendation.events");
  const common = useTranslations("Recommendation.common");
  const format = useFormatter();
  const labels = useLabels();
  const titleId = useId();
  const { event, game } = view;
  const join = useJoinEvent(event.eventId);
  const start = new Date(event.startsAt);
  const end = new Date(event.endsAt);
  const range = format.dateTimeRange(start, end, { dateStyle: "medium", timeStyle: "short", timeZone: TIME_ZONE });
  const typeLabel = t.has(`type.${event.type}`) ? t(`type.${event.type}`) : event.type;
  const joined = join.outcome === "JOINED" || join.outcome === "ALREADY_JOINED";

  return <article aria-labelledby={titleId} className={cn(panelClass, "flex min-w-0 flex-col overflow-hidden")}>
    {!compact && game ? <GameCover url={coverOf(game)} className="border-b border-border" /> : null}
    <div className={cn("flex min-w-0 flex-1 flex-col gap-3", compact ? "p-3" : "p-4")}>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-brand-text">{typeLabel}</p>
        <h3 id={titleId} className="mt-1 line-clamp-2 font-display text-lg font-semibold leading-snug [overflow-wrap:anywhere]">{event.title}</h3>
      </div>
      <p className="flex items-start gap-2 text-sm text-text-secondary">
        <Icon name="calendar" className="mt-0.5 size-4 shrink-0" />
        <time dateTime={event.startsAt}>{range}</time>
      </p>
      {game
        ? <p className="flex items-center gap-2 text-sm"><Icon name="gamepad" className="size-4 shrink-0 text-text-secondary" />
          <Link href={{ pathname: "/games/[gameId]", params: { gameId: game.gameId } }} className="truncate rounded-sm hover:text-brand-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">{game.title}</Link>
        </p>
        : null}
      {compact ? null : <div className="space-y-3">
        <ChipList label={t("sharedGenres")} items={event.sharedGenres.map(labels.genre)} />
        <ChipList label={t("sharedPlatforms")} items={event.sharedPlatforms.map(labels.platform)} />
      </div>}
      <div className="mt-auto space-y-1.5 pt-1">
        {joined
          ? <p role="status" className="flex min-h-9 items-center justify-center gap-1.5 rounded-control border border-border px-3 text-sm text-text-secondary"><Icon name="check" className="size-4 shrink-0 text-success" />{t(`outcome.${join.outcome}`)}</p>
          : join.outcome
            ? <p role="status" className="flex min-h-9 items-center justify-center gap-1.5 rounded-control border border-border px-3 text-center text-sm text-text-secondary"><Icon name="info" className="size-4 shrink-0" />{t(`outcome.${join.outcome}`)}</p>
            : <button type="button" className={buttonClass("primary", "sm", "w-full")} disabled={join.pending || join.cooldown > 0} aria-busy={join.pending} aria-label={t("joinLabel", { title: event.title })} onClick={() => void join.join()}>
              {join.pending ? common("working") : join.cooldown > 0 ? common("retryIn", { seconds: join.cooldown }) : t("join")}
            </button>}
        <InlineError error={join.error} />
      </div>
    </div>
  </article>;
}
