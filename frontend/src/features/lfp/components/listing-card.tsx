"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { GameCover } from "@/features/game/components/game-cover";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import type { GameVisual, Listing, Owner } from "../types/lfp.types";
import { Chip, Meta, StatusBadge, useListingTime, usePlatformLabel } from "./listing-parts";

type ListingCardProps = {
  listing: Listing;
  owner: Owner;
  game: GameVisual;
  mine: boolean;
  contacting: boolean;
  contactError: string | null;
  onContact: (listing: Listing) => void;
  /** `preview`: form önizlemesi (eylem pasif, "Önizleme" etiketli); `static`: eylem düğmesi yok (ilan detayı). */
  variant?: "default" | "preview" | "static";
};

/**
 * İlan kartı (11.01): oyun kapağı, sahip, oyun adı, platform/seviye/rol etiketleri, açıklama ve alt bilgi satırı (aranan oyuncu,
 * oyun zamanı, mikrofon, ilan bitişi). Başkasının ilanında "Mesaj Gönder", kendi ilanında "Düzenle" vardır. Açıklama düz metindir.
 */
export function ListingCard({ listing, owner, game, mine, contacting, contactError, onContact, variant = "default" }: Readonly<ListingCardProps>) {
  const t = useTranslations("Lfp");
  const platform = usePlatformLabel();
  const time = useListingTime();
  const titleId = useId();
  const errorId = useId();

  return (
    <article
      aria-labelledby={titleId}
      className="group flex min-w-0 flex-col overflow-hidden rounded-panel border border-border bg-surface transition-colors duration-150 hover:border-primary/45 motion-reduce:transition-none"
    >
      <GameCover url={game.coverUrl} title={game.title} className="h-28 w-full sm:h-32">
        <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-t from-surface via-surface/30 to-transparent" />
      </GameCover>
      <div className="relative -mt-9 flex flex-1 flex-col gap-3 px-4 pb-4">
        <div className="flex items-end gap-3">
          <Avatar seed={owner.userId} imageUrl={owner.avatarUrl} size="md" ring className="shrink-0 bg-surface" />
          <div className="min-w-0 flex-1 pb-0.5">
            <p className="truncate text-sm font-semibold text-text-primary">
              {owner.name}
              {owner.handle ? <span className="ms-1.5 font-normal text-text-secondary">{owner.handle}</span> : null}
            </p>
          </div>
          <StatusBadge status={listing.status} className="pb-0.5" />
        </div>
        <h3 id={titleId} className="font-display text-lg font-semibold leading-snug text-text-primary [overflow-wrap:anywhere]">
          {listing.gameTitle}
        </h3>
        <ul aria-label={t("card.details")} className="flex flex-wrap gap-1.5">
          <li className="max-w-full"><Chip icon="desktop">{platform(listing.platform)}</Chip></li>
          <li className="max-w-full">
            <Chip icon="chart" muted={!listing.skillLevel}>{listing.skillLevel ?? t("card.anyLevel")}</Chip>
          </li>
          {listing.roles.map((role) => (
            <li key={role} className="max-w-full"><Chip icon="users">{role}</Chip></li>
          ))}
        </ul>
        <p className="line-clamp-3 whitespace-pre-line text-sm text-text-secondary [overflow-wrap:anywhere]">{listing.description}</p>
        <div className="mt-auto grid grid-cols-2 gap-x-3 gap-y-1.5 border-t border-border/70 pt-3">
          <Meta icon="usersRound">{t("card.players", { count: listing.playersNeeded })}</Meta>
          <Meta icon="calendar">{time.playTime(listing)}</Meta>
          <Meta icon={listing.micRequired ? "volume" : "volumeOff"}>{listing.micRequired ? t("card.micRequired") : t("card.micOptional")}</Meta>
          <Meta icon="clock">{t("card.expires", { time: time.short(listing.expiresAt) })}</Meta>
        </div>
        {variant === "static" ? null : variant === "preview" ? (
          <span aria-disabled="true" className={buttonClass("secondary", "md", "w-full cursor-default opacity-70")}>
            <Icon name="message" className="size-4" />
            {t("actions.message")}
            <span className="rounded-full bg-surface px-2 py-0.5 text-[0.6875rem] text-text-secondary">{t("form.previewBadge")}</span>
          </span>
        ) : mine ? (
          <Link
            href={{ pathname: "/find-team/[listingId]/edit", params: { listingId: listing.id } }}
            className={buttonClass("secondary", "md", "w-full")}
          >
            <Icon name="pencil" className="size-4" />
            {t("actions.edit")}
          </Link>
        ) : (
          <button
            type="button"
            onClick={() => onContact(listing)}
            disabled={contacting}
            aria-busy={contacting || undefined}
            aria-describedby={contactError ? errorId : undefined}
            aria-label={t("actions.messageNamed", { name: owner.name })}
            className={buttonClass("primary", "md", "w-full")}
          >
            <Icon name="message" className="size-4" />
            {contacting ? t("actions.opening") : t("actions.message")}
          </button>
        )}
        {contactError ? (
          <p id={errorId} role="alert" className="text-xs text-brand-text">
            {contactError}
          </p>
        ) : null}
      </div>
    </article>
  );
}
