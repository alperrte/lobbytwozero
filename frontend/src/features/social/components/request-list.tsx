"use client";

import { useFormatter, useNow, useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { useSocialMutations } from "../hooks/use-social-mutations";
import type { FriendRequestView } from "../types/social.types";
import { GameCover } from "./game-cover";
import { useAnnounce } from "./social-announcer";
import { usePerson } from "./use-person";

type RequestListProps = {
  views: readonly FriendRequestView[];
  direction: "incoming" | "outgoing";
  /** `compact`: yan panel (ortak arkadaş sayısı, küçük düğmeler); `full`: sekme listesi (oyun ve zaman). */
  layout?: "full" | "compact";
  label: string;
};

/**
 * Gelen ya da gönderilen arkadaşlık istekleri. Gelen: Kabul Et / Reddet. Gönderilen: "Bekliyor" + İsteği İptal Et.
 * Bir işlem sürerken tüm satırların düğmeleri kilitlenir (çift gönderim yok); sonuç sunucu yanıtından sonra duyurulur.
 */
export function RequestList({ views, direction, layout = "full", label }: RequestListProps) {
  const t = useTranslations("Social");
  const announce = useAnnounce();
  const mutations = useSocialMutations();
  const describe = usePerson();

  async function act(kind: "accept" | "reject" | "cancel", view: FriendRequestView) {
    const outcome = await mutations[kind](view.request.id);
    const name = describe(view.profile).name;
    if (outcome.ok) {
      const done = kind === "accept" ? "accepted" : kind === "reject" ? "rejected" : "cancelled";
      announce(t(`announce.${done}`, { name }));
    } else {
      announce(t(`errors.${outcome.error}`), "error");
    }
  }

  return (
    <ul aria-label={label} className={cn(layout === "compact" ? "space-y-2.5" : "space-y-3")}>
      {views.map((view) => (
        <li key={view.request.id}>
          <RequestRow
            view={view}
            direction={direction}
            layout={layout}
            pending={mutations.pendingId?.endsWith(`:${view.request.id}`) ?? false}
            locked={mutations.busy}
            onAct={(kind) => act(kind, view)}
          />
        </li>
      ))}
    </ul>
  );
}

type RowProps = {
  view: FriendRequestView;
  direction: "incoming" | "outgoing";
  layout: "full" | "compact";
  pending: boolean;
  locked: boolean;
  onAct: (kind: "accept" | "reject" | "cancel") => void;
};

function RequestRow({ view, direction, layout, pending, locked, onAct }: RowProps) {
  const t = useTranslations("Social");
  const format = useFormatter();
  const now = useNow({ updateInterval: 60_000 });
  const { profile, presence, request } = view;
  const person = usePerson()(profile);
  const name = person.name;
  const compact = layout === "compact";

  const actions =
    direction === "incoming" ? (
      <div className={cn("flex shrink-0 gap-2", !compact && "w-full @[40rem]:w-auto")}>
        <button
          type="button"
          onClick={() => onAct("accept")}
          disabled={locked}
          aria-busy={pending || undefined}
          aria-label={t("requests.acceptAria", { name })}
          className={buttonClass("primary", "sm", compact ? "h-8 px-2.5 text-xs" : "flex-1 @[40rem]:w-28 @[40rem]:flex-none")}
        >
          {t("requests.accept")}
        </button>
        <button
          type="button"
          onClick={() => onAct("reject")}
          disabled={locked}
          aria-label={t("requests.rejectAria", { name })}
          className={buttonClass("secondary", "sm", compact ? "h-8 px-2.5 text-xs" : "flex-1 @[40rem]:w-28 @[40rem]:flex-none")}
        >
          {t("requests.reject")}
        </button>
      </div>
    ) : (
      <div className="flex w-full shrink-0 items-center justify-between gap-4 @[40rem]:w-auto">
        <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-warning">
          <Icon name="clock" className="size-4" />
          {t("requests.pending")}
        </span>
        <button
          type="button"
          onClick={() => onAct("cancel")}
          disabled={locked}
          aria-busy={pending || undefined}
          aria-label={t("requests.cancelAria", { name })}
          className={buttonClass("secondary", "sm")}
        >
          {t("requests.cancel")}
        </button>
      </div>
    );

  if (compact) {
    return (
      <div className="flex items-center gap-3 rounded-panel border border-border bg-surface-elevated/50 p-3">
        <Avatar seed={profile.avatarSeed} imageUrl={person.avatarUrl} size="sm" ring />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-text-primary">{name}</p>
          {person.handle ? <p className="truncate text-xs text-text-secondary">{person.handle}</p> : null}
          <p className="mt-0.5 flex items-center gap-1 text-xs whitespace-nowrap text-text-secondary">
            <Icon name="users" className="size-3.5" />
            {t("card.mutual", { count: profile.mutualFriends })}
          </p>
        </div>
        {actions}
      </div>
    );
  }

  return (
    <div className="@container rounded-panel border border-border bg-surface p-4 transition-colors duration-150 hover:border-primary/35 motion-reduce:transition-none">
      <div className="flex flex-col gap-4 @[40rem]:grid @[40rem]:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)_auto] @[40rem]:items-center @[40rem]:gap-5">
        <div className="flex min-w-0 items-center gap-4">
          <Avatar seed={profile.avatarSeed} imageUrl={person.avatarUrl} size="md" ring />
          <div className="min-w-0">
            <p className="truncate font-display text-lg font-semibold text-text-primary">{name}</p>
            {person.handle ? <p className="truncate text-sm text-text-secondary">{person.handle}</p> : null}
            <p className="text-xs text-text-secondary">
              <time dateTime={request.createdAt}>{format.relativeTime(new Date(request.createdAt), now)}</time>
            </p>
          </div>
        </div>
        {presence.game ? (
          <div className="flex min-w-0 items-center gap-3">
            <GameCover visual={presence.game.visual} className="h-12 w-20" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-text-primary">{presence.game.title}</p>
              <p className="truncate text-xs text-text-secondary">
                {presence.status === "offline" ? t("game.lastPlayed") : t("game.playing")}
              </p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-text-secondary">{t("game.none")}</p>
        )}
        {actions}
      </div>
    </div>
  );
}
